package com.recipebookpro.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.recipebookpro.BuildConfig;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.repository.HealthCheckRepository;
import com.recipebookpro.util.RiskyIngredientResolver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Groq AI kullanarak tarif güvenlik analizi.
 *   - Geçmiş konuşmalara/önbelleklere BAKMAZ, her seferinde sıfırdan analiz yapar.
 *   - Sadece gönderilen GÜNCEL profili baz alır.
 *   - Doğal dil girişlerini yorumlar ("limon yiyince kaşınıyorum" → Limon alerjisi).
 *   - Semantik eşleştirme: türevler, eş anlamlılar bulunur.
 *   - Heuristik fallback: API erişilemezse yerel kural tabanlı analiz.
 */
public class HealthCheckRepositoryImpl implements HealthCheckRepository {

    private static final String TAG       = "HealthCheckRepository";
    private static final String API_KEY   = BuildConfig.GROQ_API_KEY;
    private static final String API_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String CHAT_MODEL = "llama-3.3-70b-versatile";

    // ─────────────────────────────────────────────────────────────────────────
    // Giriş noktası
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void checkRecipeSafety(Recipe recipe,
                                   List<String> healthConditions,
                                   List<String> customHealthConditions,
                                   List<String> allergens,
                                   Map<String, List<String>> healthTriggers,
                                   String uiLangCode,
                                   HealthCheckCallback callback) {

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        if (TextUtils.isEmpty(API_KEY) || "YOUR_GROQ_API_KEY".equals(API_KEY)) {
            mainHandler.post(() -> callback.onError(recipe.getId(), "Groq API Key eksik"));
            return;
        }

        // Tüm koşulları tek listede topla
        List<String> allConditions = new ArrayList<>();
        if (healthConditions != null)       allConditions.addAll(healthConditions);
        if (customHealthConditions != null) allConditions.addAll(customHealthConditions);
        if (allergens != null)              allConditions.addAll(allergens);

        if (allConditions.isEmpty()) {
            mainHandler.post(() -> callback.onResult(recipe.getId(), true, "", new ArrayList<>()));
            return;
        }

        boolean isTurkish = uiLangCode != null
                && uiLangCode.toLowerCase(Locale.ROOT).startsWith("tr");
        String targetLang = isTurkish ? "Turkish" : "English";

        // healthTriggers'ı ek bağlam olarak ekle
        List<String> allTriggers = new ArrayList<>();
        if (healthTriggers != null) {
            for (Map.Entry<String, List<String>> e : healthTriggers.entrySet()) {
                if (e.getValue() != null) allTriggers.addAll(e.getValue());
            }
        }

        // Prompt oluştur
        String systemPrompt = buildSystemPrompt(targetLang);
        String userPrompt   = buildUserPrompt(recipe, allConditions, allTriggers, isTurkish);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("model", CHAT_MODEL);

                JSONArray messages = new JSONArray();
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.put(sysMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userPrompt);
                messages.put(userMsg);

                payload.put("messages", messages);

                JSONObject responseFormat = new JSONObject();
                responseFormat.put("type", "json_object");
                payload.put("response_format", responseFormat);
                payload.put("temperature", 0.0); // deterministik, tutarlı sonuç

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String responseStr = readStream(conn.getInputStream());
                    JSONObject jsonResponse = new JSONObject(responseStr);
                    String text = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    String cleanedText = cleanJson(text);
                    JSONObject resObj  = new JSONObject(cleanedText);

                    // "uygun_mu" veya legacy "guvenli_mi" her ikisini de destekle
                    boolean isSafe;
                    if (resObj.has("uygun_mu")) {
                        isSafe = resObj.optBoolean("uygun_mu", true);
                    } else {
                        isSafe = resObj.optBoolean("guvenli_mi", true);
                    }

                    // Mesaj: "uyari_mesaji" yoksa "kullanici_mesaji"
                    String userMessage = resObj.optString("uyari_mesaji",
                            resObj.optString("kullanici_mesaji", ""));

                    // Tavsiye ekle (varsa)
                    String tavsiye = resObj.optString("tavsiye", "");
                    if (!tavsiye.isEmpty() && !userMessage.isEmpty()) {
                        userMessage = userMessage + "\n\n" + tavsiye;
                    }

                    // Riskli malzemeler
                    List<String> riskyIngredients = new ArrayList<>();
                    JSONArray riskyArr = resObj.optJSONArray("tespit_edilen_riskli_malzemeler");
                    if (riskyArr == null) {
                        riskyArr = resObj.optJSONArray("riskli_malzemeler");
                    }
                    if (riskyArr != null) {
                        for (int i = 0; i < riskyArr.length(); i++) {
                            String item = riskyArr.optString(i, "").trim();
                            if (!item.isEmpty()) riskyIngredients.add(item);
                        }
                    }

                    // Groq riskli malzeme bulamadıysa yerel resolver ile doldur
                    if (!isSafe && riskyIngredients.isEmpty()) {
                        riskyIngredients.addAll(RiskyIngredientResolver.resolveFromRecipe(
                                recipe, healthConditions, customHealthConditions,
                                healthTriggers, userMessage, uiLangCode));
                    }

                    Log.d(TAG, "Groq analiz OK — uygun=" + isSafe
                            + " riskli=" + riskyIngredients);

                    final boolean      fSafe  = isSafe;
                    final String       fMsg   = userMessage;
                    final List<String> fRisky = new ArrayList<>(riskyIngredients);
                    mainHandler.post(() -> callback.onResult(recipe.getId(), fSafe, fMsg, fRisky));

                } else {
                    String errBody = "";
                    try { errBody = readStream(conn.getErrorStream()); } catch (Exception ignored) {}
                    Log.w(TAG, "Groq HTTP " + responseCode + ": " + errBody
                            + " → heuristik fallback");
                    performHeuristicFallback(recipe, allConditions, allTriggers,
                            targetLang, healthConditions, customHealthConditions,
                            healthTriggers, uiLangCode, callback, mainHandler);
                }

            } catch (Exception e) {
                Log.w(TAG, "Groq exception → heuristik fallback", e);
                performHeuristicFallback(recipe, allConditions, allTriggers,
                        targetLang, healthConditions, customHealthConditions,
                        healthTriggers, uiLangCode, callback, mainHandler);
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // System Prompt  (iki prompt birleştirildi + güçlendirildi)
    // ─────────────────────────────────────────────────────────────────────────
    private String buildSystemPrompt(String targetLang) {
        return
            "# ROL\n" +
            "Sen dünyanın en titiz sağlık ve gastronomi güvenlik asistanısın.\n\n" +

            "# KRİTİK KURAL — ASLA UNUTMA\n" +
            "Bu konuşmada sana gönderilen 'GÜNCEL PROFİL' dışında HİÇBİR önceki bilgiyi,\n" +
            "konuşmayı veya analizi dikkate ALMA. Her analizi sıfırdan yap.\n" +
            "Eğer bir hastalık/alerji GÜNCEL PROFİL listesinde YOKSA, o konuda ASLA uyarı verme.\n" +
            "If the user has NO health conditions and NO allergens, always return isSafe=true with a positive message. Never warn about ingredients when the health profile is empty.\n\n" +

            "# DOĞAL DİL ANALİZİ\n" +
            "Kullanıcı profili tıbbi terimler yerine günlük cümleler içerebilir.\n" +
            "Örnekler:\n" +
            "  'Limon yiyince kaşınıyorum' → Limon alerjisi → Tarifte limon, limon suyu, limon kabuğu ara.\n" +
            "  'Süt içince midem bulanıyor' → Laktoz intoleransı → Tarifte süt, peynir, tereyağı, krema ara.\n" +
            "  'Fındıktan nefret ediyorum' → Bu bir alerji DEĞİL, sadece tercih; uyarı verme.\n" +
            "  'Fındığa alerjim var' → Fındık alerjisi → Tarifte fındık ve türevleri ara.\n" +
            "Cümleyi analiz et, asıl alerjeni/hastalığı tespit et, sonra tarifte tara.\n\n" +

            "# SEMANTİK EŞLEŞTİRME KURALLARI\n" +
            "Birebir kelime eşleşmesi aramak YASAK. Şu örnekleri uygula:\n" +
            "  Şeker/Diyabet → bal, pekmez, şurup, agave, fruktoz, beyaz şeker, esmer şeker\n" +
            "  Gluten/Çölyak → un, buğday, irmik, bulgur, arpa, çavdar, makarna, ekmek\n" +
            "  Laktoz/Süt    → süt, peynir, tereyağı, krema, yoğurt, labne, kaşar, beyaz peynir\n" +
            "  Yumurta       → yumurta, mayonez, omlet, meringue\n" +
            "  Fındık        → fındık, fındık ezmesi, pralin, nutella\n" +
            "  Limon         → limon, limon suyu, limon kabuğu rendesi, sitrik asit\n" +
            "Kullanıcı MANUEL girdi eklemişse (örn: 'limon'), o maddenin TÜM türevlerini de tara.\n\n" +

            "# ANALİZ AKIŞI\n" +
            "1. GÜNCEL PROFİL'i oku ve her maddeyi anla (doğal dil → tıbbi kavram).\n" +
            "2. Tarifte her malzemeyi tek tek kontrol et.\n" +
            "3. Sadece gerçek bir risk varsa 'uygun_mu: false' döndür.\n" +
            "4. Risk yoksa 'uygun_mu: true' ve pozitif mesaj döndür.\n\n" +

            "# ÇIKTI FORMATI — SADECE JSON, BAŞKA HİÇBİR ŞEY YAZMA\n" +
            "{\n" +
            "  \"uygun_mu\": true/false,\n" +
            "  \"tespit_edilen_riskli_malzemeler\": [\"malzeme1\", \"malzeme2\"],\n" +
            "  \"uyari_mesaji\": \"Kullanıcıya yönelik, samimi ve net açıklama. Hangi profil maddesi + hangi tarif malzemesi riski neden yaratıyor, açıkla.\",\n" +
            "  \"tavsiye\": \"Bu bir tıbbi tavsiye değildir. Gerekirse doktorunuza danışın.\"\n" +
            "}\n\n" +
            "KURAL: 'uyari_mesaji' " + targetLang + " dilinde yazılmalıdır.\n" +
            "KURAL: Return 'tespit_edilen_riskli_malzemeler' list in the SAME LANGUAGE as the recipe ingredients — not the user's condition language.\n" +
            "KURAL: In tespit_edilen_riskli_malzemeler list, return ONLY the EXACT ingredient names as they appear in the recipe. Never return abstract nutritional terms like 'saturated fat', 'sodium', 'refined sugar', 'gluten'. Instead, if sucuk is risky return 'sucuk', if kaşar is risky return 'kaşar', if bal is risky return 'bal', if bazlama is risky return 'bazlama'.\n" +
            "KURAL: 'uygun_mu' false ise 'tespit_edilen_riskli_malzemeler' BOŞ OLAMAZ.\n" +
            "KURAL: 'uygun_mu' true ise 'tespit_edilen_riskli_malzemeler' boş liste olmalı ve 'uyari_mesaji' olumlu olmalı.\n" +
            "KURAL: JSON dışında hiçbir açıklama, selamlama veya markdown ekleme.";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User Prompt
    // ─────────────────────────────────────────────────────────────────────────
    private String buildUserPrompt(Recipe recipe, List<String> allConditions,
                                    List<String> allTriggers, boolean isTurkish) {
        StringBuilder sb = new StringBuilder();

        sb.append("# GÜNCEL PROFİL\n");
        sb.append("Bu kullanıcının hastalıkları ve alerjileri (SADECE bunlara bak):\n");
        for (String cond : allConditions) {
            sb.append("  - ").append(cond).append("\n");
        }

        if (!allTriggers.isEmpty()) {
            sb.append("\nBu profil için önceden tespit edilmiş tetikleyici maddeler (referans):\n");
            for (String t : allTriggers) {
                sb.append("  - ").append(t).append("\n");
            }
        }

        sb.append("\n# ANALİZ EDİLECEK TARİF\n");
        sb.append("Tarif Adı: \"").append(recipe.getTitle()).append("\"\n");
        sb.append("Malzemeler:\n");

        // Malzemeleri tek tek listele (daha kolay analiz için)
        if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
            for (Recipe.Ingredient ing : recipe.getIngredients()) {
                String line = ing.getDisplayText().trim();
                if (!line.isEmpty()) sb.append("  - ").append(line).append("\n");
            }
        } else {
            sb.append(recipe.getFormattedIngredients()).append("\n");
        }

        sb.append("\nYukarıdaki GÜNCEL PROFİL'i ve tarifte listelenen malzemeleri analiz et. ")
          .append("Sadece profilde yazanlara göre değerlendirme yap.");

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Heuristik Fallback (API erişilemezse)
    // ─────────────────────────────────────────────────────────────────────────
    private void performHeuristicFallback(Recipe recipe,
                                           List<String> allConditions,
                                           List<String> allTriggers,
                                           String targetLang,
                                           List<String> healthConditions,
                                           List<String> customConditions,
                                           Map<String, List<String>> healthTriggers,
                                           String uiLangCode,
                                           HealthCheckCallback callback,
                                           Handler mainHandler) {

        boolean simulatedSafe = true;
        String rationaleText  = "";
        List<String> riskyIngredients = new ArrayList<>();
        String ingredientsLower = recipe.getFormattedIngredients() != null
                ? recipe.getFormattedIngredients().toLowerCase(Locale.ROOT) : "";

        boolean turkish = "Turkish".equals(targetLang);

        // 1. healthTriggers tetikleyicilerini tara
        if (!allTriggers.isEmpty() && !ingredientsLower.isEmpty()) {
            for (String trigger : allTriggers) {
                String tl = trigger.toLowerCase(Locale.ROOT).trim();
                if (tl.length() >= 2 && containsWord(ingredientsLower, tl)) {
                    riskyIngredients.add(trigger);
                    simulatedSafe = false;
                }
            }
            if (!simulatedSafe) {
                rationaleText = turkish
                        ? "Sağlık profilinize göre bu tarifteki bazı malzemeler risk oluşturabilir: "
                            + TextUtils.join(", ", riskyIngredients)
                        : "Based on your health profile, some ingredients may pose a risk: "
                            + TextUtils.join(", ", riskyIngredients);
                deliver(recipe.getId(), simulatedSafe, rationaleText, riskyIngredients, callback, mainHandler);
                return;
            }
        }

        // 2. Sabit kurallar
        for (String condition : allConditions) {
            String c = condition.toLowerCase(Locale.ROOT);

            if (c.contains("diabet") || c.contains("diyabet") || c.contains("şeker hastalığı")) {
                if (containsWord(ingredientsLower, "sugar","şeker","honey","bal","syrup","şurup","pekmez")) {
                    simulatedSafe = false;
                    riskyIngredients.add(turkish ? "şeker/bal/pekmez" : "sugar/honey/syrup");
                    rationaleText = turkish
                            ? "Bu tarif rafine şeker veya tatlandırıcı içeriyor. Diyabet profiliniz için dikkatli tüketin."
                            : "This recipe contains refined sugars. Please consume cautiously for diabetes.";
                    break;
                }
            }
            if (c.contains("celiac") || c.contains("çölyak") || c.contains("gluten") || c.contains("glüten")) {
                if (containsWord(ingredientsLower, "flour","un","wheat","buğday","bulgur","irmik","ekmek","makarna")) {
                    simulatedSafe = false;
                    riskyIngredients.add(turkish ? "un/buğday/gluten" : "flour/wheat/gluten");
                    rationaleText = turkish
                            ? "Gluten içeren malzemeler tespit edildi. Çölyak/gluten hassasiyetiniz için uygun değil."
                            : "Gluten-containing ingredients detected. Not suitable for celiac/gluten sensitivity.";
                    break;
                }
            }
            if (c.contains("lactose") || c.contains("laktoz") || c.contains("dairy") || c.contains("süt")) {
                if (containsWord(ingredientsLower, "milk","süt","cheese","peynir","cream","krema","butter","tereyağı","yoğurt","yogurt")) {
                    simulatedSafe = false;
                    riskyIngredients.add(turkish ? "süt ürünleri" : "dairy");
                    rationaleText = turkish
                            ? "Bu tarif süt ürünleri içeriyor. Laktoz intoleransınız için uygun olmayabilir."
                            : "This recipe contains dairy products. May not be suitable for lactose intolerance.";
                    break;
                }
            }
            if (c.contains("hypertension") || c.contains("hipertansiyon") || c.contains("tansiyon")) {
                if (containsWord(ingredientsLower, "salt","tuz","soy sauce","soya sosu")) {
                    simulatedSafe = false;
                    riskyIngredients.add(turkish ? "tuz/soya sosu" : "salt/soy sauce");
                    rationaleText = turkish
                            ? "Yüksek sodyum içeriği tansiyonunuzu olumsuz etkileyebilir."
                            : "High sodium content may affect your blood pressure.";
                    break;
                }
            }
            if (c.contains("kidney") || c.contains("böbrek") || c.contains("renal")) {
                if (containsWord(ingredientsLower, "salt","tuz","banana","muz","potassium")) {
                    simulatedSafe = false;
                    riskyIngredients.add(turkish ? "tuz/potasyum" : "salt/potassium");
                    rationaleText = turkish
                            ? "Sodyum veya potasyum içeriği böbrek rahatsızlığınız için dikkat gerektirir."
                            : "Sodium or potassium content requires caution for kidney disease.";
                    break;
                }
            }
            if (c.contains("cardiovascular") || c.contains("kalp") || c.contains("kolesterol") || c.contains("cholesterol")) {
                if (containsWord(ingredientsLower, "butter","tereyağı","cream","krema","sausage","sucuk","bacon","pastırma")) {
                    simulatedSafe = false;
                    riskyIngredients.add(turkish ? "doymuş yağ" : "saturated fat");
                    rationaleText = turkish
                            ? "Doymuş yağ içerikleri kalp/kolesterol profiliniz için önerilmez."
                            : "Saturated fat content is not recommended for cardiovascular health.";
                    break;
                }
            }
        }

        if (simulatedSafe) {
            rationaleText = turkish
                    ? "Tarifteki malzemeler sağlık profilinizle çelişmiyor. Afiyet olsun!"
                    : "The ingredients do not conflict with your health profile. Enjoy!";
        }

        deliver(recipe.getId(), simulatedSafe, rationaleText, riskyIngredients, callback, mainHandler);
    }

    private void deliver(String recipeId, boolean isSafe, String rationale, List<String> risky,
                          HealthCheckCallback callback, Handler mainHandler) {
        final boolean      fSafe  = isSafe;
        final String       fMsg   = rationale;
        final List<String> fRisky = new ArrayList<>(risky);
        mainHandler.post(() -> callback.onResult(recipeId, fSafe, fMsg, fRisky));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı
    // ─────────────────────────────────────────────────────────────────────────
    private boolean containsWord(String text, String... targets) {
        if (text == null || text.isEmpty()) return false;
        for (String word : targets) {
            String w = java.util.regex.Pattern.quote(word.toLowerCase(Locale.ROOT));
            String regex = "(?i)(^|\\s|\\p{Punct})" + w + "(\\p{L}{0,4})?(\\s|\\p{Punct}|$)";
            if (java.util.regex.Pattern.compile(regex).matcher(text).find()) return true;
        }
        return false;
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line.trim());
        return sb.toString();
    }

    private String cleanJson(String text) {
        String s = text.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        if (s.startsWith("```"))     s = s.substring(3);
        if (s.endsWith("```"))       s = s.substring(0, s.length() - 3);
        return s.trim();
    }
}
