package com.parallax.application.pii;

/**
 * Masks a full name for display (SPEC §9): each whitespace-separated word becomes its first letter
 * followed by "•" × max(2, length − 1), e.g. "Priya Sharma" → "P•••• S•••••", "Al Li" → "A•• L••".
 */
public final class NameMasker {

    private NameMasker() {
    }

    public static String mask(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] words = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            String word = words[i];
            out.append(word.charAt(0));
            out.append("•".repeat(Math.max(2, word.length() - 1)));
        }
        return out.toString();
    }
}
