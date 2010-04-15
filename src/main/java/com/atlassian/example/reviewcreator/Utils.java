package com.atlassian.example.reviewcreator;

import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Returns a distinct set of review ids, extracted from the specified commit
     * message.
     *
     * @since   v1.4.1
     * @param commitMsg
     * @param projectKey   e.g. "CR-FE"
     * @return
     */
    public static Set<String> extractReviewIds(String commitMsg, String projectKey) {

        if (StringUtils.isEmpty(projectKey) || StringUtils.isEmpty(commitMsg)) {
            return Collections.EMPTY_SET;

        } else {
            final Set<String> ids = new HashSet<String>();

            final Matcher matcher = Pattern.compile("(" + projectKey + "-\\d+)").matcher(commitMsg);
            matcher.useTransparentBounds(true);
            matcher.useAnchoringBounds(false);

            while (matcher.find()) {
                ids.add(matcher.group(1));
            }
            return ids;
        }
    }
}
