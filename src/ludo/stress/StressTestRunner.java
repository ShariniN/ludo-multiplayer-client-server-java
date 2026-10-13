package ludo.stress;

import ludo.db.JdbcPersistenceGateway;
import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.server.LudoServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Concurrency & Stress Testing Suite (COMP63038 Assignment 2, step 4).
 *
 * Launches N automated bot clients that (a) play a full LUDO-T match as fast as
 * the server allows and (b) simultaneously fire HEARTBEAT requests at a fixed
 * rate on separate scheduler threads — i.e. asynchronous requests in rapid
 * succession from several clients at once.
 *
 * Usage:
 *   StressTestRunner                      -> starts a private server on port 8089 and reports queue metrics
 *   StressTestRunner <host> <port>        -> hammers an already running server (run_server.bat)
 *   Optional 3rd/4th args: bots (default 4) and seconds (default 10)
 */
public class StressTestRunner {

    public static void main(String[] args) throws Exception {
        boolean external = args.length >= 2;
        String host = external ? args[0] : "127.0.0.1";
        int port = external ? Integer.parseInt(args[1]) : 8089;
        int botCount = args.length >= 3 ? Integer.parseInt(args[2]) : 4;
        long durationMs = (args.length >= 4 ? Integer.parseInt(args[3]) : 10) * 1000L;
        String testRoom = "STRESS-" + System.currentTimeMillis() % 100000;

        System.out.println("===============================================================");
        System.out.println("   LUDO-T CONCURRENCY STRESS TEST SUITE (COMP63038)");
        System.out.println("   Target: " + host + ":" + port + (external ? " (external server)" : " (private in-process server)"));
        System.out.println("   Bots: " + botCount + "   Duration: " + durationMs / 1000 + "s   Room: " + testRoom);
        System.out.println("===============================================================");

        LudoServer server = null;
        if (!external) {
            server = new LudoServer(port, JdbcPersistenceGateway.inMemory("stress"));
            LudoServer s = server;
            Thread serverThread = new Thread(s::start, "StressTestServerThread");
            serverThread.setDaemon(true);
            serverThread.start();
            Thread.sleep(800); // allow server socket to bind
        }

        // Launch the bot clients (each has its own reader thread)
        ExecutorService clientPool = Executors.newFixedThreadPool(botCount);
        List<AutomatedTestClient> bots = new ArrayList<>();
        Color[] colors = Color.values();
        for (int i = 0; i < botCount; i++) {
            Color preferred = i < colors.length ? colors[i] : null;
            AutomatedTestClient bot = new AutomatedTestClient(host, port, "StressBot-" + (i + 1), preferred, testRoom);
            if (bot.connect()) {
                bots.add(bot);
                clientPool.submit(bot);
            }
        }
        System.out.println("[StressTest] " + bots.size() + " automated bot clients connected and playing...");
        long testStart = System.currentTimeMillis();

        // Request storm: every bot sends a HEARTBEAT every 50 ms on a scheduler thread,
        // independently of (and concurrently with) its game-play traffic.
        ScheduledExecutorService spammer = Executors.newScheduledThreadPool(botCount);
        for (AutomatedTestClient bot : bots) {
            spammer.scheduleAtFixedRate(() ->
                    bot.send(new GameMessage(MessageType.HEARTBEAT, bot.getBotName(), testRoom)),
                    100, 50, TimeUnit.MILLISECONDS);
        }

        Thread.sleep(durationMs);

        spammer.shutdownNow();
        long elapsedMs = System.currentTimeMillis() - testStart;

        long sent = 0, received = 0, roundTripSamples = 0, roundTripNanos = 0;
        boolean anyGameOver = false;
        for (AutomatedTestClient bot : bots) {
            sent += bot.getRequestsSent();
            received += bot.getResponsesReceived();
            roundTripSamples += bot.getHeartbeatSamples();
            roundTripNanos += bot.getHeartbeatRoundTripNanos();
            anyGameOver |= bot.isGameOver();
            bot.close();
        }
        clientPool.shutdownNow();

        System.out.println("\n===============================================================");
        System.out.println("               STRESS TEST RESULTS & METRICS                   ");
        System.out.println("===============================================================");
        System.out.printf ("  Duration:                    %.1f seconds%n", elapsedMs / 1000.0);
        System.out.println("  Bot clients:                 " + bots.size());
        System.out.println("  Requests sent by bots:       " + sent);
        System.out.println("  Messages received by bots:   " + received);
        if (roundTripSamples > 0) {
            System.out.printf ("  Avg heartbeat round-trip:    %.3f ms (%d samples)%n",
                    roundTripNanos / 1_000_000.0 / roundTripSamples, roundTripSamples);
        }
        System.out.println("  Match reached GAME_OVER:     " + (anyGameOver ? "yes" : "no (still playing when test ended)"));

        if (server != null) {
            server.getQueueProcessor().awaitDrained(3000);
            long enq = server.getQueueProcessor().getTotalEnqueued();
            long proc = server.getQueueProcessor().getTotalProcessed();
            long drop = server.getQueueProcessor().getTotalDropped();
            System.out.println("  --- server-side queue metrics ---");
            System.out.println("  Total enqueued requests:     " + enq);
            System.out.println("  Total processed tasks:       " + proc);
            System.out.println("  Total dropped tasks:         " + drop + " (expected: 0)");
            System.out.println("  Peak queue depth:            " + server.getQueueProcessor().getPeakQueueDepth()
                    + " / " + ludo.server.RequestQueueProcessor.QUEUE_CAPACITY);
            System.out.printf ("  Avg queue wait:              %.3f ms%n", server.getQueueProcessor().getAverageQueueWaitMs());
            System.out.printf ("  Avg task processing time:    %.3f ms%n", server.getQueueProcessor().getAverageProcessingTimeMs());
            System.out.printf ("  Effective throughput:        %.1f requests/sec%n", proc * 1000.0 / Math.max(1, elapsedMs));
            System.out.println("===============================================================");
            if (drop == 0 && proc == enq && proc > 50) {
                System.out.println("PASS: every request was queued and processed - 0 dropped, none lost.");
            } else {
                System.out.println("WARNING: dropped=" + drop + " processed=" + proc + " enqueued=" + enq);
            }
            server.stop();
        } else {
            System.out.println("===============================================================");
            System.out.println("Server-side counters: GET http://" + host + ":<httpPort>/api/stats"
                    + " (8081 by default)");
        }
        System.exit(0);
    }
}
