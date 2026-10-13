package ludo.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.server.GameRoom;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Room lifecycle over HTTP.
 *   GET  /api/room/status?roomId=ROOM-1              -> room summary
 *   POST /api/room/join   playerName, preferredColor  -> creates an HTTP player session and queues JOIN_ROOM
 *   POST /api/room/leave  clientId                    -> queues LEAVE_ROOM and forgets the session
 */
public class RoomServlet extends LudoServlet {

    private static final long serialVersionUID = 1L;

    public RoomServlet(GameRoomManager roomManager, RequestQueueProcessor queueProcessor) {
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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("roomId", room.getRoomId());
        body.put("matchId", room.getMatchId());
        body.put("players", room.getPlayerCount());
        body.put("started", room.isGameStarted());
        body.put("gameOver", room.isGameOver());
        body.put("round", room.getRoundNumber());
        body.put("activePlayer", room.getActivePlayer().getColor().name());
        Map<String, Object> seats = new LinkedHashMap<>();
        Map<Color, String> names = room.getPlayerNames();
        for (Color c : Color.values()) seats.put(c.name(), names.getOrDefault(c, "Open Slot"));
        body.put("seats", seats);
        writeJson(resp, 200, body);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        if (path.endsWith("/leave")) {
            doLeave(req, resp);
        } else {
            doJoin(req, resp);
        }
    }

    private void doJoin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String roomId = req.getParameter("roomId");
        if (roomId == null || roomId.trim().isEmpty()) roomId = GameRoomManager.DEFAULT_ROOM_ID;

        String playerName = req.getParameter("playerName");
        if (playerName == null || playerName.trim().isEmpty()) {
            playerName = "WebPlayer-" + (int) (Math.random() * 900 + 100);
        }

        String prefColor = req.getParameter("preferredColor");
        HttpClientSession session = new HttpClientSession("HTTP-" + UUID.randomUUID(), playerName.trim());
        roomManager.registerClient(session);

        GameMessage join = new GameMessage(MessageType.JOIN_ROOM, playerName, roomId)
                .put("roomId", roomId)
                .put("playerName", playerName.trim());
        if (prefColor != null && !prefColor.isEmpty()) join.put("preferredColor", prefColor);

        long since = System.currentTimeMillis();
        if (!enqueue(session, join)) {
            roomManager.removeClient(session);
            writeError(resp, 503, "Server request queue is full - retry shortly");
            return;
        }

        GameMessage joined = awaitOutcome(session, since, MessageType.ROOM_JOINED);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", session.getClientId());
        body.put("playerName", session.getPlayerName());
        if (joined == null) {
            body.put("status", "QUEUED");
            body.put("message", "Join request queued; outcome will appear in /api/game/events");
            writeJson(resp, 202, body);
            return;
        }
        boolean overflow = !roomId.equals(joined.getRoomId());
        body.put("status", "SUCCESS");
        body.put("roomId", joined.getRoomId());
        body.put("assignedColor", joined.get("color"));
        body.put("message", overflow
                ? "Room " + roomId + " was full - seated in overflow room " + joined.getRoomId()
                : "Joined room via HTTP - long-poll /api/game/events?clientId=...&waitMs=... for updates");
        writeJson(resp, 200, body);
    }

    private void doLeave(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpClientSession session = requireSession(req, resp);
        if (session == null) return;
        String roomId = session.getRoom() != null ? session.getRoom().getRoomId() : GameRoomManager.DEFAULT_ROOM_ID;
        enqueue(session, new GameMessage(MessageType.LEAVE_ROOM, session.getPlayerName(), roomId));
        roomManager.removeClient(session);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("message", "Leave request queued");
        writeJson(resp, 200, body);
    }
}
