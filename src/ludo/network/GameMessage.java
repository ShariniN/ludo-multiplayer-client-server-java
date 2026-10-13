package ludo.network;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Message DTO exchanged between client and server: a type, header fields and a
 * string key-value payload.
 */
public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final String senderId;
    private final String roomId;
    private final long timestamp;
    private final Map<String, String> payload;

    public GameMessage(MessageType type, String senderId, String roomId) {
        this.type = type;
        this.senderId = senderId != null ? senderId : "SYSTEM";
        this.roomId = roomId != null ? roomId : "DEFAULT";
        this.timestamp = System.currentTimeMillis();
        this.payload = new HashMap<>();
    }

    public GameMessage(MessageType type, String senderId, String roomId, Map<String, String> payload) {
        this.type = type;
        this.senderId = senderId != null ? senderId : "SYSTEM";
        this.roomId = roomId != null ? roomId : "DEFAULT";
        this.timestamp = System.currentTimeMillis();
        this.payload = payload != null ? new HashMap<>(payload) : new HashMap<>();
    }

    public MessageType getType() {
        return type;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getRoomId() {
        return roomId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public GameMessage put(String key, String value) {
        this.payload.put(key, value);
        return this;
    }

    public GameMessage put(String key, int value) {
        this.payload.put(key, String.valueOf(value));
        return this;
    }

    public GameMessage put(String key, long value) {
        this.payload.put(key, String.valueOf(value));
        return this;
    }

    public GameMessage put(String key, boolean value) {
        this.payload.put(key, String.valueOf(value));
        return this;
    }

    public String get(String key) {
        return payload.get(key);
    }

    public int getInt(String key, int defaultValue) {
        String val = payload.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String val = payload.get(key);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = payload.get(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    @Override
    public String toString() {
        return "GameMessage{" +
                "type=" + type +
                ", senderId='" + senderId + '\'' +
                ", roomId='" + roomId + '\'' +
                ", timestamp=" + timestamp +
                ", payload=" + payload +
                '}';
    }
}
