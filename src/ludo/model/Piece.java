package ludo.model;

import ludo.enums.Color;
import ludo.enums.Direction;
import ludo.enums.MysteryEffect;
import ludo.enums.PieceState;
import ludo.util.BoardUtils;

/**
 * Represents a game token.
 * Tracks state, position, direction, captures, status effects, and movement math.
 */
public class Piece {

    private final String name;
    private final Color color;

    private PieceState state;
    private int position;
    private Direction direction;
    private Direction originalDirection;

    private int captureCount;

    private MysteryEffect mysteryEffect;
    private int effectRoundsRemaining;

    private boolean passedApproachOnce;

    private int consecutiveThreeCount;

    public Piece(String name, Color color) {
        this.name = name;
        this.color = color;
        resetToBase();
    }

    // Sent back to base: clears position, direction, captures and effects
    public void resetToBase() {
        this.state = PieceState.BASE;
        this.position = BoardUtils.NO_POSITION;
        this.direction = null;
        this.originalDirection = null;
        this.captureCount = 0;
        this.mysteryEffect = MysteryEffect.NONE;
        this.effectRoundsRemaining = 0;
        this.passedApproachOnce = false;
        this.consecutiveThreeCount = 0;
    }

    public String getName()               { return name; }
    public Color getColor()               { return color; }
    public PieceState getState()          { return state; }
    public int getPosition()              { return position; }
    public Direction getDirection()        { return direction; }
    public Direction getOriginalDirection(){ return originalDirection; }
    public int getCaptureCount()          { return captureCount; }
    public MysteryEffect getMysteryEffect() { return mysteryEffect; }
    public int getEffectRoundsRemaining() { return effectRoundsRemaining; }
    public boolean hasPassedApproachOnce(){ return passedApproachOnce; }
    public int getConsecutiveThreeCount() { return consecutiveThreeCount; }

    public void setState(PieceState state)          { this.state = state; }
    public void setPosition(int position)            { this.position = position; }
    public void setDirection(Direction direction)     { this.direction = direction; }
    public void setOriginalDirection(Direction dir)   { this.originalDirection = dir; }
    public void setPassedApproachOnce(boolean passed) { this.passedApproachOnce = passed; }

    public void incrementCaptureCount() { this.captureCount++; }

    public void applyMysteryEffect(MysteryEffect effect, int rounds) {
        this.mysteryEffect = effect;
        this.effectRoundsRemaining = rounds;
    }

    // Effect resets to NONE when its rounds run out
    public void decrementEffectRounds() {
        if (effectRoundsRemaining > 0) {
            effectRoundsRemaining--;
            if (effectRoundsRemaining == 0) {
                mysteryEffect = MysteryEffect.NONE;
            }
        }
    }

    public void clearMysteryEffect() {
        this.mysteryEffect = MysteryEffect.NONE;
        this.effectRoundsRemaining = 0;
    }

    public void incrementConsecutiveThreeCount() { this.consecutiveThreeCount++; }
    public void resetConsecutiveThreeCount()     { this.consecutiveThreeCount = 0; }

    public boolean isAtBase()         { return state == PieceState.BASE; }
    public boolean isActive()         { return state == PieceState.ACTIVE; }
    public boolean isInHomeStraight()  { return state == PieceState.HOME_STRAIGHT; }
    public boolean isAtHome()         { return state == PieceState.HOME; }

    public boolean canEnterHomeStraight() {
        return captureCount >= 1;
    }

    // Dice value adjusted for any active mystery effect
    public int getEffectiveMovement(int diceValue) {
        switch (mysteryEffect) {
            case ENERGIZED: return diceValue * 2;
            case SICK:      return diceValue / 2;
            default:        return diceValue;
        }
    }

    // Pieces at home or locked in BRIEFING cannot move
    public boolean isMovable() {
        if (state == PieceState.HOME) return false;
        if (mysteryEffect == MysteryEffect.BRIEFING) return false;
        return true;
    }

    @Override
    public String toString() {
        return name;
    }
}
