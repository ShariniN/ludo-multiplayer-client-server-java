package ludo.server;

import ludo.enums.Color;
import ludo.network.GameMessage;

/**
 * A connected player as seen by the rooms, so the same game logic serves both
 * TCP sockets (ClientHandler) and HTTP sessions (HttpClientSession).
 */
public interface ClientSession {

    /** Unique id for this session (UUID for sockets, token for HTTP). */
    String getClientId();

    /** Pushes a server event to this client (socket write or outbox enqueue). */
    void sendMessage(GameMessage message);

    Color getAssignedColor();
    void setAssignedColor(Color color);

    String getPlayerName();
    void setPlayerName(String playerName);

    GameRoom getRoom();
    void setRoom(GameRoom room);
}
