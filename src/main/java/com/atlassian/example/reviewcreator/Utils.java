package com.atlassian.example.reviewcreator;

import org.apache.commons.lang.StringUtils;

public class Utils {

    public static <T> T defaultIfNull(T object, T defaultObject) {
        return object == null ? defaultObject : object;
    }

    public static String firstNonEmptyLine(String message) {

        final String[] lines = StringUtils.split(message, "\r\n");
        if (lines == null) {
            return null;
        } else {
            for (String line : lines) {
                if (StringUtils.isNotBlank(line)) {
                    return line;
                }
            }
            return "";
        }
    }
}
