package ludo.stress;

import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.network.NetworkProtocol;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Headless automated test client (brief step 4): a thin client with no GUI that
 * plays every turn the instant it is prompted and can additionally be driven to
 * send bursts of asynchronous requests. Several run at once against one server.
 */
public class AutomatedTestClient implements Runnable {

    private final String host;
    private final int port;
    private final String botName;
    private final Color preferredColor;
    private final String roomId;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final AtomicLong requestsSent = new AtomicLong(0);
    private final AtomicLong responsesReceived = new AtomicLong(0);
    private final AtomicLong heartbeatSamples = new AtomicLong(0);
    private final AtomicLong heartbeatRoundTripNanos = new AtomicLong(0);
    private final AtomicBoolean gameOver = new AtomicBoolean(false);
    private volatile Color assignedColor;

    public AutomatedTestClient(String host, int port, String botName, Color preferredColor, String roomId) {
        this.host = host;
        this.port = port;
        this.botName = botName;
        this.preferredColor = preferredColor;
        this.roomId = roomId;
    }

    public boolean connect() {
        try {
            this.socket = new Socket(host, port);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            this.isRunning.set(true);

            GameMessage joinMsg = new GameMessage(MessageType.JOIN_ROOM, botName, roomId)
                    .put("roomId", roomId)
                    .put("playerName", botName);
            if (preferredColor != null) {
                joinMsg.put("preferredColor", preferredColor.name());
            }
            send(joinMsg);

            return true;
        } catch (IOException e) {
            System.err.println("[AutomatedBot:" + botName + "] Connect failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void run() {
        try {
            String line;
            while (isRunning.get() && (line = reader.readLine()) != null) {
                responsesReceived.incrementAndGet();
                GameMessage msg = NetworkProtocol.deserialize(line);
                if (msg != null) {
                    handleServerMessage(msg);
                }
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                System.out.println("[AutomatedBot:" + botName + "] Connection closed.");
            }
        } finally {
            close();
        }
    }

    private void handleServerMessage(GameMessage msg) {
        MessageType type = msg.getType();

        switch (type) {
            case ROOM_JOINED:
                String c = msg.get("color");
                if (c != null) this.assignedColor = Color.valueOf(c);
                break;

            case YOUR_TURN:
                String state = msg.get("state");
                if ("ROLL_DICE".equalsIgnoreCase(state)) {
                    send(new GameMessage(MessageType.ROLL_DICE, botName, roomId));
                } else if ("SELECT_PIECE".equalsIgnoreCase(state)) {
                    String valid = msg.get("validPieces");
                    if (valid != null && !valid.isEmpty()) {
                        String[] pieces = valid.split(",");
                        String chosen = pieces[0]; // always pick the first legal piece
                        send(new GameMessage(MessageType.MOVE_PIECE, botName, roomId)
                                .put("piece", chosen));
                    }
                }
                break;

            case HEARTBEAT: {
                // ack carries our send timestamp back: measure queue + network round trip
                long sentAt = msg.getLong("sentNanos", 0);
                if (sentAt > 0) {
                    heartbeatSamples.incrementAndGet();
                    heartbeatRoundTripNanos.addAndGet(System.nanoTime() - sentAt);
                }
                break;
            }

            case GAME_OVER:
                gameOver.set(true);
                break;

            default:
                break;
        }
    }

    public synchronized void send(GameMessage msg) {
        if (!isRunning.get() || writer == null) return;
        if (msg.getType() == MessageType.HEARTBEAT) msg.put("sentNanos", System.nanoTime());
        requestsSent.incrementAndGet();
        String serialized = NetworkProtocol.serialize(msg);
        writer.println(serialized);
    }

    /**
     * Closes the socket first to unblock the reader thread in readLine(); closing the
     * BufferedReader mid-read from another thread would deadlock on its lock.
     */
    public void close() {
        isRunning.set(false);
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (writer != null) writer.close();
    }

    public long getRequestsSent() {
        return requestsSent.get();
    }

    public long getResponsesReceived() {
        return responsesReceived.get();
    }

    public long getHeartbeatSamples() {
        return heartbeatSamples.get();
    }

    public long getHeartbeatRoundTripNanos() {
        return heartbeatRoundTripNanos.get();
    }

    public boolean isGameOver() {
        return gameOver.get();
    }

    public Color getAssignedColor() {
        return assignedColor;
    }

    public String getBotName() {
        return botName;
    }
}
