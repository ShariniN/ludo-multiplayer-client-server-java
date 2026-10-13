package ludo.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turn actions for HTTP players.
 *   POST /api/game/roll    clientId          -> queues ROLL_DICE, answers with the dice outcome
 *   POST /api/game/move    clientId, piece   -> queues MOVE_PIECE, answers with the move outcome
 * (GET /api/game/events is served by GameEventsServlet, GET /api/game/state by GameStateServlet.)
 *
 * Both actions travel through the same ArrayBlockingQueue and QueueWorker pool as
 * socket clients, so an HTTP flood is absorbed by the same back-pressure mechanism.
 */
public class GameTurnServlet extends LudoServlet {

    private static final long serialVersionUID = 1L;

    public GameTurnServlet(GameRoomManager roomManager, RequestQueueProcessor queueProcessor) {
        super(roomManager, queueProcessor);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        HttpClientSession session = requireSession(req, resp);
        if (session == null) return;
        if (session.getRoom() == null) {
            writeError(resp, 409, "Session is not in a room yet");
            return;
        }
        String roomId = session.getRoom().getRoomId();
        long since = System.currentTimeMillis();

        if (path.endsWith("/roll")) {
            if (!enqueue(session, new GameMessage(MessageType.ROLL_DICE, session.getPlayerName(), roomId))) {
                writeError(resp, 503, "Server request queue is full - retry shortly");
                return;
            }
            GameMessage outcome = awaitOutcome(session, since,
                    MessageType.ERROR, MessageType.YOUR_TURN, MessageType.WAITING_TURN, MessageType.GAME_OVER);
            GameMessage rolled = session.latestEvent(since, MessageType.DICE_ROLLED);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", "ROLL_DICE");
            body.put("clientId", session.getClientId());
            body.put("color", session.getAssignedColor() != null ? session.getAssignedColor().name() : null);
            if (isType(outcome, MessageType.ERROR)) {
                body.put("status", "REJECTED");
                body.put("message", outcome.get("message"));
                writeJson(resp, 409, body);
                return;
            }
            body.put("status", outcome == null ? "QUEUED" : "SUCCESS");
            if (rolled != null) body.put("diceValue", rolled.getInt("diceValue", 0));
            if (isType(outcome, MessageType.YOUR_TURN)) {
                body.put("next", outcome.get("state"));
                if (outcome.get("validPieces") != null) body.put("validPieces", outcome.get("validPieces"));
            } else if (isType(outcome, MessageType.WAITING_TURN)) {
                body.put("next", "TURN_PASSED");
                body.put("activeColor", outcome.get("activeColor"));
            }
            writeJson(resp, outcome == null ? 202 : 200, body);

        } else if (path.endsWith("/move")) {
            String piece = req.getParameter("piece");
            if (piece == null || piece.trim().isEmpty()) {
                writeError(resp, 400, "Missing required 'piece' parameter");
                return;
            }
            if (!enqueue(session, new GameMessage(MessageType.MOVE_PIECE, session.getPlayerName(), roomId).put("piece", piece.trim()))) {
                writeError(resp, 503, "Server request queue is full - retry shortly");
                return;
            }
            GameMessage outcome = awaitOutcome(session, since,
                    MessageType.ERROR, MessageType.YOUR_TURN, MessageType.WAITING_TURN, MessageType.GAME_OVER);
            GameMessage moved = session.latestEvent(since, MessageType.PIECE_MOVED);
            GameMessage capture = session.latestEvent(since, MessageType.CAPTURE_EVENT);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", "MOVE_PIECE");
            body.put("piece", piece.trim());
            body.put("clientId", session.getClientId());
            if (isType(outcome, MessageType.ERROR)) {
                body.put("status", "REJECTED");
                body.put("message", outcome.get("message"));
                writeJson(resp, 409, body);
                return;
            }
            body.put("status", outcome == null ? "QUEUED" : "SUCCESS");
            if (moved != null) {
                body.put("from", moved.get("from"));
                body.put("to", moved.get("to"));
            }
            if (capture != null) body.put("captured", capture.get("captured"));
            if (isType(outcome, MessageType.GAME_OVER)) {
                body.put("next", "GAME_OVER");
                body.put("winner", outcome.get("winnerColor"));
            } else if (isType(outcome, MessageType.YOUR_TURN)) {
                body.put("next", "BONUS_TURN");
            } else if (isType(outcome, MessageType.WAITING_TURN)) {
                body.put("next", "TURN_PASSED");
                body.put("activeColor", outcome.get("activeColor"));
            }
            writeJson(resp, outcome == null ? 202 : 200, body);

        } else {
            writeError(resp, 404, "Unknown turn endpoint: " + path);
        }
    }
}
