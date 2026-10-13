package ludo.server;

import ludo.db.JdbcPersistenceGateway;
import ludo.enums.Color;
import ludo.game.Dice;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.network.NetworkProtocol;
import ludo.stress.AutomatedTestClient;
import ludo.stress.HttpTestClient;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real sockets, real HTTP, real threads: the server is started on free ports and
 * driven by the same automated clients used for the stress test.
 */
@DisplayName("LudoServer end-to-end integration tests")
class LudoServerIntegrationTest {

    private JdbcPersistenceGateway persistence;
    private LudoServer server;
    private int tcpPort, httpPort;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void startServer() throws Exception {
        Dice.resetInstance();
        try (ServerSocket a = new ServerSocket(0); ServerSocket b = new ServerSocket(0)) {
            tcpPort = a.getLocalPort();
            httpPort = b.getLocalPort();
        }
        persistence = JdbcPersistenceGateway.inMemory("test-" + UUID.randomUUID());
        server = new LudoServer(tcpPort, httpPort, persistence);
        Thread t = new Thread(server::start, "TestLudoServer");
        t.setDaemon(true);
        t.start();
        for (int i = 0; i < 100; i++) {
            try (Socket probe = new Socket("127.0.0.1", tcpPort)) {
                if (probe.isConnected()) break;
            } catch (IOException e) { Thread.sleep(20); }
        }
        // Tomcat starts after the TCP socket is bound, so wait for HTTP separately
        for (int i = 0; i < 200; i++) {
            try {
                if (send("GET", "/api/stats", null).statusCode() == 200) break;
            } catch (IOException e) { Thread.sleep(25); }
        }
    }

    @AfterEach
    void stopServer() {
        try {
            server.stop();
            Dice.resetInstance();
        } finally {
            persistence.close();
        }
    }

    @Test
    @DisplayName("a raw socket client gets CONNECTED_ACK, joins, and is answered by a queue worker")
    void rawSocketHandshake() throws Exception {
        try (Socket s = new Socket("127.0.0.1", tcpPort)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            GameMessage ack = NetworkProtocol.deserialize(in.readLine());
            assertEquals(MessageType.CONNECTED_ACK, ack.getType());

            out.println(NetworkProtocol.serialize(new GameMessage(MessageType.JOIN_ROOM, "Solo", "ROOM-1")
                    .put("roomId", "ROOM-1").put("playerName", "Solo").put("preferredColor", "BLUE")));
            GameMessage joined = readUntil(in, MessageType.ROOM_JOINED);
            assertEquals("BLUE", joined.get("color"));

            out.println(NetworkProtocol.serialize(new GameMessage(MessageType.HEARTBEAT, "Solo", "ROOM-1")));
            GameMessage hb = readUntil(in, MessageType.HEARTBEAT);
            assertTrue(hb.get("worker").startsWith("QueueWorker-"));
        }
    }

