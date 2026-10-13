package ludo.servlet;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ludo.network.GameMessage;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GET /api/game/events?clientId=...[&waitMs=...] — drains this player's pending server events.
 *
 * Without waitMs (or when events are already waiting) it answers at once. With waitMs it
 * long-polls using Servlet 3 asynchronous processing: the Tomcat request thread is released
 * straight away, and the response is written when the next event arrives or the wait expires.
 * The event is delivered on a QueueWorker thread that may hold a room lock, so the listener
 * only calls AsyncContext.start(), which hands the actual write back to a Tomcat thread.
 */
public class GameEventsServlet extends LudoServlet {

    private static final long serialVersionUID = 1L;
    static final long MAX_WAIT_MS = 30_000;

    public GameEventsServlet(GameRoomManager roomManager, RequestQueueProcessor queueProcessor) {
        super(roomManager, queueProcessor);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpClientSession session = requireSession(req, resp);
        if (session == null) return;

        long waitMs = parseWait(req.getParameter("waitMs"));
        if (waitMs == 0 || session.getPendingEventCount() > 0) {
            writeEvents(session, resp);
            return;
        }

        AsyncContext async = req.startAsync();
        async.setTimeout(waitMs);
        AtomicBoolean answered = new AtomicBoolean(false);

        Runnable onEvent = () -> {
            if (answered.compareAndSet(false, true)) {
                async.start(() -> respondAndComplete(async, session));
            }
        };
        async.addListener(new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) {
                session.cancelNextEvent(onEvent);
                if (answered.compareAndSet(false, true)) {
                    respondAndComplete(async, session); // already on a container thread
                }
            }

            @Override public void onComplete(AsyncEvent event) { session.cancelNextEvent(onEvent); }
            @Override public void onError(AsyncEvent event) { session.cancelNextEvent(onEvent); }
            @Override public void onStartAsync(AsyncEvent event) { }
        });
        session.onNextEvent(onEvent);
    }

    private static void respondAndComplete(AsyncContext async, HttpClientSession session) {
        try {
            writeEvents(session, (HttpServletResponse) async.getResponse());
        } catch (IOException e) {
            // client went away; nothing to undo because the events were already drained for it
        } finally {
            async.complete();
        }
    }

    private static void writeEvents(HttpClientSession session, HttpServletResponse resp) throws IOException {
        List<Object> events = new ArrayList<>();
        for (GameMessage m : session.pollEvents()) {
            events.add(eventToMap(m));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("clientId", session.getClientId());
        body.put("count", events.size());
        body.put("events", events);
        writeJson(resp, 200, body);
    }

    static long parseWait(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Math.max(0, Math.min(MAX_WAIT_MS, Long.parseLong(raw.trim())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
