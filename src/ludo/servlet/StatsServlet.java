package ludo.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ludo.server.GameRoom;
import ludo.server.GameRoomManager;
import ludo.server.RequestQueueProcessor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/stats — live Producer-Consumer queue metrics. This is the evidence
 * endpoint for stress testing: JMeter/Postman can assert dropped == 0 and watch
 * processed catch up with enqueued after a burst.
 */
public class StatsServlet extends LudoServlet {

    private static final long serialVersionUID = 1L;

    public StatsServlet(GameRoomManager roomManager, RequestQueueProcessor queueProcessor) {
        super(roomManager, queueProcessor);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("capacity", RequestQueueProcessor.QUEUE_CAPACITY);
        queue.put("workerThreads", RequestQueueProcessor.WORKER_THREADS);
        queue.put("currentDepth", queueProcessor.getQueueSize());
        queue.put("peakDepth", queueProcessor.getPeakQueueDepth());
        queue.put("enqueued", queueProcessor.getTotalEnqueued());
        queue.put("processed", queueProcessor.getTotalProcessed());
        queue.put("dropped", queueProcessor.getTotalDropped());
        queue.put("avgProcessingMs", Math.round(queueProcessor.getAverageProcessingTimeMs() * 1000.0) / 1000.0);
        queue.put("avgQueueWaitMs", Math.round(queueProcessor.getAverageQueueWaitMs() * 1000.0) / 1000.0);

        Map<String, Object> rooms = new LinkedHashMap<>();
        for (GameRoom room : roomManager.getRooms().values()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("players", room.getPlayerCount());
            r.put("started", room.isGameStarted());
            r.put("gameOver", room.isGameOver());
            r.put("round", room.getRoundNumber());
            rooms.put(room.getRoomId(), r);
        }

        Runtime rt = Runtime.getRuntime();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("liveThreads", Thread.activeCount());
        jvm.put("usedMemoryMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        jvm.put("availableProcessors", rt.availableProcessors());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("activeSessions", roomManager.getActiveClientCount());
        body.put("queue", queue);
        body.put("rooms", rooms);
        body.put("jvm", jvm);
        body.put("timestamp", System.currentTimeMillis());
        writeJson(resp, 200, body);
    }
}
