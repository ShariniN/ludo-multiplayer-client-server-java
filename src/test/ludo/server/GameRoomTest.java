package ludo.server;

import ludo.db.JdbcPersistenceGateway;
import ludo.enums.Color;
import ludo.game.Dice;
import ludo.network.GameMessage;
import ludo.network.MessageType;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameRoom use-case tests (no sockets)")
class GameRoomTest {

    private JdbcPersistenceGateway persistence;

    private GameRoomManager manager;
    private GameRoom room;
    private RecordingSession red, green, yellow, blue;

    @BeforeEach
    void setUp() {
        Dice.resetInstance();
        persistence = JdbcPersistenceGateway.inMemory("test-" + UUID.randomUUID());
        manager = new GameRoomManager(persistence);
        room = manager.getOrCreateRoom("TEST-ROOM");
        red = new RecordingSession("red");
        green = new RecordingSession("green");
        yellow = new RecordingSession("yellow");
        blue = new RecordingSession("blue");
    }

    @AfterEach
    void tearDown() {
        try {
            manager.shutdown();
            Dice.resetInstance();
        } finally {
            persistence.close();
        }
    }

    private void joinAll() {
        room.joinPlayer(red, Color.RED, "Red");
        room.joinPlayer(green, Color.GREEN, "Green");
        room.joinPlayer(yellow, Color.YELLOW, "Yellow");
        room.joinPlayer(blue, Color.BLUE, "Blue");
    }

    @Test
    @DisplayName("first join assigns the preferred colour and sends ROOM_JOINED + state")
    void joinAssignsColour() {
        assertTrue(room.joinPlayer(red, Color.RED, "Red"));
        assertEquals(Color.RED, red.getAssignedColor());
        assertSame(room, red.getRoom());
        assertEquals("RED", red.last(MessageType.ROOM_JOINED).get("color"));
        assertTrue(red.has(MessageType.GAME_STATE_UPDATE));
        assertFalse(room.isGameStarted(), "match must not start with one player");
    }

    @Test
    @DisplayName("taken colour falls back to the next free slot")
    void colourConflictFallsBack() {
        room.joinPlayer(red, Color.RED, "A");
        RecordingSession other = new RecordingSession("other");
        room.joinPlayer(other, Color.RED, "B");
        assertEquals(Color.GREEN, other.getAssignedColor());
    }

    @Test
    @DisplayName("fifth player is refused with ROOM_FULL")
    void roomFull() {
        joinAll();
        RecordingSession fifth = new RecordingSession("fifth");
        assertFalse(room.joinPlayer(fifth, null, "Fifth"));
        assertTrue(fifth.has(MessageType.ROOM_FULL));
        assertNull(fifth.getRoom());
    }

    @Test
    @DisplayName("fourth join auto-starts the match and prompts RED to roll")
    void fourthJoinStarts() {
        joinAll();
        assertTrue(room.isGameStarted());
        assertTrue(red.has(MessageType.GAME_START));
        assertEquals("ROLL_DICE", red.last(MessageType.YOUR_TURN).get("state"));
        assertTrue(green.has(MessageType.WAITING_TURN));
        assertFalse(green.has(MessageType.YOUR_TURN));
    }

    @Test
    @DisplayName("rolling out of turn is rejected with ERROR and changes nothing")
    void rollOutOfTurn() {
        joinAll();
        room.handleRollDice(blue);
        assertEquals("Not your turn!", blue.last(MessageType.ERROR).get("message"));
        assertEquals(0, blue.count(MessageType.DICE_ROLLED));
        assertEquals(Color.RED, room.getActivePlayer().getColor());
    }

    @Test
    @DisplayName("rolling or moving before the match starts is answered with ERROR, not silence")
    void actionsBeforeStartRejected() {
        room.joinPlayer(red, Color.RED, "Red");
        room.handleRollDice(red);
        assertEquals("The game has not started yet - waiting for 4 players.", red.last(MessageType.ERROR).get("message"));
        room.handleMovePiece(red, "R1");
        assertEquals(2, red.count(MessageType.ERROR));
        assertEquals(0, red.count(MessageType.DICE_ROLLED));
    }

