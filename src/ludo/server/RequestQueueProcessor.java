package ludo.server;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Producer-consumer request queue. ClientHandler and servlet threads enqueue();
 * a fixed pool of QueueWorker threads drains the bounded queue.
 *
 * When the queue is full the producer blocks up to ENQUEUE_TIMEOUT_MS, so a socket
 * client's reads stall and TCP pushes back; only after the timeout is the request
 * rejected (with an ERROR reply, never silently).
 */
public class RequestQueueProcessor {

    public static final int QUEUE_CAPACITY = 2000;
    public static final int WORKER_THREADS = 8;
    public static final long ENQUEUE_TIMEOUT_MS = 2000;

    private final BlockingQueue<RequestTask> taskQueue;
    private final ExecutorService workerPool;
    private final GameRoomManager roomManager;
    private volatile boolean isRunning = true;

    // Stress-test metrics (lock-free atomic counters)
    private final AtomicLong totalEnqueuedTasks   = new AtomicLong(0);
    private final AtomicLong totalProcessedTasks  = new AtomicLong(0);
    private final AtomicLong totalDroppedTasks    = new AtomicLong(0);
    private final AtomicLong totalFailedTasks     = new AtomicLong(0);
    private final AtomicLong totalProcessingNanos = new AtomicLong(0);
    private final AtomicLong totalQueueWaitNanos  = new AtomicLong(0);
    private final AtomicInteger peakQueueDepth    = new AtomicInteger(0);

    public RequestQueueProcessor(GameRoomManager roomManager) {
        this.roomManager = roomManager;
        this.taskQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.workerPool = Executors.newFixedThreadPool(WORKER_THREADS, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "QueueWorker-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

        startConsumerWorkers();
    }

    private void startConsumerWorkers() {
        for (int i = 0; i < WORKER_THREADS; i++) {
            workerPool.submit(this::consumerLoop);
        }
    }

    private void consumerLoop() {
        while (isRunning) {
            try {
                // timed poll so the loop notices isRunning=false on shutdown
                RequestTask task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
                if (task == null) continue;
                totalQueueWaitNanos.addAndGet(task.getQueueWaitNanos());
                long startTime = System.nanoTime();
                try {
                    processTask(task);
                } catch (RuntimeException e) {
                    // one bad request must never take a worker down
                    totalFailedTasks.incrementAndGet();
                    System.err.println("[RequestQueueProcessor] Error processing " + task.getMessage().getType()
                            + " from " + task.getSession().getPlayerName() + ": " + e);
                } finally {
                    totalProcessingNanos.addAndGet(System.nanoTime() - startTime);
                    totalProcessedTasks.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Producer side; blocks up to ENQUEUE_TIMEOUT_MS when full. Returns false if rejected. */
    public boolean enqueue(RequestTask task) {
        return enqueue(task, ENQUEUE_TIMEOUT_MS);
    }

    public boolean enqueue(RequestTask task, long timeoutMs) {
        if (!isRunning) return false;
        totalEnqueuedTasks.incrementAndGet();
        boolean offered;
        try {
            offered = taskQueue.offer(task, timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            offered = false;
        }
        if (!offered) {
            totalDroppedTasks.incrementAndGet();
            System.err.println("[RequestQueueProcessor] Queue full for " + timeoutMs + " ms - rejected "
                    + task.getMessage().getType() + " from " + task.getSession().getPlayerName());
        } else {
            peakQueueDepth.accumulateAndGet(taskQueue.size(), Math::max);
        }
        return offered;
    }

    private void processTask(RequestTask task) {
        roomManager.handleClientMessage(task.getSession(), task.getMessage());
    }

    public int getQueueSize()          { return taskQueue.size(); }
    public int getPeakQueueDepth()     { return peakQueueDepth.get(); }
    public long getTotalEnqueued()     { return totalEnqueuedTasks.get(); }
    public long getTotalProcessed()    { return totalProcessedTasks.get(); }
    public long getTotalDropped()      { return totalDroppedTasks.get(); }
    public long getTotalFailed()       { return totalFailedTasks.get(); }

    public double getAverageProcessingTimeMs() {
        long processed = totalProcessedTasks.get();
        return processed == 0 ? 0 : totalProcessingNanos.get() / 1_000_000.0 / processed;
    }

    public double getAverageQueueWaitMs() {
        long processed = totalProcessedTasks.get();
        return processed == 0 ? 0 : totalQueueWaitNanos.get() / 1_000_000.0 / processed;
    }

    /** Blocks until every task enqueued so far has been consumed (used by tests / stats). */
    public boolean awaitDrained(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (totalProcessedTasks.get() + totalDroppedTasks.get() < totalEnqueuedTasks.get()) {
            if (System.currentTimeMillis() > deadline) return false;
            Thread.sleep(5);
        }
        return true;
    }

    public void shutdown() {
        isRunning = false;
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
