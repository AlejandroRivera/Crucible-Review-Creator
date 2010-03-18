package com.atlassian.example.reviewcreator;

public class Utils {

    public static <T> T defaultIfNull(T object, T defaultObject) {
        return object == null ? defaultObject : object;
    }
}
