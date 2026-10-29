package com.example.inventory.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, strict JSON reader/writer so the project needs no third-party dependencies.
 *
 * <p>Parsed values are {@code Map<String,Object>}, {@code List<Object>}, {@code String},
 * {@code Long}, {@code Double}, {@code Boolean} or {@code null}.
 */
public final class Json {

    private static final int MAX_DEPTH = 32;

    /** Thrown for malformed JSON. The message includes the character position. */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    private Json() { }

    // ---- parsing ------------------------------------------------------------------------------

    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("input is null");
        }
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.value(0);
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw p.error("unexpected content after JSON value");
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        JsonException error(String message) {
            return new JsonException(message + " at position " + pos);
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nesting too deep");
            }
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{': return object(depth);
                case '[': return array(depth);
                case '"': return string();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return number();
                    }
                    throw error("unexpected character '" + c + "'");
            }
        }

        private void literal(String word) {
            if (!s.startsWith(word, pos)) {
                throw error("invalid literal");
            }
            pos += word.length();
        }

        private Map<String, Object> object(int depth) {
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("expected string key");
                }
                String key = string();
                skipWhitespace();
                if (!peek(':')) {
                    throw error("expected ':'");
                }
                pos++;
                skipWhitespace();
                if (map.containsKey(key)) {
                    throw error("duplicate key '" + key + "'");
                }
                map.put(key, value(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    return map;
                } else {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> array(int depth) {
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(value(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    return list;
                } else {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private boolean peek(char expected) {
            return pos < s.length() && s.charAt(pos) == expected;
        }

        private String string() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    pos--;
                    throw error("unescaped control character in string");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("unterminated escape sequence");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw error("incomplete unicode escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("invalid unicode escape");
                        }
                        pos += 4;
                        break;
                    default:
                        pos--;
                        throw error("invalid escape '\\" + e + "'");
                }
            }
        }

        private Object number() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            if (peek('0')) {
                pos++;
            } else if (pos < s.length() && s.charAt(pos) >= '1' && s.charAt(pos) <= '9') {
                digits();
            } else {
                throw error("invalid number");
            }
            boolean integral = true;
            if (peek('.')) {
                integral = false;
                pos++;
                if (digits() == 0) {
                    throw error("digits expected after decimal point");
                }
            }
            if (peek('e') || peek('E')) {
                integral = false;
                pos++;
                if (peek('+') || peek('-')) {
                    pos++;
                }
                if (digits() == 0) {
                    throw error("digits expected in exponent");
                }
            }
            String text = s.substring(start, pos);
            if (integral) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ex) {
                    return Double.parseDouble(text);
                }
            }
            return Double.parseDouble(text);
        }

        private int digits() {
            int count = 0;
            while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                pos++;
                count++;
            }
            return count;
        }
    }

    // ---- writing ------------------------------------------------------------------------------

    /** Serialises Maps, Iterables, Strings, Numbers, Booleans, Enums and null. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof CharSequence || v instanceof Enum<?>) {
            writeString(sb, v.toString());
        } else if (v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Number n) {
            if (n instanceof Double d && (d.isNaN() || d.isInfinite())) {
                throw new IllegalArgumentException("NaN and Infinity cannot be written as JSON");
            }
            sb.append(n);
        } else if (v instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Cannot serialise " + v.getClass().getName());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
