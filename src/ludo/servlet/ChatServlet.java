package ludo.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;

import java.io.IOException;
import java.util.*;

/**
 * In-game chat over HTTP.
 *   POST /api/chat  clientId, message -> queues CHAT_MESSAGE (broadcast to socket + HTTP players)
 *   GET  /api/chat                    -> the last messages posted through this endpoint
 */
public class ChatServlet extends LudoServlet {

    private static final long serialVersionUID = 1L;

    private static final int HISTORY_LIMIT = 100;

    private record ChatEntry(String sender, String message, long timestamp) {
        ChatEntry(String sender, String message) {
            this(sender, message, System.currentTimeMillis());
        }
    }

    /** Shared by all request threads; every access holds its monitor. */
    private final transient Deque<ChatEntry> chatHistory = new ArrayDeque<>();

    public ChatServlet(GameRoomManager roomManager, RequestQueueProcessor queueProcessor) {
        super(roomManager, queueProcessor);
        chatHistory.add(new ChatEntry("SYSTEM", "Welcome to the LUDO-T HTTP chat gateway"));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<Object> messages = new ArrayList<>();
        synchronized (chatHistory) {
            for (ChatEntry e : chatHistory) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sender", e.sender());
                m.put("message", e.message());
                m.put("timestamp", e.timestamp());
                messages.add(m);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("totalMessages", messages.size());
        body.put("messages", messages);
        writeJson(resp, 200, body);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpClientSession session = requireSession(req, resp);
        if (session == null) return;
        String message = req.getParameter("message");
        if (message == null || message.trim().isEmpty()) {
            writeError(resp, 400, "Missing required 'message' parameter");
            return;
        }
        String roomId = session.getRoom() != null ? session.getRoom().getRoomId() : GameRoomManager.DEFAULT_ROOM_ID;
        boolean queued = enqueue(session, new GameMessage(MessageType.CHAT_MESSAGE, session.getPlayerName(), roomId)
                .put("message", message.trim()));
        if (!queued) {
            writeError(resp, 503, "Server request queue is full - retry shortly");
            return;
        }
        synchronized (chatHistory) {
            chatHistory.addLast(new ChatEntry(session.getPlayerName(), message.trim()));
            while (chatHistory.size() > HISTORY_LIMIT) chatHistory.removeFirst();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("message", "Chat message queued for broadcast");
        writeJson(resp, 200, body);
    }
}
