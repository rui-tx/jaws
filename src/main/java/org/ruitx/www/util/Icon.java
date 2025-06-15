package org.ruitx.www.util;

public class Icon {
    /**
     * Returns an HTML markup string for a Feather icon placeholder.
     *
     * @param name    Feather icon name (e.g., "eye", "trash").
     * @param classes Tailwind classes for sizing/colour. Can be null or empty.
     * @return HTML string like <i data-feather="eye" class="h-4 w-4"></i>
     */
    public static String feather(String name, String classes) {
        if (classes == null) classes = "";
        return String.format("<i data-feather=\"%s\" class=\"%s\"></i>", name, classes);
    }
} 