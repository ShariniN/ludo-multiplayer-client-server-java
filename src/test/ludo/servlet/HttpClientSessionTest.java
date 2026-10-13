package ludo.servlet;

import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HTTP session and long-poll tests")
class HttpClientSessionTest {

    @Nested
    @DisplayName("Long-poll support")
    class LongPoll {
        @Test
        @DisplayName("onNextEvent fires once, on the next delivered event")
        void firesOnceOnNextEvent() {
            HttpClientSession s = new HttpClientSession("HTTP-LP1", "web");
            AtomicInteger calls = new AtomicInteger();
            s.onNextEvent(calls::incrementAndGet);
            assertEquals(0, calls.get(), "nothing pending yet");
            s.sendMessage(new GameMessage(MessageType.HEARTBEAT, "S", "R"));
            s.sendMessage(new GameMessage(MessageType.HEARTBEAT, "S", "R"));
            assertEquals(1, calls.get(), "one-shot listener");
        }

        @Test
        @DisplayName("onNextEvent fires immediately when events are already waiting")
        void firesImmediatelyWhenPending() {
            HttpClientSession s = new HttpClientSession("HTTP-LP2", "web");
            s.sendMessage(new GameMessage(MessageType.HEARTBEAT, "S", "R"));
            AtomicInteger calls = new AtomicInteger();
            s.onNextEvent(calls::incrementAndGet);
            assertEquals(1, calls.get());
        }

        @Test
        @DisplayName("a cancelled listener (timed-out long-poll) is never called")
        void cancelledListenerNotCalled() {
            HttpClientSession s = new HttpClientSession("HTTP-LP3", "web");
            AtomicInteger calls = new AtomicInteger();
            Runnable listener = calls::incrementAndGet;
            s.onNextEvent(listener);
            s.cancelNextEvent(listener);
            s.sendMessage(new GameMessage(MessageType.HEARTBEAT, "S", "R"));
            assertEquals(0, calls.get());
        }

        @Test
        @DisplayName("waitMs is clamped to 0..30000 and garbage means no wait")
        void waitParsing() {
            assertEquals(0, GameEventsServlet.parseWait(null));
            assertEquals(0, GameEventsServlet.parseWait("abc"));
            assertEquals(0, GameEventsServlet.parseWait("-5"));
            assertEquals(1500, GameEventsServlet.parseWait(" 1500 "));
            assertEquals(GameEventsServlet.MAX_WAIT_MS, GameEventsServlet.parseWait("999999"));
        }
    }

    @Nested
    @DisplayName("HttpClientSession outbox")
    class Session {
        @Test
        @DisplayName("events are queued in order and drained by pollEvents")
        void queueAndDrain() {
            HttpClientSession s = new HttpClientSession("HTTP-1", "web");
            s.sendMessage(new GameMessage(MessageType.ROOM_JOINED, "SERVER", "R").put("color", "RED"));
            s.sendMessage(new GameMessage(MessageType.GAME_STATE_UPDATE, "SERVER", "R"));
            assertEquals(2, s.getPendingEventCount());
            var events = s.pollEvents();
            assertEquals(MessageType.ROOM_JOINED, events.get(0).getType());
            assertEquals(MessageType.GAME_STATE_UPDATE, events.get(1).getType());
            assertEquals(0, s.getPendingEventCount());
        }

        @Test
        @DisplayName("outbox is bounded: oldest events are dropped beyond 500")
        void bounded() {
            HttpClientSession s = new HttpClientSession("HTTP-2", "web");
            for (int i = 0; i < 600; i++) s.sendMessage(new GameMessage(MessageType.HEARTBEAT, "S", "R").put("i", i));
            assertEquals(500, s.getPendingEventCount());
            assertEquals(100, s.pollEvents().get(0).getInt("i", -1));
        }

        @Test
        @DisplayName("awaitEvent wakes up when a matching event arrives from another thread")
        void awaitWakesUp() throws Exception {
            HttpClientSession s = new HttpClientSession("HTTP-3", "web");
            long since = System.currentTimeMillis();
            Thread producer = new Thread(() -> {
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                s.sendMessage(new GameMessage(MessageType.DICE_ROLLED, "SERVER", "R").put("diceValue", 4));
            });
            producer.start();
            long t0 = System.nanoTime();
            GameMessage got = s.awaitEvent(since, 3000, MessageType.DICE_ROLLED, MessageType.ERROR);
            long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            assertNotNull(got);
            assertEquals(4, got.getInt("diceValue", 0));
            assertTrue(waitedMs >= 100 && waitedMs < 2500, "should block until the event, not spin or time out: " + waitedMs);
            assertEquals(1, s.getPendingEventCount(), "awaitEvent must not consume the event");
        }

        @Test
        @DisplayName("awaitEvent ignores stale events and times out cleanly")
        void awaitIgnoresStale() throws Exception {
            HttpClientSession s = new HttpClientSession("HTTP-4", "web");
            s.sendMessage(new GameMessage(MessageType.ERROR, "SERVER", "R"));
            Thread.sleep(5);
            long since = System.currentTimeMillis();
            assertNull(s.awaitEvent(since, 100, MessageType.ERROR));
        }

        @Test
        @DisplayName("session carries player identity like a socket session")
        void identity() {
            HttpClientSession s = new HttpClientSession("HTTP-5", "Zed");
            s.setAssignedColor(Color.BLUE);
            s.setPlayerName("Zed2");
            assertEquals("HTTP-5", s.getClientId());
            assertEquals(Color.BLUE, s.getAssignedColor());
            assertEquals("Zed2", s.getPlayerName());
            assertNull(s.getRoom());
            assertTrue(s.getIdleMillis() >= 0);
        }
    }
}