    @Test
    @DisplayName("after the turn passes, every client's latest state snapshot names the new active player")
    void stateSnapshotFollowsTurn() {
        joinAll();
        Map<Color, RecordingSession> seats = Map.of(Color.RED, red, Color.GREEN, green, Color.YELLOW, yellow, Color.BLUE, blue);
        Color first = room.getActivePlayer().getColor();
        for (int i = 0; i < 50 && room.getActivePlayer().getColor() == first; i++) {
            RecordingSession s = seats.get(first);
            room.handleRollDice(s);
            GameMessage prompt = s.last(MessageType.YOUR_TURN);
            if (room.getActivePlayer().getColor() == first && prompt != null && prompt.get("validPieces") != null) {
                room.handleMovePiece(s, prompt.get("validPieces").split(",")[0]);
            }
        }
        String now = room.getActivePlayer().getColor().name();
        assertNotEquals(first.name(), now, "the turn should have passed");
        for (RecordingSession s : seats.values()) {
            assertEquals(now, s.last(MessageType.GAME_STATE_UPDATE).get("activeColor"), s.getClientId());
        }
    }

    @Test
    @DisplayName("a chat message from one player is broadcast to every player in the room")
    void chatReachesEveryPlayer() {
        joinAll();
        manager.handleClientMessage(red, new GameMessage(MessageType.CHAT_MESSAGE, "Red", "TEST-ROOM")
                .put("message", "good luck everyone"));
        for (RecordingSession s : List.of(red, green, yellow, blue)) {
            GameMessage chat = s.last(MessageType.CHAT_BROADCAST);
            assertNotNull(chat, s.getClientId() + " got no chat");
            assertEquals("good luck everyone", chat.get("message"), s.getClientId());
        }
    }

    @Test
    @DisplayName("moving before rolling is rejected with an explanation")
    void moveBeforeRollRejected() {
        joinAll();
        room.handleMovePiece(red, "R1");
        assertEquals("Roll the dice before moving a piece.", red.last(MessageType.ERROR).get("message"));
    }

