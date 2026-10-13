package ludo;

import ludo.event.ConsoleGameListener;
import ludo.game.Dice;
import ludo.game.GameEngine;

/**
 * Main entry point for the original Assignment 1 console simulation (kept so the
 * domain can still be exercised head-less; the networked GUI is ClientMain/ServerMain).
 * Parses command line seed parameter and runs the game coordinator.
 */
public class Main {

    private static final long DEFAULT_SEED = 42;

    public static void main(String[] args) {
        long seed = DEFAULT_SEED;

        if (args.length > 0) {
            seed = parseSeed(args[0]);
        }

        Dice.resetInstance();

        System.out.println("=== LUDO-T Game Simulation ===");
        System.out.println("Random seed: " + seed);
        System.out.println();

        GameEngine engine = new GameEngine(seed);
        engine.addListener(new ConsoleGameListener());
        engine.runGame();
    }

    private static long parseSeed(String arg) {
        try {
            return Long.parseLong(arg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Seed must be a valid integer, got: '" + arg + "'", e);
        }
    }
}
