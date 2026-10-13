package ludo.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ludo.network.GameMessage;
import ludo.server.GameRoom;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only snapshot of a room (GET /api/game/state?roomId=ROOM-1).
 * Returns exactly what socket clients receive in GAME_STATE_UPDATE, as JSON —
 * useful for Postman/JMeter assertions and for a web dashboard.
 */
public class GameStateServlet extends LudoServlet {

    private static final long serialVersionUID = 1L;

    public GameStateServlet(GameRoomManager roomManager, RequestQueueProcessor queueProcessor) {
        super(roomManager, queueProcessor);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String roomId = req.getParameter("roomId");
        if (roomId == null || roomId.trim().isEmpty()) roomId = GameRoomManager.DEFAULT_ROOM_ID;
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            writeError(resp, 404, "No such room: " + roomId);
            return;
        }

        GameMessage state = room.buildStateMessage();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("roomId", room.getRoomId());
        body.put("round", state.getInt("round", 1));
        body.put("activePlayer", state.get("activeColor"));
        body.put("started", state.getBoolean("started", false));
        body.put("gameOver", state.getBoolean("gameOver", false));
        body.put("winner", state.get("winner"));
        body.put("diceValue", state.getInt("diceValue", 0));
        body.put("mysteryCellPosition", state.getInt("mysteryPos", -1));
        body.put("mysteryCellActive", state.getBoolean("mysteryActive", false));

        List<Object> pieces = new ArrayList<>();
        String encoded = state.get("pieces");
        if (encoded != null && !encoded.isEmpty()) {
            for (String token : encoded.split(";")) {
                String[] f = token.split(":");
                if (f.length < 4) continue;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", f[0]);
                p.put("state", f[1]);
                p.put("position", Integer.parseInt(f[2]));
                p.put("effect", f[3]);
                pieces.add(p);
            }
        }
        body.put("pieces", pieces);
        body.put("serverType", "Apache Tomcat " + org.apache.catalina.util.ServerInfo.getServerNumber() + " (embedded)");
        body.put("timestamp", System.currentTimeMillis());
        writeJson(resp, 200, body);
    }
}
