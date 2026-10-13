package ludo.model;

import ludo.enums.*;
import ludo.util.BoardUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Piece, grouped by concern using @Nested.
 *
 * Tests cover: initial state, state transitions, movement calculation,
 * mystery effect lifecycle, and approach-pass tracking.
 */
@DisplayName("Piece Tests")
class PieceTest {

    private Piece piece;

    @BeforeEach
    void setUp() {
        piece = new Piece("R1", Color.RED);
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("New piece starts at BASE with sentinel position")
        void constructionState() {
            assertEquals(PieceState.BASE, piece.getState());
            assertEquals(BoardUtils.NO_POSITION, piece.getPosition());
            assertNull(piece.getDirection());
            assertEquals("R1", piece.getName());
            assertEquals(Color.RED, piece.getColor());
        }

        @Test
        @DisplayName("New piece has zero capture count")
        void constructionCaptureCount() {
            assertEquals(0, piece.getCaptureCount());
        }

        @Test
        @DisplayName("New piece has NONE mystery effect with 0 rounds remaining")
        void constructionMysteryEffect() {
            assertEquals(MysteryEffect.NONE, piece.getMysteryEffect());
            assertEquals(0, piece.getEffectRoundsRemaining());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("State Helpers")
    class StateHelperTests {

        @Test
        @DisplayName("isAtBase is true only in BASE state")
        void isAtBase() {
            assertTrue(piece.isAtBase());
            piece.setState(PieceState.ACTIVE);
            assertFalse(piece.isAtBase());
        }

        @Test
        @DisplayName("isActive is true only in ACTIVE state")
        void isActive() {
            assertFalse(piece.isActive());
            piece.setState(PieceState.ACTIVE);
            assertTrue(piece.isActive());
        }

        @Test
        @DisplayName("isInHomeStraight is true only in HOME_STRAIGHT state")
        void isInHomeStraight() {
            assertFalse(piece.isInHomeStraight());
            piece.setState(PieceState.HOME_STRAIGHT);
            assertTrue(piece.isInHomeStraight());
        }

        @Test
        @DisplayName("isAtHome is true only in HOME state")
        void isAtHome() {
            assertFalse(piece.isAtHome());
            piece.setState(PieceState.HOME);
            assertTrue(piece.isAtHome());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Movability")
    class MovabilityTests {

        @Test
        @DisplayName("BASE piece is movable (dice=6 requirement enforced elsewhere)")
        void movableAtBase() {
            assertTrue(piece.isMovable());
        }

        @Test
        @DisplayName("ACTIVE piece is movable")
        void movableWhenActive() {
            piece.setState(PieceState.ACTIVE);
            assertTrue(piece.isMovable());
        }

        @Test
        @DisplayName("HOME piece is NOT movable")
        void notMovableAtHome() {
            piece.setState(PieceState.HOME);
            assertFalse(piece.isMovable());
        }

        @Test
        @DisplayName("BRIEFING piece is NOT movable despite being ACTIVE")
        void notMovableInBriefing() {
            piece.setState(PieceState.ACTIVE);
            piece.applyMysteryEffect(MysteryEffect.BRIEFING, BoardUtils.MYSTERY_DURATION);
            assertFalse(piece.isMovable());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Home Straight Entry Condition")
    class HomeStraightEntryTests {

        @Test
        @DisplayName("Cannot enter home straight with 0 captures")
        void cannotEnterWith0Captures() {
            assertFalse(piece.canEnterHomeStraight());
        }

        @Test
        @DisplayName("Can enter home straight with exactly 1 capture")
        void canEnterWith1Capture() {
            piece.incrementCaptureCount();
            assertTrue(piece.canEnterHomeStraight());
        }

        @Test
        @DisplayName("Can enter home straight with multiple captures")
        void canEnterWithMultipleCaptures() {
            piece.incrementCaptureCount();
            piece.incrementCaptureCount();
            assertTrue(piece.canEnterHomeStraight());
            assertEquals(2, piece.getCaptureCount());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Effective Movement")
    class EffectiveMovementTests {

        @Test
        @DisplayName("NONE effect: dice value passes through unchanged")
        void effectiveMovementNone() {
            assertEquals(4, piece.getEffectiveMovement(4));
            assertEquals(6, piece.getEffectiveMovement(6));
        }

        @Test
        @DisplayName("ENERGIZED effect: doubles the dice value")
        void effectiveMovementEnergized() {
            piece.applyMysteryEffect(MysteryEffect.ENERGIZED, BoardUtils.MYSTERY_DURATION);
            assertEquals(8,  piece.getEffectiveMovement(4));
            assertEquals(12, piece.getEffectiveMovement(6));
        }

        @Test
        @DisplayName("SICK effect: halves the dice value (floor division)")
        void effectiveMovementSick() {
            piece.applyMysteryEffect(MysteryEffect.SICK, BoardUtils.MYSTERY_DURATION);
            assertEquals(2, piece.getEffectiveMovement(4));
            assertEquals(2, piece.getEffectiveMovement(5)); // floor(5/2) = 2
        }

        @Test
        @DisplayName("SICK effect: dice=1 yields 0 effective movement")
        void effectiveMovementSickDice1() {
            piece.applyMysteryEffect(MysteryEffect.SICK, BoardUtils.MYSTERY_DURATION);
            assertEquals(0, piece.getEffectiveMovement(1));
        }

        @Test
        @DisplayName("BRIEFING effect: dice value unchanged (piece is immobile)")
        void effectiveMovementBriefing() {
            piece.applyMysteryEffect(MysteryEffect.BRIEFING, BoardUtils.MYSTERY_DURATION);
            assertEquals(4, piece.getEffectiveMovement(4));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Mystery Effect Lifecycle")
    class MysteryEffectLifecycleTests {

        @Test
        @DisplayName("applyMysteryEffect sets effect and round counter")
        void applyMysteryEffect() {
            piece.applyMysteryEffect(MysteryEffect.ENERGIZED, BoardUtils.MYSTERY_DURATION);
            assertEquals(MysteryEffect.ENERGIZED, piece.getMysteryEffect());
            assertEquals(BoardUtils.MYSTERY_DURATION, piece.getEffectRoundsRemaining());
        }

        @Test
        @DisplayName("decrementEffectRounds counts down; clears effect at 0")
        void decrementEffectRoundsLifecycle() {
            piece.applyMysteryEffect(MysteryEffect.SICK, 2);

            piece.decrementEffectRounds();
            assertEquals(1, piece.getEffectRoundsRemaining());
            assertEquals(MysteryEffect.SICK, piece.getMysteryEffect());

            piece.decrementEffectRounds();
            assertEquals(0, piece.getEffectRoundsRemaining());
            assertEquals(MysteryEffect.NONE, piece.getMysteryEffect());
        }

        @Test
        @DisplayName("decrementEffectRounds is a no-op when already at 0")
        void decrementWhenAlreadyZero() {
            piece.decrementEffectRounds(); // should not throw
            assertEquals(0, piece.getEffectRoundsRemaining());
            assertEquals(MysteryEffect.NONE, piece.getMysteryEffect());
        }

        @Test
        @DisplayName("clearMysteryEffect resets immediately regardless of remaining rounds")
        void clearMysteryEffect() {
            piece.applyMysteryEffect(MysteryEffect.ENERGIZED, BoardUtils.MYSTERY_DURATION);
            piece.clearMysteryEffect();
            assertEquals(MysteryEffect.NONE, piece.getMysteryEffect());
            assertEquals(0, piece.getEffectRoundsRemaining());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Consecutive-Three Briefing Escape Tracking")
    class BriefingEscapeTrackingTests {

        @Test
        @DisplayName("consecutiveThreeCount increments and resets correctly")
        void consecutiveThreeTracking() {
            piece.incrementConsecutiveThreeCount();
            assertEquals(1, piece.getConsecutiveThreeCount());
            piece.incrementConsecutiveThreeCount();
            assertEquals(2, piece.getConsecutiveThreeCount());
            piece.resetConsecutiveThreeCount();
            assertEquals(0, piece.getConsecutiveThreeCount());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Reset")
    class ResetTests {

        @Test
        @DisplayName("resetToBase clears ALL mutable fields back to initial values")
        void resetToBaseClearsAll() {
            // Arrange — mutate every field
            piece.setState(PieceState.ACTIVE);
            piece.setPosition(25);
            piece.setDirection(Direction.CLOCKWISE);
            piece.setOriginalDirection(Direction.COUNTERCLOCKWISE);
            piece.incrementCaptureCount();
            piece.applyMysteryEffect(MysteryEffect.ENERGIZED, BoardUtils.MYSTERY_DURATION);
            piece.setPassedApproachOnce(true);
            piece.incrementConsecutiveThreeCount();

            // Act
            piece.resetToBase();

            // Assert — every field returns to its initial value
            assertEquals(PieceState.BASE, piece.getState());
            assertEquals(BoardUtils.NO_POSITION, piece.getPosition());
            assertNull(piece.getDirection());
            assertNull(piece.getOriginalDirection());
            assertEquals(0, piece.getCaptureCount());
            assertEquals(MysteryEffect.NONE, piece.getMysteryEffect());
            assertEquals(0, piece.getEffectRoundsRemaining());
            assertFalse(piece.hasPassedApproachOnce());
            assertEquals(0, piece.getConsecutiveThreeCount());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString returns the piece name")
        void toStringReturnName() {
            assertEquals("R1", piece.toString());
        }
    }
}
