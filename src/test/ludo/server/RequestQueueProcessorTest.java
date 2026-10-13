package ludo.server;

import ludo.db.JdbcPersistenceGateway;
import ludo.game.Dice;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RequestQueueProcessor producer-consumer tests")
class RequestQueueProcessorTest {

    private JdbcPersistenceGateway persistence;
    private GameRoomManager manager;
    private RequestQueueProcessor queue;

    @BeforeEach
    void setUp() {
        Dice.resetInstance();
        persistence = JdbcPersistenceGateway.inMemory("test-" + UUID.randomUUID());
        manager = new GameRoomManager(persistence);
        queue = new RequestQueueProcessor(manager);
    }

    @AfterEach
    void tearDown() {
        try {
            queue.shutdown();
            manager.shutdown();
            Dice.resetInstance();
        } finally {
            persistence.close();
        }
    }

    @Test
    @DisplayName("a single heartbeat is consumed by a worker thread and answered")
    void singleRequestProcessed() throws Exception {
        RecordingSession s = new RecordingSession("s1");
        assertTrue(queue.enqueue(new RequestTask(s, new GameMessage(MessageType.HEARTBEAT, "s1", "ROOM-1"))));
        assertTrue(queue.awaitDrained(2000));
        GameMessage ack = s.last(MessageType.HEARTBEAT);
        assertNotNull(ack);
        assertTrue(ack.get("worker").startsWith("QueueWorker-"), "must run on a pool worker, not the caller");
        assertEquals(1, queue.getTotalProcessed());
        assertEquals(0, queue.getTotalDropped());
    }

