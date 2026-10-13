package ludo.servlet;

import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.server.ClientSession;
import ludo.server.GameRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A player connected over stateless HTTP instead of a persistent socket.
 *
 * HTTP cannot be pushed to, so server events are parked in an outbox that the
 * client drains with GET /api/game/events. That request may long-poll: it registers
 * a one-shot listener (onNextEvent) and is answered as soon as an event arrives.
 * The outbox is bounded so a client that stops polling cannot grow server memory
 * without limit.
 *
 * awaitEvent() lets a servlet block briefly for the outcome of the action it just
 * queued (e.g. the DICE_ROLLED after a ROLL_DICE), turning the asynchronous
 * queue round-trip into a synchronous HTTP response for tools like Postman.
 */
public class HttpClientSession implements ClientSession {

    private static final int OUTBOX_LIMIT = 500;

    private final String clientId;
    private final ConcurrentLinkedQueue<GameMessage> outbox = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition eventArrived = lock.newCondition();
    private final AtomicReference<Runnable> nextEventListener = new AtomicReference<>();

    private volatile Color assignedColor;
    private volatile String playerName;
    private volatile GameRoom room;
    private volatile long lastSeenMillis = System.currentTimeMillis();

    public HttpClientSession(String clientId, String playerName) {
        this.clientId = clientId;
        this.playerName = playerName;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public void sendMessage(GameMessage message) {
        outbox.add(message);
        while (outbox.size() > OUTBOX_LIMIT) {
            outbox.poll(); // drop oldest
        }
        lock.lock();
        try {
            eventArrived.signalAll();
        } finally {
            lock.unlock();
        }
        fireNextEventListener();
    }

    /**
     * Registers a callback that runs once, on the thread that delivers the next event
     * (a QueueWorker, possibly while it holds a room lock - so the callback must only
     * hand work off, never block). Runs immediately if events are already waiting.
     */
    public void onNextEvent(Runnable listener) {
        nextEventListener.set(listener);
        if (!outbox.isEmpty()) {
            fireNextEventListener();
        }
    }

    /** Removes the callback if it is still registered (e.g. the long-poll timed out). */
    public void cancelNextEvent(Runnable listener) {
        nextEventListener.compareAndSet(listener, null);
    }

    private void fireNextEventListener() {
        Runnable listener = nextEventListener.getAndSet(null);
        if (listener != null) {
            listener.run();
        }
    }

    /** Returns the newest pending event of the given type at/after sinceMillis, or null. */
    public GameMessage latestEvent(long sinceMillis, MessageType type) {
        GameMessage found = null;
        for (GameMessage m : outbox) {
            if (m.getType() == type && m.getTimestamp() >= sinceMillis) found = m;
        }
        return found;
    }

    /** Drains all pending events (oldest first). */
    public List<GameMessage> pollEvents() {
        touch();
        List<GameMessage> events = new ArrayList<>();
        GameMessage m;
        while ((m = outbox.poll()) != null) {
            events.add(m);
        }
        return events;
    }

    /**
     * Waits up to timeoutMs for an event of one of the given types, stamped at or after
     * sinceMillis, without removing anything from the outbox. Returns null on timeout.
     */
    public GameMessage awaitEvent(long sinceMillis, long timeoutMs, MessageType... types) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        lock.lock();
        try {
            while (true) {
                for (GameMessage m : outbox) {
                    if (m.getTimestamp() < sinceMillis) continue; // ignore events from before this request
                    for (MessageType t : types) {
                        if (m.getType() == t) return m;
                    }
                }
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) return null;
                eventArrived.awaitNanos(remaining);
            }
        } finally {
            lock.unlock();
        }
    }

    public void touch() {
        lastSeenMillis = System.currentTimeMillis();
    }

    public long getIdleMillis() {
        return System.currentTimeMillis() - lastSeenMillis;
    }

    public int getPendingEventCount() {
        return outbox.size();
    }

    @Override public Color getAssignedColor()            { return assignedColor; }
    @Override public void setAssignedColor(Color color)  { this.assignedColor = color; }
    @Override public String getPlayerName()              { return playerName; }
    @Override public void setPlayerName(String name)     { this.playerName = name; }
    @Override public GameRoom getRoom()                  { return room; }
    @Override public void setRoom(GameRoom room)         { this.room = room; }
}
