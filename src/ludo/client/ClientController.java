package ludo.client;

import ludo.enums.Color;
import ludo.network.GameMessage;
import ludo.network.MessageType;

import java.util.Arrays;
import java.util.List;

/**
 * Tier 1 controller (GRASP Controller): translates GUI gestures into protocol
 * messages and server events into view updates. It holds no game rules — the
 * server is authoritative — only the little session state the view needs.
 *
 * handleServerMessage() always runs on the Swing EDT because ServerCommunicator
 * marshals every incoming message with SwingUtilities.invokeLater().
 */
public class ClientController {

    private final ServerCommunicator communicator;
    private LudoGameFrame view;

    private String playerName;
    private Color assignedColor;
    private String currentRoomId = "ROOM-1";

    public ClientController(ServerCommunicator communicator) {
        this.communicator = communicator;
        this.communicator.setMessageConsumer(this::handleServerMessage);
        this.communicator.setConnectionStatusConsumer(status -> {
            if (view != null) view.setConnectionStatus(status);
        });
    }

    public void setView(LudoGameFrame view) {
        this.view = view;
    }

    // Outgoing requests

    public void joinRoom(String roomId, String name, Color prefColor) {
        this.playerName = name;
        this.currentRoomId = roomId;

        GameMessage msg = new GameMessage(MessageType.JOIN_ROOM, playerName, roomId)
                .put("roomId", roomId)
                .put("playerName", playerName);

        if (prefColor != null) {
            msg.put("preferredColor", prefColor.name());
        }
        communicator.sendMessage(msg);
    }

    public void requestRollDice() {
        communicator.sendMessage(new GameMessage(MessageType.ROLL_DICE, playerName, currentRoomId));
    }

    public void requestMovePiece(String pieceName) {
        communicator.sendMessage(new GameMessage(MessageType.MOVE_PIECE, playerName, currentRoomId)
                .put("piece", pieceName));
        if (view != null) {
            view.clearMovablePieces();
        }
    }

    public void sendChat(String text) {
        if (text == null || text.trim().isEmpty()) return;
        communicator.sendMessage(new GameMessage(MessageType.CHAT_MESSAGE, playerName, currentRoomId)
                .put("message", text.trim()));
    }

    public void requestLeaderboard() {
        communicator.sendMessage(new GameMessage(MessageType.LEADERBOARD_REQUEST, playerName, currentRoomId));
    }

    public void disconnect() {
        communicator.disconnect();
    }

    // Incoming events (EDT)

    private void handleServerMessage(GameMessage msg) {
        if (msg == null || view == null) return;

        switch (msg.getType()) {
            case CONNECTED_ACK:
                view.appendActivityLog("Server acknowledged connection (session " + shortId(msg.get("clientId")) + ")");
                break;

            case ROOM_JOINED:
                String colorStr = msg.get("color");
                if (colorStr != null) {
                    this.assignedColor = Color.valueOf(colorStr);
                    view.setAssignedPlayerInfo(assignedColor, msg.get("playerName"), msg.getRoomId());
                    view.appendActivityLog("Joined " + msg.getRoomId() + " as " + assignedColor.getDisplayName() + " (" + msg.get("playerName") + ")");
                }
                break;

            case ROOM_FULL:
                view.appendActivityLog("Room " + msg.getRoomId() + " is full - server is placing you in another room");
                break;

            case GAME_START:
                view.appendActivityLog("Match started! First turn: " + msg.get("activeColor"));
                view.setMatchStarted(true);
                break;

            case GAME_STATE_UPDATE:
                view.updateGameState(
                        msg.getInt("round", 1),
                        msg.get("activeColor"),
                        msg.get("pieces"),
                        msg.getInt("mysteryPos", -1),
                        msg.getBoolean("mysteryActive", false),
                        msg.getInt("players", 0));
                break;

            case DICE_ROLLED:
                String roller = msg.get("color");
                int val = msg.getInt("diceValue", 1);
                view.animateDiceRoll(val, roller);
                view.appendActivityLog(roller + " rolled a " + val);
                break;

            case YOUR_TURN:
                String state = msg.get("state");
                if ("ROLL_DICE".equalsIgnoreCase(state)) {
                    view.enableDiceRoll(true);
                    view.clearMovablePieces();
                    view.setTurnStatus("Your turn - click ROLL DICE", true);
                } else if ("SELECT_PIECE".equalsIgnoreCase(state)) {
                    view.enableDiceRoll(false);
                    String valid = msg.get("validPieces");
                    if (valid != null && !valid.isEmpty()) {
                        List<String> list = Arrays.asList(valid.split(","));
                        view.setMovablePieces(list);
                        view.setTurnStatus("Rolled " + msg.getInt("diceValue", 0) + " - click a highlighted piece", true);
                    }
                }
                break;

            case WAITING_TURN:
                view.enableDiceRoll(false);
                view.clearMovablePieces();
                view.setTurnStatus("Waiting for " + msg.get("activeColor") + "...", false);
                break;

            case PIECE_MOVED:
                String dir = msg.get("direction");
                view.appendActivityLog("Move: " + msg.get("piece") + " " + msg.get("from") + " -> " + msg.get("to")
                        + (dir != null && !dir.isEmpty() ? " (" + dir + ")" : ""));
                break;

            case CAPTURE_EVENT:
                view.appendActivityLog("CAPTURE! " + msg.get("attacker") + " captured " + msg.get("captured") + " at " + msg.get("location"));
                view.flashBoard();
                break;

            case MYSTERY_EVENT:
                String sub = msg.get("subType");
                if ("SPAWNED".equals(sub)) {
                    view.appendActivityLog("Mystery cell appeared at cell " + msg.get("position"));
                } else if ("LANDED".equals(sub)) {
                    view.appendActivityLog("Mystery cell! " + msg.get("piece") + " teleported to " + msg.get("destination"));
                } else if ("EFFECT".equals(sub)) {
                    view.appendActivityLog("Mystery effect on " + msg.get("piece") + ": " + msg.get("effect"));
                }
                break;

            case ROUND_END:
                view.appendActivityLog("--- Round " + msg.get("round") + " ---");
                break;

            case CHAT_BROADCAST:
                view.appendChat(msg.get("sender"), msg.get("message"));
                if (msg.get("members") != null) view.updateLobby(msg.get("members"));
                break;

            case LEADERBOARD_DATA:
                view.showLeaderboard(msg.get("source"), msg.get("rows"), msg.get("history"));
                break;

            case GAME_OVER:
                view.enableDiceRoll(false);
                view.clearMovablePieces();
                view.showGameOver(msg.get("winnerColor"), msg.get("winnerName"), msg.getInt("rounds", 0));
                view.appendActivityLog("GAME OVER - winner: " + msg.get("winnerName") + " (" + msg.get("winnerColor") + ")");
                break;

            case ERROR:
                view.appendActivityLog("Server: " + msg.get("message"));
                break;

            case HEARTBEAT:
            default:
                break;
        }
    }

    private static String shortId(String id) {
        return id == null ? "?" : id.substring(0, Math.min(8, id.length()));
    }

    public Color getAssignedColor() {
        return assignedColor;
    }

    public String getPlayerName() {
        return playerName;
    }
}
