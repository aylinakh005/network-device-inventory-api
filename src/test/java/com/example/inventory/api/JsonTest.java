package com.example.inventory.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void parsesObjectWithAllValueTypes() {
        Object v = Json.parse(" {\"s\":\"x\",\"n\":-12,\"d\":1.5e2,\"t\":true,\"f\":false,\"z\":null,\"a\":[1,\"b\"]} ");
        Map<?, ?> m = assertInstanceOf(Map.class, v);
        assertEquals("x", m.get("s"));
        assertEquals(-12L, m.get("n"));
        assertEquals(150.0, m.get("d"));
        assertEquals(true, m.get("t"));
        assertEquals(false, m.get("f"));
        assertTrue(m.containsKey("z"));
        assertNull(m.get("z"));
        assertEquals(List.of(1L, "b"), m.get("a"));
    }

    @Test
    void parsesEmptyContainers() {
        assertEquals(Map.of(), Json.parse("{}"));
        assertEquals(List.of(), Json.parse("[ ]"));
    }

    @Test
    void parsesEscapesAndUnicode() {
        assertEquals("a\"b\\c/d\n\t\u00e9", Json.parse("\"a\\\"b\\\\c\\/d\\n\\t\\u00e9\""));
    }

    @Test
    void parsesTopLevelScalars() {
        assertEquals("hi", Json.parse("\"hi\""));
        assertEquals(0L, Json.parse("0"));
        assertNull(Json.parse("null"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "{", "}", "{\"a\":}", "{\"a\" 1}", "{a:1}", "{\"a\":1,}", "[1,]", "[1 2]",
            "\"unterminated", "\"bad\\q\"", "\"bad\\u12\"", "\"bad\\uZZZZ\"", "\"tab\there\"",
            "01", "-", "1.", "1e", ".5", "+1", "tru", "nul", "{\"a\":1} extra", "{\"a\":1,\"a\":2}",
            "'single'", "NaN"})
    void rejectsMalformedJson(String input) {
        assertThrows(Json.JsonException.class, () -> Json.parse(input), input);
    }

    @Test
    void rejectsNullInput() {
        assertThrows(Json.JsonException.class, () -> Json.parse(null));
    }

    @Test
    void rejectsExcessiveNesting() {
        String deep = "[".repeat(100) + "]".repeat(100);
        Json.JsonException ex = assertThrows(Json.JsonException.class, () -> Json.parse(deep));
        assertTrue(ex.getMessage().contains("nesting"));
    }

    @Test
    void errorMessagesIncludePosition() {
        Json.JsonException ex = assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":@}"));
        assertTrue(ex.getMessage().contains("position 5"), ex.getMessage());
    }

    // ---- writing ------------------------------------------------------------------------------

    @Test
    void writesObjectsArraysAndScalars() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "x");
        m.put("n", 3);
        m.put("b", true);
        m.put("z", null);
        m.put("l", List.of("a", 1));
        assertEquals("{\"s\":\"x\",\"n\":3,\"b\":true,\"z\":null,\"l\":[\"a\",1]}", Json.write(m));
    }

    @Test
    void escapesSpecialCharacters() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\"", Json.write("a\"b\\c\n\u0001"));
    }

    @Test
    void writesEnumsByName() {
        assertEquals("\"ACTIVE\"", Json.write(com.example.inventory.model.NodeStatus.ACTIVE));
    }

    @Test
    void rejectsUnsupportedTypesAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
    }

    @Test
    void roundTripPreservesTrickyStrings() {
        String tricky = "quote\" backslash\\ newline\n unicode \u00e9\u4e2d control\u0007";
        assertEquals(tricky, Json.parse(Json.write(tricky)));
    }
}
