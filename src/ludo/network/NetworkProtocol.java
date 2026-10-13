package ludo.network;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Line-based wire format: TYPE|SENDER|ROOM|TIMESTAMP|key1=val1&key2=val2
 * Fields and payload entries are URL-encoded so '|', '&' and '=' are safe in values.
 */
public class NetworkProtocol {

    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final String DELIMITER = "\\|";
    public static final String FIELD_SEP = "|";

    /** Serializes a message to one line (no trailing newline). */
    public static String serialize(GameMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(msg.getType().name()).append(FIELD_SEP);
        sb.append(encode(msg.getSenderId())).append(FIELD_SEP);
        sb.append(encode(msg.getRoomId())).append(FIELD_SEP);
        sb.append(msg.getTimestamp()).append(FIELD_SEP);

        Map<String, String> payload = msg.getPayload();
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) sb.append("&");
            sb.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
            first = false;
        }
        return sb.toString();
    }

    /** Parses one line; returns null if it is blank or malformed. */
    public static GameMessage deserialize(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        String[] parts = line.split(DELIMITER, 5);
        if (parts.length < 4) {
            return null;
        }

        try {
            MessageType type = MessageType.valueOf(parts[0]);
            String senderId = decode(parts[1]);
            String roomId = decode(parts[2]);
            // parts[3] (timestamp) is ignored; the new message gets a fresh one
            Map<String, String> payload = new HashMap<>();

            if (parts.length == 5 && !parts[4].trim().isEmpty()) {
                String[] pairs = parts[4].split("&");
                for (String pair : pairs) {
                    int eq = pair.indexOf('=');
                    if (eq != -1) {
                        String key = decode(pair.substring(0, eq));
                        String val = decode(pair.substring(eq + 1));
                        payload.put(key, val);
                    }
                }
            }

            return new GameMessage(type, senderId, roomId, payload);
        } catch (Exception e) {
            System.err.println("[NetworkProtocol] Failed to parse message line: " + line + " - " + e.getMessage());
            return null;
        }
    }

    private static String encode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    private static String decode(String s) {
        if (s == null) return "";
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }
}