    @Test
    @DisplayName("overflow players are seated together in <room>-2 and their match starts")
    void overflowPlayersShareARoom() {
        List<RecordingSession> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            RecordingSession s = new RecordingSession("p" + i);
            eight.add(s);
            manager.handleClientMessage(s, new GameMessage(MessageType.JOIN_ROOM, "p" + i, "BUSY")
                    .put("roomId", "BUSY").put("playerName", "p" + i));
        }
        for (int i = 4; i < 8; i++) {
            assertEquals("BUSY-2", eight.get(i).getRoom().getRoomId(), "player " + i);
        }
        assertTrue(manager.getRoom("BUSY").isGameStarted());
        assertTrue(manager.getRoom("BUSY-2").isGameStarted(), "the overflow room fills up and starts too");
        assertNull(manager.getRoom("BUSY-3"));
    }

    @Test
    @DisplayName("a roll is broadcast to every player, then either a piece prompt or the turn passes")
    void rollBroadcast() {
        joinAll();
        room.handleRollDice(red);
        for (RecordingSession s : new RecordingSession[]{red, green, yellow, blue}) {
            assertEquals(1, s.count(MessageType.DICE_ROLLED), s.getClientId() + " must see the roll");
        }
        int value = red.last(MessageType.DICE_ROLLED).getInt("diceValue", 0);
        assertTrue(value >= 1 && value <= 6);
        if (value == 6) {
            assertEquals("SELECT_PIECE", red.last(MessageType.YOUR_TURN).get("state"));
            assertTrue(room.isWaitingForPieceSelection());
            assertEquals(4, room.getCurrentMovablePieceNames().size(), "all four base pieces may leave on a 6");
        } else {
            assertFalse(room.isWaitingForPieceSelection());
            assertEquals(Color.GREEN, room.getActivePlayer().getColor(), "no movable piece -> turn passes");
        }
    }

    @Test
    @DisplayName("moving a piece that was not offered is rejected")
    void invalidPieceRejected() {
        joinAll();
        rollUntilSix(red);
        room.handleMovePiece(red, "G1");
        assertEquals("Invalid piece selection!", red.last(MessageType.ERROR).get("message"));
        assertTrue(room.isWaitingForPieceSelection(), "still waiting for a valid selection");
    }

    @Test
    @DisplayName("moving from base on a 6 places the piece on the start cell and grants a bonus roll")
    void moveFromBase() {
        joinAll();
        rollUntilSix(red);
        room.handleMovePiece(red, "R1");
        GameMessage moved = red.last(MessageType.PIECE_MOVED);
        assertEquals("BASE", moved.get("from"));
        assertEquals(String.valueOf(Color.RED.getStartPosition()), moved.get("to"));
        assertTrue(room.buildStateMessage().get("pieces").contains("R1:ACTIVE:" + Color.RED.getStartPosition()));
        assertEquals("ROLL_DICE", red.last(MessageType.YOUR_TURN).get("state"), "a six earns another roll");
        assertEquals(Color.RED, room.getActivePlayer().getColor());
    }

    @Test
    @DisplayName("state snapshot lists all 16 pieces with state, position and effect")
    void stateSnapshot() {
        joinAll();
        String pieces = room.buildStateMessage().get("pieces");
        assertEquals(16, pieces.split(";").length);
        assertTrue(pieces.startsWith("R1:BASE:-1:NONE"));
        assertTrue(pieces.contains("B4:BASE:-1:NONE"));
    }

    @Test
    @DisplayName("player leaving during their own turn passes the turn")
    void leaveDuringTurn() {
        joinAll();
        room.removePlayer(red);
        assertEquals(3, room.getPlayerCount());
        assertEquals(Color.GREEN, room.getActivePlayer().getColor());
        assertEquals("ROLL_DICE", green.last(MessageType.YOUR_TURN).get("state"));
    }

    @Test
    @DisplayName("a full bot-driven match finishes, records the result and updates every player's stats")
    void fullMatchPersists() throws Exception {
        joinAll();
        RecordingSession[] all = {red, green, yellow, blue};
        int guard = 0;
        while (!room.isGameOver() && guard++ < 20000) {
            RecordingSession active = all[room.getActivePlayer().getColor().ordinal()];
            if (room.isWaitingForPieceSelection()) {
                room.handleMovePiece(active, room.getCurrentMovablePieceNames().get(0));
            } else {
                room.handleRollDice(active);
            }
        }
        assertTrue(room.isGameOver(), "match should finish within the guard limit");
        assertNotNull(room.getWinnerColor());
        GameMessage over = red.last(MessageType.GAME_OVER);
        assertEquals(room.getWinnerColor().name(), over.get("winnerColor"));
        assertTrue(red.count(MessageType.MYSTERY_EVENT) > 0, "mystery cell must spawn during a full match");

        // persistence happens on the background writer
        manager.shutdown();
        TimeUnit.MILLISECONDS.sleep(100);
        assertEquals(1, manager.getPersistence().getMatchHistory().size());
        assertEquals(4, manager.getPersistence().getLeaderboard().size(), "one row per match player");
        assertTrue(manager.getPersistence().getLeaderboard().stream()
                .anyMatch(p -> p.name().equals("Red") && p.gamesPlayed() == 1));
    }

    private void rollUntilSix(RecordingSession session) {
        // With every piece still in base, only a 6 yields a piece prompt; otherwise the turn passes.
        RecordingSession[] all = {red, green, yellow, blue};
        for (int i = 0; i < 200 && !room.isWaitingForPieceSelection(); i++) {
            RecordingSession active = all[room.getActivePlayer().getColor().ordinal()];
            room.handleRollDice(active);
            if (room.isWaitingForPieceSelection() && active != session) {
                room.handleMovePiece(active, room.getCurrentMovablePieceNames().get(0));
                // that player may get a bonus roll; keep looping until it is `session`'s prompt
                while (room.getActivePlayer().getColor() == active.getAssignedColor() && !room.isGameOver()) {
                    room.handleRollDice(active);
                    if (room.isWaitingForPieceSelection()) {
                        room.handleMovePiece(active, room.getCurrentMovablePieceNames().get(0));
                    }
                }
            }
        }
        assertTrue(room.isWaitingForPieceSelection());
        assertEquals(session.getAssignedColor(), room.getActivePlayer().getColor());
    }
}
