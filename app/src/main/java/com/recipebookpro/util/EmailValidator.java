package com.recipebookpro.util;

import android.text.TextUtils;
import android.util.Patterns;
import java.util.HashSet;
import java.util.Set;

public class EmailValidator {

    public enum ValidationResult {
        VALID,
        EMPTY,
        INVALID_FORMAT,
        DISPOSABLE_OR_FAKE
    }

    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>();

    static {
        // Silly / Dummy domains
        BLOCKED_DOMAINS.add("sanane.com");
        BLOCKED_DOMAINS.add("deneme.com");
        BLOCKED_DOMAINS.add("test.com");
        BLOCKED_DOMAINS.add("example.com");
        BLOCKED_DOMAINS.add("salla.com");
        BLOCKED_DOMAINS.add("abc.com");
        BLOCKED_DOMAINS.add("xyz.com");
        BLOCKED_DOMAINS.add("asdf.com");
        BLOCKED_DOMAINS.add("qwer.com");
        BLOCKED_DOMAINS.add("dummy.com");
        BLOCKED_DOMAINS.add("fake.com");
        BLOCKED_DOMAINS.add("junk.com");
        BLOCKED_DOMAINS.add("domain.com");

        // Common disposable / temporary email providers
        BLOCKED_DOMAINS.add("mailinator.com");
        BLOCKED_DOMAINS.add("yopmail.com");
        BLOCKED_DOMAINS.add("tempmail.com");
        BLOCKED_DOMAINS.add("10minutemail.com");
        BLOCKED_DOMAINS.add("guerrillamail.com");
        BLOCKED_DOMAINS.add("trashmail.com");
        BLOCKED_DOMAINS.add("dispostable.com");
        BLOCKED_DOMAINS.add("getairmail.com");
        BLOCKED_DOMAINS.add("sharklasers.com");
        BLOCKED_DOMAINS.add("guerillamailblock.com");
        BLOCKED_DOMAINS.add("guerillamail.net");
        BLOCKED_DOMAINS.add("guerillamail.org");
        BLOCKED_DOMAINS.add("guerillamail.biz");
        BLOCKED_DOMAINS.add("spam4.me");
        BLOCKED_DOMAINS.add("grr.la");
        BLOCKED_DOMAINS.add("crazymailing.com");
        BLOCKED_DOMAINS.add("owlymail.com");
        BLOCKED_DOMAINS.add("temp-mail.org");
        BLOCKED_DOMAINS.add("throwawaymail.com");
    }

    /**
     * Validates the email address and returns the specific validation status.
     *
     * @param email The email string to validate.
     * @return ValidationResult indicating the category of validation.
     */
    public static ValidationResult validate(String email) {
        if (TextUtils.isEmpty(email)) {
            return ValidationResult.EMPTY;
        }

        // 1. Basic format validation using Android's Patterns
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return ValidationResult.INVALID_FORMAT;
        }

        // 2. Split email into username and domain
        int atIndex = email.indexOf('@');
        if (atIndex == -1 || atIndex == 0 || atIndex == email.length() - 1) {
            return ValidationResult.INVALID_FORMAT;
        }

        String username = email.substring(0, atIndex).toLowerCase().trim();
        String domain = email.substring(atIndex + 1).toLowerCase().trim();

        // 3. Blocked domains check
        if (BLOCKED_DOMAINS.contains(domain)) {
            return ValidationResult.DISPOSABLE_OR_FAKE;
        }

        // 4. Username garbage pattern check (too short or repetitive characters)
        if (username.length() < 3 || isRepetitive(username)) {
            return ValidationResult.DISPOSABLE_OR_FAKE;
        }

        // 5. Common trash keyword check in domain
        if (domain.contains("tempmail") || domain.contains("yopmail") || 
            domain.contains("mailinator") || domain.contains("trashmail") ||
            domain.contains("temp-mail") || domain.contains("fake")) {
            return ValidationResult.DISPOSABLE_OR_FAKE;
        }

        return ValidationResult.VALID;
    }

    private static boolean isRepetitive(String s) {
        if (s == null || s.isEmpty()) return false;
        char first = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }
}
