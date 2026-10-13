package ludo.util;

import ludo.enums.Color;
import ludo.enums.Direction;

/** Static board constants and modular track math. */
public final class BoardUtils {

    public static final int PATH_SIZE = 52;

    public static final int HOME_STRAIGHT_SIZE = 5;

    public static final int ALPHA_CELL = 7;
    public static final int BETA_CELL  = 25;
    public static final int GAMMA_CELL = 44;

    public static final int MAX_ROUNDS = 1000;

    // Game configuration
    public static final int DICE_FACES            = 6;  // Faces on the dice; roll range is [1..DICE_FACES]
    public static final int PLAYERS_COUNT         = 4;  // Number of players in the game
    public static final int PIECES_PER_PLAYER     = 4;  // Tokens each player controls

    // Mystery cell rules
    public static final int MYSTERY_DURATION      = 4;  // Rounds a mystery cell status effect lasts
    public static final int TELEPORT_OPTIONS      = 6;  // Number of distinct teleport outcomes
    public static final int MYSTERY_SPAWN_DELAY   = 2;  // Rounds after first piece on board before MC spawns

    // Consecutive-move rule thresholds
    public static final int MAX_CONSECUTIVE_SIXES = 3;  // Three consecutive sixes triggers a penalty
    public static final int BRIEFING_ESCAPE_THREES = 2; // Consecutive 3-rolls needed to escape BRIEFING

    // Path math
    public static final int HOME_STRAIGHT_ENTRY_DISTANCE = 6; // Steps from approach cell to reach HOME

    // Sentinel values
    public static final int NO_POSITION  = -1; // Piece is not on the standard or home-straight path
    public static final int UNSET_ROUND  = -1; // firstPieceOnBoardRound before any piece is placed

    // Block rule
    public static final int MIN_BLOCK_SIZE = 2; // Minimum same-colour pieces on one cell to form a blockade

    private BoardUtils() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    public static int clockwiseDistance(int from, int to) {
        return (to - from + PATH_SIZE) % PATH_SIZE;
    }

    public static int counterclockwiseDistance(int from, int to) {
        return (from - to + PATH_SIZE) % PATH_SIZE;
    }

    public static int moveClockwise(int from, int steps) {
        return (from + steps) % PATH_SIZE;
    }

    public static int moveCounterclockwise(int from, int steps) {
        // Adds enough laps of PATH_SIZE to keep the result non-negative
        return (from - steps + PATH_SIZE * ((steps / PATH_SIZE) + 1)) % PATH_SIZE;
    }

    // Steps from position to the colour's approach cell in the given direction
    public static int distanceToApproach(int position, Color color, Direction direction) {
        int approach = color.getApproachPosition();
        if (direction == Direction.CLOCKWISE) {
            return clockwiseDistance(position, approach);
        } else {
            return counterclockwiseDistance(position, approach);
        }
    }

    // Total steps to HOME; counter-clockwise pieces must loop the board once before entering
    public static int distanceToHome(int position, Color color, Direction direction,
                                     boolean passedApproachOnce) {
        int distToApproach = distanceToApproach(position, color, direction);

        if (direction == Direction.CLOCKWISE) {
            return distToApproach + HOME_STRAIGHT_ENTRY_DISTANCE;
        } else {
            if (passedApproachOnce) {
                return distToApproach + HOME_STRAIGHT_ENTRY_DISTANCE;
            } else {
                int firstLeg = (distToApproach == 0) ? PATH_SIZE : distToApproach;
                return firstLeg + PATH_SIZE + HOME_STRAIGHT_ENTRY_DISTANCE;
            }
        }
    }

    public static String standardCellName(int position) {
        return String.valueOf(position);
    }

    public static String homeStraightCellName(Color color, int position) {
        return color.getDisplayName() + " homepath[" + position + "]";
    }

    public static String locationName(ludo.enums.PieceState state, int position, Color color) {
        switch (state) {
            case ACTIVE:        return standardCellName(position);
            case HOME_STRAIGHT: return homeStraightCellName(color, position);
            case BASE:          return "Base";
            case HOME:          return "Home";
            default:            return "Unknown";
        }
    }
}
