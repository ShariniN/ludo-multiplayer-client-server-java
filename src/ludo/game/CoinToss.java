package ludo.game;

import ludo.enums.Direction;
import java.util.Random;

/**
 * Simulates coin toss rules for determining movement direction.
 */
public class CoinToss {

    private final Random random;

    public CoinToss(Random random) {
        this.random = random;
    }

    // Flips a coin: heads -> CLOCKWISE, tails -> COUNTERCLOCKWISE
    public Direction toss() {
        return random.nextBoolean() ? Direction.CLOCKWISE : Direction.COUNTERCLOCKWISE;
    }
}
