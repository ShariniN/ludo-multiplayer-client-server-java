package ludo.event;

import ludo.enums.Color;
import ludo.enums.Direction;
import ludo.model.Piece;
import ludo.model.Player;
import ludo.util.GameLogger;

/** Observer that prints game events to the console via GameLogger. */
public class ConsoleGameListener implements GameEventListener {

    private final GameLogger logger = new GameLogger();

    // Setup

    @Override
    public void onPlayerPiecesAnnounced(Player player) {
        logger.printPlayerPieces(player);
    }

    @Override
    public void onInitialRoll(Color color, int value) {
        logger.printInitialRoll(color, value);
    }

    @Override
    public void onTieDetected(int maxRoll) {
        logger.printTieDetected(maxRoll);
    }

    @Override
    public void onFirstPlayerDetermined(Color color) {
        logger.printFirstPlayer(color);
    }

    @Override
    public void onRoundOrderSet(Player[] playOrder) {
        logger.printRoundOrder(playOrder);
    }

    // Turn

    @Override
    public void onDiceRolled(Color color, int value) {
        logger.printDiceRoll(color, value);
    }

    @Override
    public void onPieceMoved(Piece piece, String from, String to, int steps, Direction dir) {
        logger.printStandardMove(piece, from, to, steps, dir);
    }

    @Override
    public void onMoveFromBase(Player player, Piece piece, Direction dir) {
        logger.printMoveFromBase(player, piece);
        logger.printPieceCount(player);
        logger.printCoinToss(piece, dir);
    }

    @Override
    public void onPieceBlocked(Piece piece, String from, String to, Color blockColor, String blockName) {
        logger.printBlocked(piece, from, to, blockColor, blockName);
    }

    @Override
    public void onBlockedIgnored(Color color) {
        logger.printBlockedIgnore(color);
    }

    @Override
    public void onBlockedPartialMove(Color color, String cellBefore) {
        logger.printBlockedPartialMove(color, cellBefore);
    }

    @Override
    public void onCapture(Piece attacker, String location, Piece captured) {
        logger.printCapture(attacker, location, captured);
    }

    @Override
    public void onPieceCount(Player player) {
        logger.printPieceCount(player);
    }

    @Override
    public void onBlockMove(Color color, int from, int to, int steps, Direction dir) {
        logger.printBlockMove(color, from, to, steps, dir);
    }

    @Override
    public void onPieceReachedHome(Piece piece) {
        logger.printPieceReachedHome(piece);
    }

    // Six / Penalty

    @Override
    public void onConsecutiveSixPenalty(Color color) {
        logger.printThirdConsecutiveSix(color);
    }

    @Override
    public void onBlockadeForceBreak(Color color) {
        logger.printBlockadeForceBreak(color);
    }

    @Override
    public void onNoValidMove(Color color) {
        logger.printNoValidMove(color);
    }

    // Mystery Cell

    @Override
    public void onMysteryCellSpawned(int position) {
        logger.printMysteryCellSpawn(position);
    }

    @Override
    public void onMysteryCellLanding(Piece piece, String destination) {
        logger.printMysteryCellLanding(piece, destination);
    }

    // Maps effect keys fired by TeleportEffect implementations to console messages
    @Override
    public void onMysteryEffect(Piece piece, String type) {
        switch (type) {
            case "Alpha-Energized"  -> logger.printEnergized(piece);
            case "Alpha-Sick"       -> logger.printSick(piece);
            case "Beta-Briefing"    -> logger.printBriefing(piece);
            case "Gamma-Direction"  -> logger.printGammaDirectionChange(piece);
            case "Gamma-ToBeta"     -> logger.printGammaToBeta(piece);
            case "Briefing-Escape"  -> logger.printBriefingConsecutiveThree(piece);
            case "Teleport-Alpha"   -> logger.printTeleportAlpha(piece);
            case "Teleport-Beta"    -> logger.printTeleportBeta(piece);
            case "Teleport-Gamma"   -> logger.printTeleportGamma(piece);
            case "Teleport-Base"    -> logger.printTeleportBase(piece);
            case "Teleport-X"       -> logger.printTeleportX(piece);
            case "Teleport-Approach"-> logger.printTeleportApproach(piece);
        }
    }

    // Round End

    @Override
    public void onRoundEnd(Player[] players, int mcPosition, int mcRounds, boolean mcActive) {
        logger.printRoundStatus(players, mcPosition, mcRounds, mcActive);
    }

    // Game Over

    @Override
    public void onPlayerWon(Color color) {
        logger.printWinner(color);
    }

    @Override
    public void onGameOver(int maxRounds) {
        logger.printMaxRoundsReached(maxRounds);
    }
}
