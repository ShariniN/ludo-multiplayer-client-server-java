package ludo.network;

/** All message types in the client-server protocol. */
public enum MessageType {
    // Client -> Server connection & room actions
    CONNECT,
    JOIN_ROOM,
    LEAVE_ROOM,

    // In-game client actions
    ROLL_DICE,
    MOVE_PIECE,
    CHAT_MESSAGE,
    LEADERBOARD_REQUEST,

    // Server -> Client broadcasts and notifications
    CONNECTED_ACK,
    ROOM_JOINED,
    ROOM_FULL,
    GAME_START,
    GAME_STATE_UPDATE,
    DICE_ROLLED,
    PIECE_MOVED,
    YOUR_TURN,
    WAITING_TURN,
    CAPTURE_EVENT,
    MYSTERY_EVENT,
    ROUND_END,
    GAME_OVER,
    CHAT_BROADCAST,
    LEADERBOARD_DATA,
    ERROR,
    HEARTBEAT
}