    @Test
    @DisplayName("16 producer threads x 200 requests: every request is queued and processed, none dropped")
    void concurrentProducers() throws Exception {
        int producers = 16, perProducer = 200;
        ExecutorService pool = Executors.newFixedThreadPool(producers);
        CountDownLatch start = new CountDownLatch(1);
        List<RecordingSession> sessions = new ArrayList<>();
        AtomicInteger rejected = new AtomicInteger();

        for (int p = 0; p < producers; p++) {
            RecordingSession s = new RecordingSession("p" + p);
            sessions.add(s);
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perProducer; i++) {
                    if (!queue.enqueue(new RequestTask(s, new GameMessage(MessageType.HEARTBEAT, s.getClientId(), "ROOM-1")))) {
                        rejected.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(queue.awaitDrained(10000), "queue must drain");

        long expected = (long) producers * perProducer;
        assertEquals(expected, queue.getTotalEnqueued());
        assertEquals(expected, queue.getTotalProcessed() + queue.getTotalDropped());
        assertEquals(0, queue.getTotalDropped());
        assertEquals(0, rejected.get());
        long acks = sessions.stream().mapToLong(s -> s.count(MessageType.HEARTBEAT)).sum();
        assertEquals(expected, acks, "each request gets exactly one response");
        assertTrue(queue.getPeakQueueDepth() > 0, "burst must actually have queued work");
    }

    @Test
    @DisplayName("bounded buffer: with all 8 workers blocked, extra producers block (back-pressure); only a timed-out one is refused")
    void backPressureWhenFull() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch blockedWorkers = new CountDownLatch(RequestQueueProcessor.WORKER_THREADS);
        ClientSession blocker = new RecordingSession("blocker") {
            @Override public void sendMessage(GameMessage message) {
                blockedWorkers.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                super.sendMessage(message);
            }
        };
        // occupy every consumer thread
        for (int i = 0; i < RequestQueueProcessor.WORKER_THREADS; i++) {
            assertTrue(queue.enqueue(new RequestTask(blocker, new GameMessage(MessageType.HEARTBEAT, "b", "ROOM-1"))));
        }
        assertTrue(blockedWorkers.await(2, TimeUnit.SECONDS), "all workers should now be busy");

        // fill the bounded buffer exactly
        RecordingSession s = new RecordingSession("flood");
        for (int i = 0; i < RequestQueueProcessor.QUEUE_CAPACITY; i++) {
            assertTrue(queue.enqueue(new RequestTask(s, new GameMessage(MessageType.HEARTBEAT, "flood", "ROOM-1"))), "slot " + i);
        }
        assertEquals(RequestQueueProcessor.QUEUE_CAPACITY, queue.getQueueSize());

        // one more: the producer blocks (back-pressure) and is only refused once the timeout elapses
        long t0 = System.nanoTime();
        assertFalse(queue.enqueue(new RequestTask(s, new GameMessage(MessageType.HEARTBEAT, "flood", "ROOM-1")), 300));
        long blockedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(blockedMs >= 280, "producer must wait for a slot before giving up, waited " + blockedMs + " ms");
        assertEquals(1, queue.getTotalDropped());

        // ...but a producer that waits long enough gets its slot as soon as a worker frees one
        Thread lateProducer = new Thread(() -> queue.enqueue(new RequestTask(s, new GameMessage(MessageType.HEARTBEAT, "flood", "ROOM-1")), 5000));
        lateProducer.start();
        Thread.sleep(100);
        assertTrue(lateProducer.isAlive(), "producer should still be blocked while the buffer is full");

        release.countDown();
        lateProducer.join(5000);
        assertFalse(lateProducer.isAlive());
        assertTrue(queue.awaitDrained(10000));
        assertEquals(RequestQueueProcessor.QUEUE_CAPACITY + 1, s.count(MessageType.HEARTBEAT), "every queued request was still served");
        assertEquals(1, queue.getTotalDropped(), "only the timed-out request was ever rejected");
        assertEquals(0, queue.getQueueSize());
    }

    @Test
    @DisplayName("4 players flooding conflicting ROLL/MOVE requests from 4 threads leave the room consistent")
    void conflictingRequestsStayConsistent() throws Exception {
        ludo.enums.Color[] colours = ludo.enums.Color.values();
        RecordingSession[] players = new RecordingSession[4];
        for (int i = 0; i < 4; i++) {
            players[i] = new RecordingSession("p" + i);
            queue.enqueue(new RequestTask(players[i], new GameMessage(MessageType.JOIN_ROOM, "p" + i, "FLOOD")
                    .put("roomId", "FLOOD").put("preferredColor", colours[i].name())));
        }
        assertTrue(queue.awaitDrained(2000));
        GameRoom room = manager.getRoom("FLOOD");
        assertTrue(room.isGameStarted());

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        for (RecordingSession p : players) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 500; i++) {
                    queue.enqueue(new RequestTask(p, new GameMessage(MessageType.ROLL_DICE, p.getClientId(), "FLOOD")));
                    for (String piece : new String[]{"1", "2", "3", "4"}) {
                        queue.enqueue(new RequestTask(p, new GameMessage(MessageType.MOVE_PIECE, p.getClientId(), "FLOOD")
                                .put("piece", p.getAssignedColor().getInitial() + piece)));
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        assertTrue(queue.awaitDrained(20000));

        assertEquals(queue.getTotalEnqueued(), queue.getTotalProcessed());
        assertEquals(0, queue.getTotalDropped());

        // Broadcast consistency: every player saw exactly the same sequence of rolls
        long rolls = players[0].count(MessageType.DICE_ROLLED);
        for (RecordingSession p : players) assertEquals(rolls, p.count(MessageType.DICE_ROLLED));
        assertTrue(rolls > 0);
        for (GameMessage m : players[0].received) {
            if (m.getType() == MessageType.DICE_ROLLED) {
                int v = m.getInt("diceValue", 0);
                assertTrue(v >= 1 && v <= 6, "corrupted dice value " + v);
            }
        }
        // Board integrity: 16 tokens, each in a legal state, positions in range
        String[] tokens = room.buildStateMessage().get("pieces").split(";");
        assertEquals(16, tokens.length);
        for (String t : tokens) {
            String[] f = t.split(":");
            assertTrue(java.util.Set.of("BASE", "ACTIVE", "HOME_STRAIGHT", "HOME").contains(f[1]), t);
            int pos = Integer.parseInt(f[2]);
            assertTrue(pos >= -1 && pos < 52, t);
        }
        // Out-of-turn requests were rejected, not silently applied
        long errors = 0;
        for (RecordingSession p : players) errors += p.count(MessageType.ERROR);
        assertTrue(errors > 0, "out-of-turn rolls must be rejected with ERROR");
    }

    @Test
    @DisplayName("worker survives an exception thrown while processing a task")
    void workerSurvivesException() throws Exception {
        ClientSession bad = new RecordingSession("bad") {
            @Override public void sendMessage(GameMessage message) { throw new IllegalStateException("boom"); }
        };
        queue.enqueue(new RequestTask(bad, new GameMessage(MessageType.HEARTBEAT, "bad", "ROOM-1")));
        RecordingSession good = new RecordingSession("good");
        queue.enqueue(new RequestTask(good, new GameMessage(MessageType.HEARTBEAT, "good", "ROOM-1")));
        assertTrue(queue.awaitDrained(2000));
        assertTrue(good.has(MessageType.HEARTBEAT), "later tasks still processed after a failure");
        assertEquals(1, queue.getTotalFailed());
        assertEquals(2, queue.getTotalProcessed(), "the failed task still counts as consumed");
    }

    @Test
    @DisplayName("metrics: processing time and queue wait are measured")
    void metrics() throws Exception {
        RecordingSession s = new RecordingSession("m");
        for (int i = 0; i < 50; i++) {
            queue.enqueue(new RequestTask(s, new GameMessage(MessageType.HEARTBEAT, "m", "ROOM-1")));
        }
        assertTrue(queue.awaitDrained(2000));
        assertTrue(queue.getAverageProcessingTimeMs() >= 0);
        assertTrue(queue.getAverageQueueWaitMs() >= 0);
        assertEquals(0, queue.getQueueSize());
    }
}
