package io.github.wboult.esrunner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonUtils {
    /**
     * Matches a single flat JSON key-value pair where the value is a quoted
     * string (with escape sequences), a JSON number, {@code null}, {@code true},
     * or {@code false}.
     *
     * <p>Group 1 – key (escape sequences preserved, unescaped by {@link #unescape}).
     * <p>Group 2 – raw value token.
     * <p>Group 3 – quoted string content (present only when group 2 starts with {@code "}).
     */
    private static final Pattern FLAT_JSON = Pattern.compile(
            "\"((?:[^\"\\\\]|\\\\.)*)\"" +          // group 1: key
            "\\s*:\\s*" +
            "(\"((?:[^\"\\\\]|\\\\.)*)\"|null|true|false|-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)");
                                                      // group 2: value; group 3: string content

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

    /**
     * Parses a flat (non-nested) JSON object into a string map.
     *
     * <p>String values are unescaped before being stored. Non-string primitives
     * ({@code null}, {@code true}, {@code false}, numbers) are stored as their
     * raw JSON text.
     */
    static Map<String, String> parseFlatJson(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = FLAT_JSON.matcher(json);
        while (matcher.find()) {
            String key = unescape(matcher.group(1));
            String raw = matcher.group(2);
            // group(3) is non-null only for quoted string values.
            String value = raw.startsWith("\"") ? unescape(matcher.group(3)) : raw;
            values.put(key, value);
        }
        return values;
    }

    /**
     * Reverses JSON string escaping. Returns the original string for inputs
     * that contain no backslash sequences (fast path).
     */
    private static String unescape(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    default -> { sb.append('\\'); sb.append(c); }
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
