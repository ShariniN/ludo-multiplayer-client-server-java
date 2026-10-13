package ludo.server;

import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import ludo.network.NetworkProtocol;

import java.io.*;
import java.net.Socket;

/**
 * One thread per TCP client: reads requests and enqueues them (producer), and
 * sends server events back as a ClientSession.
 */
public final class ClientHandler implements Runnable, ClientSession {

    private final Socket socket;
    private final String clientId;
    private final RequestQueueProcessor queueProcessor;
    private final GameRoomManager roomManager;

    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean isRunning = true;

    private Color assignedColor;
    private String playerName;
    private GameRoom currentRoom;

    public ClientHandler(Socket socket, String clientId, RequestQueueProcessor queueProcessor, GameRoomManager roomManager) {
        this.socket = socket;
        this.clientId = clientId;
        this.queueProcessor = queueProcessor;
        this.roomManager = roomManager;
        this.playerName = "Player-" + clientId.substring(0, Math.min(clientId.length(), 6));

        try {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        } catch (IOException e) {
            System.err.println("[ClientHandler] Error creating I/O streams: " + e.getMessage());
            close();
        }
    }

    @Override
    public void run() {
        System.out.println("[ClientHandler] Client connected: " + clientId + " from " + socket.getRemoteSocketAddress());
        sendMessage(new GameMessage(MessageType.CONNECTED_ACK, "SERVER", "LOBBY")
                .put("clientId", clientId)
                .put("serverTime", System.currentTimeMillis()));

        try {
            String line;
            while (isRunning && (line = reader.readLine()) != null) {
                GameMessage msg = NetworkProtocol.deserialize(line);
                if (msg != null) {
                    RequestTask task = new RequestTask(this, msg);
                    boolean enqueued = queueProcessor.enqueue(task);
                    if (!enqueued) {
                        sendMessage(new GameMessage(MessageType.ERROR, "SERVER", "LOBBY")
                                .put("message", "Server request queue is overloaded. Please retry."));
                    }
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                System.out.println("[ClientHandler] Connection closed by client: " + clientId);
            }
        } finally {
            close();
        }
    }

    // synchronized: several QueueWorkers may broadcast to this client at once
    @Override
    public synchronized void sendMessage(GameMessage message) {
        if (!isRunning || writer == null) return;
        try {
            String serialized = NetworkProtocol.serialize(message);
            writer.println(serialized);
        } catch (Exception e) {
            System.err.println("[ClientHandler] Error sending message to " + clientId + ": " + e.getMessage());
        }
    }

    public void close() {
        isRunning = false;
        if (currentRoom != null) {
            currentRoom.removePlayer(this);
        }
        roomManager.removeClient(this);

        // Close the socket, not the reader: closing the reader mid-readLine() from another thread deadlocks
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (writer != null) writer.close();
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public Color getAssignedColor() {
        return assignedColor;
    }

    @Override
    public void setAssignedColor(Color assignedColor) {
        this.assignedColor = assignedColor;
    }

    @Override
    public String getPlayerName() {
        return playerName;
    }

    @Override
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    @Override
    public GameRoom getRoom() {
        return currentRoom;
    }

    @Override
    public void setRoom(GameRoom currentRoom) {
        this.currentRoom = currentRoom;
    }
}
