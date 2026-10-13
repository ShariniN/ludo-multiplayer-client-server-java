package ludo.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.server.ClientSession;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;
import ludo.server.RequestTask;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared plumbing for LUDO-T servlets: JSON responses, session lookup, and the
 * one path every game action takes — the RequestQueueProcessor. Tomcat request
 * threads are producers exactly like ClientHandler socket threads; they never
 * change a GameRoom directly.
 *
 * Tomcat creates one instance per servlet and calls it from many request threads at
 * once, so subclasses keep no per-request state in fields.
 */
public abstract class LudoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** How long a servlet will wait for the queued action's outcome before answering "QUEUED". */
    protected static final long ACTION_WAIT_MS = 1500;

    protected final transient GameRoomManager roomManager;
    protected final transient RequestQueueProcessor queueProcessor;

    protected LudoServlet(GameRoomManager roomManager, RequestQueueProcessor queueProcessor) {
        this.roomManager = roomManager;
        this.queueProcessor = queueProcessor;
    }

    protected static void writeJson(HttpServletResponse resp, int status, Map<String, ?> body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().print(Json.obj(body));
    }

    protected static void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("code", status);
        body.put("message", message);
        writeJson(resp, status, body);
    }

    /** Resolves the HTTP session for the clientId parameter, or null (after writing a 400/401). */
    protected HttpClientSession requireSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String clientId = req.getParameter("clientId");
        if (clientId == null || clientId.isEmpty()) {
            writeError(resp, 400, "Missing required 'clientId' parameter (join a room first)");
            return null;
        }
        ClientSession session = roomManager.getClient(clientId);
        if (!(session instanceof HttpClientSession http)) {
            writeError(resp, 401, "Unknown or expired clientId: " + clientId);
            return null;
        }
        http.touch();
        return http;
    }

    /** Producer side of the queue: returns false when the bounded buffer is full. */
    protected boolean enqueue(ClientSession session, GameMessage message) {
        return queueProcessor.enqueue(new RequestTask(session, message));
    }

    /** Parameter value, or the fallback when missing or blank. */
    protected static String param(HttpServletRequest req, String name, String fallback) {
        String value = req.getParameter(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * Waits for the outcome of an action this request just queued. The wait is bounded and
     * an interrupt (Tomcat shutting down) is treated as "no outcome yet".
     */
    protected static GameMessage awaitOutcome(HttpClientSession session, long since, MessageType... types) {
        try {
            return session.awaitEvent(since, ACTION_WAIT_MS, types);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    protected static Map<String, Object> eventToMap(GameMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", m.getType().name());
        map.put("sender", m.getSenderId());
        map.put("roomId", m.getRoomId());
        map.put("timestamp", m.getTimestamp());
        map.put("payload", new LinkedHashMap<>(m.getPayload()));
        return map;
    }

    protected static boolean isType(GameMessage m, MessageType t) {
        return m != null && m.getType() == t;
    }
}
