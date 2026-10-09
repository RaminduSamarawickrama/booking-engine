package com.booking.booking.domain;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Booking references customers read out on the phone: "TR" + 6 characters, with no 0/O,
 * 1/I or L to mistake for each other. 31^6 is about 887 million combinations.
 */
public final class References {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private References() {
    }

    public static String next() {
        StringBuilder ref = new StringBuilder("TR");
        for (int i = 0; i < 6; i++) {
            ref.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return ref.toString();
    }

    /** Accepts lower case, spaces and dashes as typed by a customer. */
    public static String normalise(String typed) {
        return typed == null ? "" : typed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }
}
