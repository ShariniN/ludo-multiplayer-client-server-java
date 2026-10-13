package ludo.game;

import ludo.model.Board;
import ludo.enums.Color;
import ludo.model.Piece;
import ludo.util.BoardUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Random;
import java.util.ArrayList;

/**
 * Unit tests for MysteryCell.
 *
 * Uses a mocked Random to drive deterministic outcomes and verify
 * spawn logic, round countdown, respawn, and teleport rolling.
 */
@DisplayName("MysteryCell Tests")
class MysteryCellTest {

    private Random mockRandom;
    private MysteryCell mysteryCell;
    private Board board;

    @BeforeEach
    void setUp() {
        mockRandom  = mock(Random.class);
        mysteryCell = new MysteryCell(mockRandom);
        board       = new Board();
    }

    // ── Initial State ────────────────────────────────────────────────────────

    @Test
    @DisplayName("MysteryCell starts inactive with sentinel position")
    void initiallyInactive() {
        assertFalse(mysteryCell.isActive());
        assertEquals(BoardUtils.NO_POSITION, mysteryCell.getPosition());
    }

    // ── Spawn ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("spawn places cell on a valid standard cell")
    void spawnPlacesCell() {
        when(mockRandom.nextInt(BoardUtils.PATH_SIZE)).thenReturn(10);
        assertTrue(mysteryCell.spawn(board));
        assertTrue(mysteryCell.isActive());
        assertEquals(10, mysteryCell.getPosition());
        assertEquals(BoardUtils.MYSTERY_DURATION, mysteryCell.getRoundsRemaining());
    }

    @Test
    @DisplayName("spawn on occupied board returns false and stays inactive")
    void spawnOnFullBoardFails() {
        Board fullBoard = new Board();
        for (int i = 0; i < BoardUtils.PATH_SIZE; i++) {
            fullBoard.placePieceOnStandard(new Piece("X" + i, Color.RED), i);
        }
        assertFalse(mysteryCell.spawn(fullBoard));
        assertFalse(mysteryCell.isActive());
    }

    // ── Round countdown ───────────────────────────────────────────────────────

    @Test
    @DisplayName("onRoundEnd decrements rounds remaining by 1")
    void onRoundEndDecrements() {
        when(mockRandom.nextInt(BoardUtils.PATH_SIZE)).thenReturn(10);
        mysteryCell.spawn(board);

        mysteryCell.onRoundEnd(board);
        assertEquals(BoardUtils.MYSTERY_DURATION - 1, mysteryCell.getRoundsRemaining());
    }

    @Test
    @DisplayName("onRoundEnd on inactive cell is a no-op")
    void onRoundEndInactive() {
        mysteryCell.onRoundEnd(board);
        assertFalse(mysteryCell.isActive());
    }

    @Test
    @DisplayName("After MYSTERY_DURATION rounds, cell respawns at a different position")
    void onRoundEndRespawnsAfterDuration() {
        when(mockRandom.nextInt(BoardUtils.PATH_SIZE)).thenReturn(10);
        mysteryCell.spawn(board);

        // Drain all MYSTERY_DURATION rounds
        when(mockRandom.nextInt(BoardUtils.PATH_SIZE - 1)).thenReturn(20); // re-spawn seed
        for (int i = 0; i < BoardUtils.MYSTERY_DURATION; i++) {
            mysteryCell.onRoundEnd(board);
        }

        assertTrue(mysteryCell.isActive());
        assertEquals(BoardUtils.MYSTERY_DURATION, mysteryCell.getRoundsRemaining());
        assertNotEquals(10, mysteryCell.getPosition(),
                "Respawned cell must not be at the previous position");
    }

    // ── Teleport rolling ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "rollTeleportOption returns {0} when Random returns {0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    @DisplayName("rollTeleportOption covers all 6 outcomes")
    void rollTeleportOptionAllValues(int rawValue) {
        when(mockRandom.nextInt(BoardUtils.TELEPORT_OPTIONS)).thenReturn(rawValue);
        assertEquals(rawValue, mysteryCell.rollTeleportOption());
    }

    @Test
    @DisplayName("Teleport constants match assignment spec")
    void teleportConstantsMatchSpec() {
        assertEquals(0, MysteryCell.TELEPORT_ALPHA,    "ALPHA must be 0");
        assertEquals(1, MysteryCell.TELEPORT_BETA,     "BETA must be 1");
        assertEquals(2, MysteryCell.TELEPORT_GAMMA,    "GAMMA must be 2");
        assertEquals(3, MysteryCell.TELEPORT_BASE,     "BASE must be 3");
        assertEquals(4, MysteryCell.TELEPORT_X,        "X must be 4");
        assertEquals(5, MysteryCell.TELEPORT_APPROACH, "APPROACH must be 5");
    }

    // ── Alpha energy roll ─────────────────────────────────────────────────────

    @Test
    @DisplayName("rollAlphaEnergized returns true when random is true")
    void alphaEnergized() {
        when(mockRandom.nextBoolean()).thenReturn(true);
        assertTrue(mysteryCell.rollAlphaEnergized());
    }

    @Test
    @DisplayName("rollAlphaEnergized returns false when random is false")
    void alphaSick() {
        when(mockRandom.nextBoolean()).thenReturn(false);
        assertFalse(mysteryCell.rollAlphaEnergized());
    }
}
