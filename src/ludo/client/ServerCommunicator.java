package ludo.client;

import ludo.network.GameMessage;
import ludo.network.NetworkProtocol;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Client-side asynchronous network communicator (Tier 1 Network Layer).
 * Maintains a dedicated background thread to read server socket messages
 * and safely dispatches UI state updates onto the Swing Event Dispatch Thread (EDT).
 */
public class ServerCommunicator {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread listenerThread;
    private volatile boolean isConnected = false;

    private Consumer<GameMessage> messageConsumer;
    private Consumer<String> connectionStatusConsumer;

    public ServerCommunicator(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setMessageConsumer(Consumer<GameMessage> consumer) {
        this.messageConsumer = consumer;
    }

    public void setConnectionStatusConsumer(Consumer<String> consumer) {
        this.connectionStatusConsumer = consumer;
    }

    public boolean connect() {
        try {
            this.socket = new Socket(host, port);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            this.isConnected = true;

            startListenerThread();
            notifyStatus("Connected to " + host + ":" + port);
            return true;
        } catch (IOException e) {
            notifyStatus("Connection failed: " + e.getMessage());
            return false;
        }
    }

    private void startListenerThread() {
        this.listenerThread = new Thread(() -> {
            try {
                String line;
                while (isConnected && (line = reader.readLine()) != null) {
                    GameMessage msg = NetworkProtocol.deserialize(line);
                    if (msg != null && messageConsumer != null) {
                        // Hand off to the EDT; Swing is not thread-safe
                        SwingUtilities.invokeLater(() -> {
                            try {
                                messageConsumer.accept(msg);
                            } catch (Exception e) {
                                System.err.println("[ServerCommunicator] Error in UI message handler: " + e.getMessage());
                            }
                        });
                    }
                }
            } catch (IOException e) {
                if (isConnected) {
                    notifyStatus("Disconnected from server.");
                }
            } finally {
                disconnect();
            }
        }, "ClientSocketListener");
        this.listenerThread.setDaemon(true);
        this.listenerThread.start();
    }

    public synchronized void sendMessage(GameMessage message) {
        if (!isConnected || writer == null) return;
        try {
            String serialized = NetworkProtocol.serialize(message);
            writer.println(serialized);
        } catch (Exception e) {
            System.err.println("[ServerCommunicator] Error sending message: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (!isConnected) return;
        isConnected = false;
        // Socket first: unblocks the listener thread's readLine() without deadlocking
        // on the BufferedReader lock it holds while reading.
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (writer != null) writer.close();
        notifyStatus("Disconnected");
    }

    private void notifyStatus(String status) {
        if (connectionStatusConsumer != null) {
            SwingUtilities.invokeLater(() -> connectionStatusConsumer.accept(status));
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}
