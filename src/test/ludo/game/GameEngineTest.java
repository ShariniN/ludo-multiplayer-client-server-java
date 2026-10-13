package ludo.game;

import ludo.event.ConsoleGameListener;
import ludo.event.GameEventListener;
import ludo.enums.Color;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.enums.Direction;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Integration tests for GameEngine.
 *
 * Tests two concerns:
 *   1. Game output (via ConsoleGameListener) is correct and deterministic.
 *   2. The Observer Pattern is correctly implemented — events are dispatched
 *      to all registered listeners.
 */
@DisplayName("GameEngine Integration Tests")
class GameEngineTest {

    private ByteArrayOutputStream outContent;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        Dice.resetInstance();
        outContent  = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        Dice.resetInstance();
    }

    /** Helper: runs a full game with a ConsoleGameListener and returns stdout. */
    private String runGame(long seed) {
        GameEngine engine = new GameEngine(seed);
        engine.addListener(new ConsoleGameListener());
        engine.runGame();
        return outContent.toString();
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Same seed produces byte-identical output across two runs")
    void deterministic() {
        String output1 = runGame(Dice.DEFAULT_TEST_SEED);

        outContent.reset();
        Dice.resetInstance();

        String output2 = runGame(Dice.DEFAULT_TEST_SEED);
        assertEquals(output1, output2, "Same seed must produce identical output");
    }

    // ── Game termination ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Game always terminates — ends with a winner or MAX_ROUNDS")
    void gameTerminates() {
        String output = runGame(Dice.DEFAULT_TEST_SEED);
        assertFalse(output.isEmpty(), "Game should produce output");
        assertTrue(output.contains("wins!!!") || output.contains("Maximum rounds reached"),
                "Game must end with a winner or the round limit");
    }

    // ── Initial announcements ─────────────────────────────────────────────────

    @Test
    @DisplayName("Initial setup announces all four players")
    void initialSetup() {
        String output = runGame(Dice.DEFAULT_TEST_SEED);
        assertTrue(output.contains("red player has four"));
        assertTrue(output.contains("green player has four"));
        assertTrue(output.contains("yellow player has four"));
        assertTrue(output.contains("blue player has four"));
    }

    // ── Game events ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Game produces at least one capture event")
    void captureEvents() {
        String output = runGame(Dice.DEFAULT_TEST_SEED);
        assertTrue(output.contains("captures"),
                "Seed " + Dice.DEFAULT_TEST_SEED + " should produce captures");
    }

    @Test
    @DisplayName("At least one player wins")
    void atLeastOneWinner() {
        String output = runGame(Dice.DEFAULT_TEST_SEED);
        assertTrue(output.contains("wins!!!"),
                "Seed " + Dice.DEFAULT_TEST_SEED + " should produce at least one winner");
    }

    // ── Different seeds produce different games ───────────────────────────────

    @Test
    @DisplayName("Different seeds produce different game logs")
    void differentSeeds() {
        String outputDefault = runGame(Dice.DEFAULT_TEST_SEED);
        outContent.reset();
        Dice.resetInstance();
        String output99 = runGame(99);
        assertNotEquals(outputDefault, output99,
                "Different seeds must produce different outputs");
    }

    // ── Observer Pattern contract ─────────────────────────────────────────────

    @Test
    @DisplayName("Observer: onPlayerPiecesAnnounced is fired once per player at game start")
    void observerPiecesAnnouncedFiredForEachPlayer() {
        GameEngine engine = new GameEngine(Dice.DEFAULT_TEST_SEED);
        GameEventListener spy = mock(GameEventListener.class);
        engine.addListener(spy);
        engine.runGame();

        // 4 players announced at startup
        verify(spy, times(4)).onPlayerPiecesAnnounced(any(Player.class));
    }

    @Test
    @DisplayName("Observer: multiple listeners both receive onDiceRolled events")
    void multipleListenersEachReceiveEvents() {
        GameEngine engine = new GameEngine(Dice.DEFAULT_TEST_SEED);
        GameEventListener spy1 = mock(GameEventListener.class);
        GameEventListener spy2 = mock(GameEventListener.class);
        engine.addListener(spy1);
        engine.addListener(spy2);
        engine.runGame();

        // Both listeners must receive the same number of dice-rolled events
        // (exact count will vary, but both must receive at least one)
        verify(spy1, atLeastOnce()).onDiceRolled(any(Color.class), anyInt());
        verify(spy2, atLeastOnce()).onDiceRolled(any(Color.class), anyInt());
    }

    @Test
    @DisplayName("Observer: onPlayerWon is fired for every winner")
    void observerWinnerFired() {
        GameEngine engine = new GameEngine(Dice.DEFAULT_TEST_SEED);
        GameEventListener spy = mock(GameEventListener.class);
        engine.addListener(spy);
        engine.runGame();

        // At least one player must have won
        verify(spy, atLeastOnce()).onPlayerWon(any(Color.class));
    }

    @Test
    @DisplayName("Observer: game without listener runs silently — no exception")
    void noListenerRunsSilently() {
        Dice.resetInstance();
        GameEngine engine = new GameEngine(Dice.DEFAULT_TEST_SEED);
        // No listener added — should not throw
        assertDoesNotThrow(engine::runGame);
    }
}
