package com.booking.payment.domain;

import com.booking.platform.error.ApiException;

/**
 * Test cards for the mock provider, mirroring Stripe's test numbers so the same habits work
 * in both modes. Real card numbers are never accepted: anything else is declined.
 */
public final class MockCards {

    public record Outcome(boolean approved, String code, String message) {
    }

    private MockCards() {
    }

    public static Outcome charge(String typedNumber) {
        String number = typedNumber == null ? "" : typedNumber.replaceAll("[\\s-]", "");
        if (!number.matches("\\d{12,19}") || !luhn(number)) {
            throw ApiException.unprocessable("invalid_card_number", "Check the card number.");
        }
        return switch (number) {
            case "4242424242424242", "5555555555554444" -> new Outcome(true, null, null);
            case "4000000000009995" -> new Outcome(false, "insufficient_funds", "Your card has insufficient funds.");
            case "4000000000000069" -> new Outcome(false, "expired_card", "Your card has expired.");
            default -> new Outcome(false, "card_declined", "Your card was declined.");
        };
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (twice) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            twice = !twice;
        }
        return sum % 10 == 0;
    }
}
