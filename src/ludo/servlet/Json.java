package ludo.servlet;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer (vanilla Java — no third-party libraries allowed).
 * Handles the only shapes the servlets emit: objects, arrays, strings, numbers, booleans.
 */
public final class Json {

    private Json() {}

    public static String str(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Serialises a map whose values are String, Number, Boolean, Map, List or null. */
    public static String obj(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append(str(e.getKey())).append(":").append(value(e.getValue()));
            first = false;
        }
        return sb.append("}").toString();
    }

    public static String arr(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(value(list.get(i)));
        }
        return sb.append("]").toString();
    }

    @SuppressWarnings("unchecked")
    public static String value(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return str((String) v);
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Map) return obj((Map<String, ?>) v);
        if (v instanceof List) return arr((List<?>) v);
        return str(v.toString());
    }
}
