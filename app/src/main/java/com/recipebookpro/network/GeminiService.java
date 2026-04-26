package com.recipebookpro.network;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import com.recipebookpro.BuildConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GeminiService {

    // LÜTFEN KENDİ GEMINI API KEY'İNİZİ BURAYA GİRİN:
    // https://aistudio.google.com/ adresinden ücretsiz alabilirsiniz.
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;
    
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;

    public interface GeminiCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void analyzeNutrition(String ingredientsText, GeminiCallback callback) {
        new Thread(() -> {
            try {
                // Eğer API key girilmemişse, deneme (mock) verisi döndürürüz ki arayüzü görebilelim.
                if ("YOUR_GEMINI_API_KEY".equals(API_KEY)) {
                    Thread.sleep(1500); // Yapay zeka düşünüyor efekti :)
                    callback.onSuccess("API Anahtarı eksik! Örnek Değerler:\nKalori: 450 kcal\nProtein: 18g\nKarbonhidrat: 42g\nYağ: 14g");
                    return;
                }

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // Prompt'umuzu hazırlıyoruz
                String prompt = "Sen uzman bir diyetisyensin. Aşağıdaki malzemelerin birleşimiyle oluşan yemeğin tek porsiyonluk DİYET ve BESİN DEĞERLERİNİ tahmin et. Format tam olarak şöyle olmalı:\nKalori: XXX kcal\nProtein: XX g\nKarbonhidrat: XX g\nYağ: XX g\n\nBaşka hiçbir yorum yazma. Malzemeler:\n" + ingredientsText;

                JSONObject payload = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();
                
                part.put("text", prompt);
                parts.put(part);
                content.put("parts", parts);
                contents.put(content);
                payload.put("contents", contents);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String text = jsonResponse.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");
                            
                    callback.onSuccess(text.trim());
                } else {
                    callback.onError("Sunucu Hatası: " + conn.getResponseCode());
                }
            } catch (Exception e) {
                Log.e("GeminiService", "API Error", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
