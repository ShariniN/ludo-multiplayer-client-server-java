package ludo.stress;

import ludo.enums.Color;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Second kind of automated test client (brief step 4): plays LUDO-T through the
 * HTTP servlet gateway instead of a socket. It joins with POST /api/room/join,
 * long-polls GET /api/game/events, and answers YOUR_TURN with POST /api/game/roll or
 * /api/game/move. Runs on its own thread; several can run at once, mixed with
 * socket bots, and they all end up in the same server-side room.
 *
 * Uses only java.net.http (JDK 11+) and a couple of regexes — no JSON library.
 */
public class HttpTestClient implements Runnable {

    private static final long LONG_POLL_MS = 2000;
    private static final Pattern EVENT_TYPE = Pattern.compile("\"type\":\"([A-Z_]+)\"");
    private static final Pattern STATE = Pattern.compile("\"state\":\"([A-Z_]+)\"");
    private static final Pattern VALID = Pattern.compile("\"validPieces\":\"([^\"]*)\"");

    private final String baseUrl;
    private final String botName;
    private final Color preferredColor;
    private final String roomId;
    private final long pollIntervalMs;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean gameOver = new AtomicBoolean(false);
    private final AtomicLong requestsSent = new AtomicLong();
    private final AtomicLong eventsReceived = new AtomicLong();

    private String clientId;
    private String assignedColor;

    public HttpTestClient(String host, int httpPort, String botName, Color preferredColor, String roomId, long pollIntervalMs) {
        this.baseUrl = "http://" + host + ":" + httpPort;
        this.botName = botName;
        this.preferredColor = preferredColor;
        this.roomId = roomId;
        this.pollIntervalMs = pollIntervalMs;
    }

    public boolean join() {
        try {
            String body = "roomId=" + enc(roomId) + "&playerName=" + enc(botName)
                    + (preferredColor != null ? "&preferredColor=" + preferredColor.name() : "");
            String json = post("/api/room/join", body);
            clientId = extract(json, "clientId");
            assignedColor = extract(json, "assignedColor");
            running.set(clientId != null);
            return running.get();
        } catch (Exception e) {
            System.err.println("[HttpBot:" + botName + "] join failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void run() {
        while (running.get() && !gameOver.get()) {
            try {
                // Long-poll: the server holds the request until an event arrives or the wait ends,
                // so there is no busy polling; the short pause only paces the bot's own actions.
                String events = get("/api/game/events?clientId=" + enc(clientId) + "&waitMs=" + LONG_POLL_MS);
                handleEvents(events);
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[HttpBot:" + botName + "] poll error: " + e.getMessage());
            }
        }
    }

    /** Splits the events array on object boundaries and reacts to turn prompts. */
    private void handleEvents(String json) throws IOException, InterruptedException {
        if (json == null) return;
        String[] chunks = json.split("\\{\"type\":");
        for (int i = 1; i < chunks.length; i++) {
            String ev = "{\"type\":" + chunks[i];
            eventsReceived.incrementAndGet();
            Matcher m = EVENT_TYPE.matcher(ev);
            if (!m.find()) continue;
            String type = m.group(1);
            if ("YOUR_TURN".equals(type)) {
                Matcher st = STATE.matcher(ev);
                String state = st.find() ? st.group(1) : "";
                if ("ROLL_DICE".equals(state)) {
                    post("/api/game/roll", "clientId=" + enc(clientId));
                } else if ("SELECT_PIECE".equals(state)) {
                    Matcher v = VALID.matcher(ev);
                    if (v.find() && !v.group(1).isEmpty()) {
                        String piece = v.group(1).split(",")[0];
                        post("/api/game/move", "clientId=" + enc(clientId) + "&piece=" + enc(piece));
                    }
                }
            } else if ("GAME_OVER".equals(type)) {
                gameOver.set(true);
            }
        }
    }

    public void stop() {
        running.set(false);
    }

    private String post(String path, String form) throws IOException, InterruptedException {
        requestsSent.incrementAndGet();
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private String get(String path) throws IOException, InterruptedException {
        requestsSent.incrementAndGet();
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String extract(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\"([^\"]*)\"").matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public String getBotName()        { return botName; }
    public String getClientId()       { return clientId; }
    public String getAssignedColor()  { return assignedColor; }
    public boolean isGameOver()       { return gameOver.get(); }
    public long getRequestsSent()     { return requestsSent.get(); }
    public long getEventsReceived()   { return eventsReceived.get(); }
}
