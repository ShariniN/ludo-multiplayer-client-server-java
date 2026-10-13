package ludo.servlet;

import jakarta.servlet.http.HttpServlet;
import ludo.db.PersistenceGateway;
import ludo.server.ClientSession;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.ServerInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedded Apache Tomcat hosting the LUDO-T servlets inside the game-server process.
 *
 * The servlets are handed the SAME GameRoomManager, RequestQueueProcessor and
 * PersistenceGateway as the TCP socket listener, so HTTP players (Postman, JMeter,
 * a browser) and Swing players share one set of rooms and one request queue.
 *
 * Concurrency: Tomcat's NIO connector serves requests from a bounded worker pool
 * (MAX_THREADS); when every worker is busy, up to ACCEPT_COUNT connections wait in the
 * operating system's backlog - the HTTP counterpart of the request queue's back-pressure.
 * A scheduled reaper evicts HTTP sessions that stopped polling.
 */
public final class TomcatServerRunner {

    public static final int DEFAULT_HTTP_PORT = 8081;
    static final int MAX_THREADS = 50;
    static final int ACCEPT_COUNT = 100;
    private static final long SESSION_IDLE_LIMIT_MS = 10 * 60 * 1000;

    /** Strong reference so the JUL configuration is not garbage-collected. */
    private static final Logger TOMCAT_LOG = Logger.getLogger("org.apache");

    private final int port;
    private final GameRoomManager roomManager;
    private final RequestQueueProcessor queueProcessor;
    private final PersistenceGateway persistence;
    private final ScheduledExecutorService reaper;
    private Tomcat tomcat;
    private Path baseDir;

    public TomcatServerRunner(int port, GameRoomManager roomManager,
                              RequestQueueProcessor queueProcessor, PersistenceGateway persistence) {
        this.port = port;
        this.roomManager = roomManager;
        this.queueProcessor = queueProcessor;
        this.persistence = persistence;
        this.reaper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HttpSessionReaper");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() throws LifecycleException {
        TOMCAT_LOG.setLevel(Level.WARNING); // keep the server console readable
        try {
            baseDir = Files.createTempDirectory("ludo-tomcat");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create Tomcat work directory", e);
        }

        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        Connector connector = new Connector();
        connector.setPort(port);
        connector.setProperty("maxThreads", String.valueOf(MAX_THREADS));
        connector.setProperty("acceptCount", String.valueOf(ACCEPT_COUNT));
        connector.setURIEncoding("UTF-8");
        tomcat.getService().addConnector(connector);
        tomcat.setConnector(connector);

        Context ctx = tomcat.addContext("", baseDir.toString());
        ctx.setRequestCharacterEncoding("UTF-8");
        ctx.setResponseCharacterEncoding("UTF-8");

        // Exact mappings win over path mappings, so /api/game/events and /api/game/state
        // resolve to their own servlets before /api/game/*.
        mount(ctx, "room", new RoomServlet(roomManager, queueProcessor), "/api/room/*");
        mount(ctx, "turn", new GameTurnServlet(roomManager, queueProcessor), "/api/game/*");
        mount(ctx, "events", new GameEventsServlet(roomManager, queueProcessor), "/api/game/events");
        mount(ctx, "state", new GameStateServlet(roomManager, queueProcessor), "/api/game/state");
        mount(ctx, "chat", new ChatServlet(roomManager, queueProcessor), "/api/chat");
        mount(ctx, "leaderboard", new LeaderboardServlet(roomManager, queueProcessor, persistence), "/api/leaderboard");
        mount(ctx, "stats", new StatsServlet(roomManager, queueProcessor), "/api/stats");

        tomcat.start();
        reaper.scheduleAtFixedRate(this::evictIdleSessions, 60, 60, TimeUnit.SECONDS);

        System.out.println("   HTTP: Apache Tomcat " + ServerInfo.getServerNumber() + " (embedded) on port "
                + getPort() + ", maxThreads " + MAX_THREADS + ", acceptCount " + ACCEPT_COUNT);
        System.out.println("     GET  /api/room/status   POST /api/room/join | /api/room/leave");
        System.out.println("     POST /api/game/roll     POST /api/game/move   GET /api/game/events[?waitMs=]");
        System.out.println("     GET  /api/game/state    GET  /api/leaderboard GET /api/stats");
        System.out.println("     GET|POST /api/chat");
    }

    private static void mount(Context ctx, String name, HttpServlet servlet, String mapping) {
        Wrapper wrapper = Tomcat.addServlet(ctx, name, servlet);
        wrapper.setAsyncSupported(true); // required by the long-poll in GameEventsServlet
        wrapper.addMapping(mapping);
    }

    private void evictIdleSessions() {
        for (String id : roomManager.getClientIds()) {
            ClientSession s = roomManager.getClient(id);
            if (s instanceof HttpClientSession http && http.getIdleMillis() > SESSION_IDLE_LIMIT_MS) {
                if (s.getRoom() != null) s.getRoom().removePlayer(s);
                roomManager.removeClient(s);
                System.out.println("[Tomcat] Evicted idle HTTP session " + id);
            }
        }
    }

    public void stop() {
        reaper.shutdownNow();
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (LifecycleException e) {
                System.err.println("[Tomcat] Error while stopping: " + e.getMessage());
            }
            tomcat = null;
        }
        deleteQuietly(baseDir);
        System.out.println("[Tomcat] Stopped.");
    }

    /** The bound port (useful when started on port 0 in tests). */
    public int getPort() {
        return tomcat != null ? tomcat.getConnector().getLocalPort() : port;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temporary directory; the OS cleans it up eventually
        }
    }
}
