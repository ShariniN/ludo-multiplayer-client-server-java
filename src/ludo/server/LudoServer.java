package ludo.server;

import ludo.db.PersistenceGateway;
import ludo.servlet.TomcatServerRunner;
import org.apache.catalina.LifecycleException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tier 2: the game server process.
 *
 * Threads inside this process:
 *   - main thread: blocks in ServerSocket.accept(), one ClientHandler per TCP client (cached pool)
 *   - QueueWorker-1..8: consumers of the RequestQueueProcessor, run all game logic
 *   - Tomcat http-nio-*-exec threads: serve the servlets (same rooms, same queue)
 *   - PersistenceWriter: single ordered writer to the Database Tier
 */
public class LudoServer {

    private final int port;
    private final int httpPort;
    private ServerSocket serverSocket;
    private final ExecutorService clientThreadPool;
    private final GameRoomManager roomManager;
    private final RequestQueueProcessor queueProcessor;
    private final PersistenceGateway persistence;
    private final TomcatServerRunner httpContainer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /** Socket-only server (used by tests and the stress harness). */
    public LudoServer(int port, PersistenceGateway persistence) {
        this(port, -1, persistence);
    }

    /** Full server: TCP on port, embedded Tomcat on httpPort (-1 to disable). */
    public LudoServer(int port, int httpPort, PersistenceGateway persistence) {
        this.port = port;
        this.httpPort = httpPort;
        this.persistence = persistence;
        this.clientThreadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ClientHandler");
            t.setDaemon(true);
            return t;
        });
        this.roomManager = new GameRoomManager(persistence);
        this.queueProcessor = new RequestQueueProcessor(roomManager);
        this.httpContainer = httpPort > 0
                ? new TomcatServerRunner(httpPort, roomManager, queueProcessor, persistence)
                : null;
    }

    public void start() {
        if (isRunning.getAndSet(true)) return;

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("===============================================================");
            System.out.println("   LUDO-T GAME SERVER (TIER 2) - TCP PORT " + port);
            System.out.println("   Queue: ArrayBlockingQueue(" + RequestQueueProcessor.QUEUE_CAPACITY
                    + ") + " + RequestQueueProcessor.WORKER_THREADS + " QueueWorker threads");
            System.out.println("   Persistence: " + persistence.describe());
            if (httpContainer != null) {
                startTomcat();
            }
            System.out.println("===============================================================");

            while (isRunning.get()) {
                Socket clientSocket = serverSocket.accept();
                String clientId = UUID.randomUUID().toString();
                ClientHandler handler = new ClientHandler(clientSocket, clientId, queueProcessor, roomManager);
                roomManager.registerClient(handler);
                clientThreadPool.submit(handler);
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                System.err.println("[LudoServer] Server socket error: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void startTomcat() {
        try {
            httpContainer.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException("Embedded Tomcat failed to start on port " + httpPort, e);
        }
    }

    public void stop() {
        if (!isRunning.getAndSet(false)) return;
        System.out.println("[LudoServer] Shutting down server...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        if (httpContainer != null) httpContainer.stop();
        queueProcessor.shutdown();
        roomManager.shutdown();
        clientThreadPool.shutdownNow();
        System.out.println("[LudoServer] Server stopped successfully.");
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public RequestQueueProcessor getQueueProcessor() {
        return queueProcessor;
    }

    public GameRoomManager getRoomManager() {
        return roomManager;
    }

    /** Bound HTTP port, or -1 when the HTTP gateway is disabled. */
    public int getHttpPort() {
        return httpContainer != null ? httpContainer.getPort() : -1;
    }

    public PersistenceGateway getPersistence() {
        return persistence;
    }
}
