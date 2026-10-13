package ludo.server;

import ludo.network.GameMessage;

/**
 * One queued request: the sending session plus its message, stamped with the
 * enqueue time so queue wait can be measured.
 */
public class RequestTask {
    private final ClientSession session;
    private final GameMessage message;
    private final long enqueuedNanos;

    public RequestTask(ClientSession session, GameMessage message) {
        this.session = session;
        this.message = message;
        this.enqueuedNanos = System.nanoTime();
    }

    public ClientSession getSession() {
        return session;
    }

    public GameMessage getMessage() {
        return message;
    }

    /** Nanoseconds spent waiting in the queue before a worker picked the task up. */
    public long getQueueWaitNanos() {
        return System.nanoTime() - enqueuedNanos;
    }
}
