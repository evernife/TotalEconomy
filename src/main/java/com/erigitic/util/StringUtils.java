package com.erigitic.util;

public class StringUtils {

    /**
     * Convert strings to titles (title -> Title).
     *
     * @param input the string to be titleized
     * @return String the titileized version of the input
     */
    public static String titleize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }
}