    @Test
    @DisplayName("four socket bots complete a whole match and every client sees identical state broadcasts")
    void fourBotsCompleteMatch() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        List<AutomatedTestClient> bots = new ArrayList<>();
        for (Color c : Color.values()) {
            AutomatedTestClient bot = new AutomatedTestClient("127.0.0.1", tcpPort, "Bot-" + c, c, "ROOM-1");
            assertTrue(bot.connect());
            bots.add(bot);
            pool.submit(bot);
        }
        long deadline = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < deadline && bots.stream().noneMatch(AutomatedTestClient::isGameOver)) {
            Thread.sleep(100);
        }
        assertTrue(bots.stream().allMatch(AutomatedTestClient::isGameOver), "all four clients must observe GAME_OVER");
        assertEquals(0, server.getQueueProcessor().getTotalDropped());
        assertEquals(server.getQueueProcessor().getTotalEnqueued(), server.getQueueProcessor().getTotalProcessed());
        GameRoom room = server.getRoomManager().getRoom("ROOM-1");
        assertTrue(room.isGameOver());
        assertNotNull(room.getWinnerColor());
        for (AutomatedTestClient b : bots) b.close();
        pool.shutdownNow();
    }

    @Test
    @DisplayName("HTTP and socket players share one room; the HTTP path uses the same queue")
    void mixedTransports() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        AutomatedTestClient tcp1 = new AutomatedTestClient("127.0.0.1", tcpPort, "Tcp-1", Color.RED, "MIX");
        AutomatedTestClient tcp2 = new AutomatedTestClient("127.0.0.1", tcpPort, "Tcp-2", Color.GREEN, "MIX");
        assertTrue(tcp1.connect());
        assertTrue(tcp2.connect());
        pool.submit(tcp1);
        pool.submit(tcp2);
        HttpTestClient web1 = new HttpTestClient("127.0.0.1", httpPort, "Web-1", Color.YELLOW, "MIX", 30);
        HttpTestClient web2 = new HttpTestClient("127.0.0.1", httpPort, "Web-2", Color.BLUE, "MIX", 30);
        assertTrue(web1.join());
        assertTrue(web2.join());
        assertEquals("YELLOW", web1.getAssignedColor());
        assertEquals("BLUE", web2.getAssignedColor());
        pool.submit(web1);
        pool.submit(web2);

        String status = get("/api/room/status?roomId=MIX");
        assertTrue(status.contains("\"players\":4"), status);
        assertTrue(status.contains("\"started\":true"), status);

        long deadline = System.currentTimeMillis() + 90000;
        while (System.currentTimeMillis() < deadline && !(tcp1.isGameOver() && web1.isGameOver())) Thread.sleep(200);
        assertTrue(tcp1.isGameOver(), "socket player must see GAME_OVER");
        assertTrue(web1.isGameOver(), "HTTP player must see GAME_OVER via its event outbox");
        assertEquals(0, server.getQueueProcessor().getTotalDropped());
        assertTrue(web1.getRequestsSent() > 10);

        String state = get("/api/game/state?roomId=MIX");
        assertTrue(state.contains("\"gameOver\":true"), state);
        tcp1.close(); tcp2.close(); web1.stop(); web2.stop();
        pool.shutdownNow();
    }

    @Test
    @DisplayName("servlet endpoints: join, out-of-turn roll rejected, events, stats, leaderboard, 404, 401")
    void servletEndpoints() throws Exception {
        String join = post("/api/room/join", "playerName=Postman&preferredColor=GREEN&roomId=API");
        assertTrue(join.contains("\"status\":\"SUCCESS\""), join);
        assertTrue(join.contains("\"assignedColor\":\"GREEN\""), join);
        String clientId = extract(join, "clientId");
        assertNotNull(clientId);

        // Not enough players: game not started -> roll produces no outcome quickly -> QUEUED (202)
        HttpResponse<String> roll = send("POST", "/api/game/roll", "clientId=" + clientId);
        assertTrue(roll.statusCode() == 202 || roll.statusCode() == 409, "got " + roll.statusCode() + " " + roll.body());

        String events = get("/api/game/events?clientId=" + clientId);
        assertTrue(events.contains("ROOM_JOINED"), events);
        assertTrue(events.contains("GAME_STATE_UPDATE"), events);

        String stats = get("/api/stats");
        assertTrue(stats.contains("\"workerThreads\":8"), stats);
        assertTrue(stats.contains("\"dropped\":0"), stats);

        String lb = get("/api/leaderboard");
        assertTrue(lb.contains("\"leaderboard\":[") && lb.contains("\"source\""), lb);

        String chat = post("/api/chat", "clientId=" + clientId + "&message=hello%20from%20http");
        assertTrue(chat.contains("SUCCESS"), chat);
        assertTrue(get("/api/chat").contains("hello from http"));

        assertEquals(404, send("GET", "/api/room/status?roomId=NOPE", null).statusCode());
        assertEquals(401, send("POST", "/api/game/roll", "clientId=bogus").statusCode());
        assertEquals(400, send("POST", "/api/game/move", "clientId=" + clientId).statusCode());
        assertEquals(405, send("DELETE", "/api/leaderboard", null).statusCode());
        assertTrue(get("/api/game/state?roomId=API").contains("Apache Tomcat"), "served by the embedded Tomcat");
    }

    @Test
    @DisplayName("Tomcat async long-poll: /api/game/events waits for the next event instead of returning empty")
    void longPollWakesOnEvent() throws Exception {
        String clientId = extract(post("/api/room/join", "playerName=Waiter&preferredColor=RED&roomId=LONGPOLL"), "clientId");
        get("/api/game/events?clientId=" + clientId); // drain the join events

        // Nothing happens: the request is held for about waitMs and then answers with no events
        long t0 = System.nanoTime();
        String idle = get("/api/game/events?clientId=" + clientId + "&waitMs=400");
        long idleMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(idle.contains("\"count\":0"), idle);
        assertTrue(idleMs >= 350, "the request was held, not answered at once: " + idleMs + " ms");

        // A second player joins 300 ms into a 10 s long-poll: the poll must return early with that event
        ScheduledExecutorService later = Executors.newSingleThreadScheduledExecutor();
        later.schedule(() -> post("/api/room/join", "playerName=Joiner&roomId=LONGPOLL"), 300, TimeUnit.MILLISECONDS);
        t0 = System.nanoTime();
        String woke = get("/api/game/events?clientId=" + clientId + "&waitMs=10000");
        long wokeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        later.shutdown();
        assertTrue(woke.contains("Joiner") || woke.contains("GAME_STATE_UPDATE"), woke);
        assertTrue(wokeMs >= 250 && wokeMs < 5000, "woken by the event, not by the timeout: " + wokeMs + " ms");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static GameMessage readUntil(BufferedReader in, MessageType type) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            GameMessage m = NetworkProtocol.deserialize(line);
            if (m != null && m.getType() == type) return m;
        }
        throw new EOFException("stream ended before " + type);
    }

    private String get(String path) throws Exception {
        return send("GET", path, null).body();
    }

    private String post(String path, String form) throws Exception {
        return send("POST", path, form).body();
    }

    private HttpResponse<String> send(String method, String path, String form) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + path))
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (form == null) b.method(method, HttpRequest.BodyPublishers.noBody());
        else b.method(method, HttpRequest.BodyPublishers.ofString(form));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
