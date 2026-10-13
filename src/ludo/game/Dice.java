package ludo.game;

import ludo.util.BoardUtils;
import java.util.Random;

/**
 * Singleton representing the physical game dice.
 * Thread-safe implementation with testing hooks to reset seed.
 */
public class Dice {

    /** Predefined seed for reproducible unit tests — defined here so all test classes share one source of truth. */
    public static final long DEFAULT_TEST_SEED = 42L;

    private static Dice instance;
    private final Random random;

    private Dice(Random random) {
        this.random = random;
    }

    public static synchronized Dice getInstance(Random random) {
        if (instance == null) {
            instance = new Dice(random);
        }
        return instance;
    }

    public static Dice getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Dice has not been initialised. Call getInstance(Random) first.");
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    public int roll() {
        return random.nextInt(BoardUtils.DICE_FACES) + 1;
    }
}
