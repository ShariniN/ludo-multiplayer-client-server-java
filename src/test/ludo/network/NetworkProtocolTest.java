package ludo.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NetworkProtocol serialisation tests")
class NetworkProtocolTest {

    @Test
    @DisplayName("serialize/deserialize round-trips type, sender, room and payload")
    void roundTrip() {
        GameMessage original = new GameMessage(MessageType.MOVE_PIECE, "Player1", "ROOM-1")
                .put("piece", "R1")
                .put("steps", 4)
                .put("bonus", true);

        String line = NetworkProtocol.serialize(original);
        GameMessage parsed = NetworkProtocol.deserialize(line);

        assertNotNull(parsed);
        assertEquals(MessageType.MOVE_PIECE, parsed.getType());
        assertEquals("Player1", parsed.getSenderId());
        assertEquals("ROOM-1", parsed.getRoomId());
        assertEquals("R1", parsed.get("piece"));
        assertEquals(4, parsed.getInt("steps", 0));
        assertTrue(parsed.getBoolean("bonus", false));
    }

    @Test
    @DisplayName("serialized line has no embedded newline (one message per line framing)")
    void singleLine() {
        GameMessage msg = new GameMessage(MessageType.CHAT_MESSAGE, "Player2", "ROOM-1")
                .put("message", "line one\nline two");
        String line = NetworkProtocol.serialize(msg);
        assertFalse(line.contains("\n"), "newlines must be encoded so readLine() framing holds");
        assertEquals("line one\nline two", NetworkProtocol.deserialize(line).get("message"));
    }

    @Test
    @DisplayName("delimiter characters inside values are escaped")
    void delimiterSafety() {
        GameMessage msg = new GameMessage(MessageType.CHAT_MESSAGE, "P|pe&Amp=Eq", "R|1")
                .put("k=ey&", "v|al=ue&x");
        GameMessage parsed = NetworkProtocol.deserialize(NetworkProtocol.serialize(msg));
        assertEquals("P|pe&Amp=Eq", parsed.getSenderId());
        assertEquals("R|1", parsed.getRoomId());
        assertEquals("v|al=ue&x", parsed.get("k=ey&"));
    }

    @Test
    @DisplayName("unicode player names survive the wire")
    void unicode() {
        GameMessage msg = new GameMessage(MessageType.JOIN_ROOM, "Ṡhārini ✓", "ROOM-1").put("playerName", "நிர்மலன்");
        GameMessage parsed = NetworkProtocol.deserialize(NetworkProtocol.serialize(msg));
        assertEquals("Ṡhārini ✓", parsed.getSenderId());
        assertEquals("நிர்மலன்", parsed.get("playerName"));
    }

    @Test
    @DisplayName("message with empty payload deserializes with empty map")
    void emptyPayload() {
        String line = NetworkProtocol.serialize(new GameMessage(MessageType.ROLL_DICE, "Player2", "ROOM-1"));
        GameMessage parsed = NetworkProtocol.deserialize(line);
        assertNotNull(parsed);
        assertTrue(parsed.getPayload().isEmpty());
    }

    @Test
    @DisplayName("garbage, blank and HTTP request lines are rejected with null, never an exception")
    void garbageInput() {
        assertNull(NetworkProtocol.deserialize(null));
        assertNull(NetworkProtocol.deserialize("   "));
        assertNull(NetworkProtocol.deserialize("POST /api/connect HTTP/1.1"));
        assertNull(NetworkProtocol.deserialize("NOT_A_TYPE|a|b|123|"));
        assertNull(NetworkProtocol.deserialize("ROLL_DICE|only|two"));
    }

    @Test
    @DisplayName("payload copy constructor is defensive")
    void defensiveCopy() {
        Map<String, String> payload = new HashMap<>();
        payload.put("a", "1");
        GameMessage msg = new GameMessage(MessageType.HEARTBEAT, "x", "y", payload);
        payload.put("b", "2");
        assertNull(msg.get("b"));
    }

    @Test
    @DisplayName("null sender/room default to SYSTEM/DEFAULT")
    void nullDefaults() {
        GameMessage msg = new GameMessage(MessageType.ERROR, null, null);
        assertEquals("SYSTEM", msg.getSenderId());
        assertEquals("DEFAULT", msg.getRoomId());
    }

    @Test
    @DisplayName("getInt/getLong fall back on unparsable values")
    void numericFallbacks() {
        GameMessage msg = new GameMessage(MessageType.ERROR, "s", "r").put("n", "abc");
        assertEquals(7, msg.getInt("n", 7));
        assertEquals(9L, msg.getLong("n", 9L));
        assertEquals(3, msg.getInt("missing", 3));
    }
}
