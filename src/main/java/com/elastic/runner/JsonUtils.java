package com.elastic.runner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonUtils {
    private static final Pattern FLAT_JSON =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|[0-9]+)");

    private JsonUtils() {
    }

    static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    static Map<String, String> parseFlatJson(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = FLAT_JSON.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String raw = matcher.group(2);
            String value = raw.startsWith("\"") ? matcher.group(3) : raw;
            values.put(key, value);
        }
        return values;
    }
}
