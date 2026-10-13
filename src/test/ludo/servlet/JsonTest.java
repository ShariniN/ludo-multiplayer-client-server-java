package ludo.servlet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Json writer tests")
class JsonTest {

    @Test
    @DisplayName("strings are quoted and escaped")
    void strings() {
        assertEquals("\"plain\"", Json.str("plain"));
        assertEquals("\"a\\\"b\"", Json.str("a\"b"));
        assertEquals("\"back\\\\slash\"", Json.str("back\\slash"));
        assertEquals("\"line\\nbreak\"", Json.str("line\nbreak"));
        assertEquals("\"\\u0001\"", Json.str("\u0001"));
        assertEquals("null", Json.str(null));
    }

    @Test
    @DisplayName("objects keep insertion order and nest arrays and maps")
    void objects() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("x", 1);
        inner.put("ok", true);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Ludo");
        m.put("n", 2.5);
        m.put("nothing", null);
        m.put("list", Arrays.asList("a", 2, inner));
        assertEquals("{\"name\":\"Ludo\",\"n\":2.5,\"nothing\":null,\"list\":[\"a\",2,{\"x\":1,\"ok\":true}]}", Json.obj(m));
    }

    @Test
    @DisplayName("unknown objects are rendered via toString as strings")
    void unknownObjects() {
        assertEquals("\"RED\"", Json.value(ludo.enums.Color.RED));
    }
}
