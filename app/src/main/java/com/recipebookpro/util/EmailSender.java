package com.recipebookpro.util;

import android.text.TextUtils;
import com.recipebookpro.BuildConfig;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailSender {

    public interface EmailSendListener {
        void onSuccess(boolean isRealEmailSent);
        void onFailure(Exception e);
    }

    /**
     * Sends an email containing the verification OTP code.
     * If SMTP_EMAIL or SMTP_PASSWORD is not set in local.properties, it falls back to simulation mode
     * so that the developer/tester can still test it via Toast/Logcat.
     *
     * @param recipientEmail The destination email.
     * @param otpCode The 6-digit verification code.
     * @param listener The callback for success or failure.
     */
    public static void sendOtpEmail(final String recipientEmail, final String otpCode, final EmailSendListener listener) {
        final String senderEmail = BuildConfig.SMTP_EMAIL;
        final String senderPassword = BuildConfig.SMTP_PASSWORD;

        // If credentials are empty, fallback to simulation mode
        if (TextUtils.isEmpty(senderEmail) || TextUtils.isEmpty(senderPassword)) {
            android.util.Log.w("EmailSender", "SMTP credentials are not configured in local.properties. Falling back to simulation mode.");
            if (listener != null) {
                // Call back on a short delay to simulate network latency
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    listener.onSuccess(false); // indicates simulated success
                }, 800);
            }
            return;
        }

        new Thread(() -> {
            try {
                Properties props = new Properties();
                
                // Configure SMTP details. We select standard configurations based on host domain.
                String smtpHost = "smtp.gmail.com";
                String smtpPort = "587";
                
                if (senderEmail.contains("yandex")) {
                    smtpHost = "smtp.yandex.com";
                    smtpPort = "465";
                    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                    props.put("mail.smtp.socketFactory.port", "465");
                } else if (senderEmail.contains("outlook") || senderEmail.contains("hotmail")) {
                    smtpHost = "smtp.office365.com";
                }

                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", smtpHost);
                props.put("mail.smtp.port", smtpPort);

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(senderEmail, senderPassword);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(senderEmail));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                
                // Bilingual title for optimal delivery
                message.setSubject("RecipeBook Pro - E-posta Doğrulama Kodu / Email Verification");
                
                String emailBody = "Merhaba,\n\n" +
                        "RecipeBook Pro uygulamasına kayıt olmak için doğrulama kodunuz:\n\n" +
                        "👉 " + otpCode + " 👈\n\n" +
                        "Bu kodu 1 dakika içerisinde doğrulama ekranına girmeniz gerekmektedir.\n\n" +
                        "--------------------------------------------------\n\n" +
                        "Hello,\n\n" +
                        "Your verification code to register on RecipeBook Pro:\n\n" +
                        "👉 " + otpCode + " 👈\n\n" +
                        "Please enter this code in the verification screen within 1 minute.\n\n" +
                        "RecipeBook Pro Team";
                        
                message.setText(emailBody);

                Transport.send(message);

                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onSuccess(true); // indicates real email success
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("EmailSender", "Failed to send SMTP email", e);
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onFailure(e);
                    });
                }
            }
        }).start();
    }
}
