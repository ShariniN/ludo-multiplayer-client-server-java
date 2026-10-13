package ludo.util;

import ludo.enums.Color;
import ludo.enums.Direction;
import ludo.enums.PieceState;
import ludo.model.Piece;
import ludo.model.Player;

/** All console output text for the game (Single Responsibility). */
public class GameLogger {

    public void printPlayerPieces(Player player) {
        String colorName = player.getColor().getDisplayName().toLowerCase();
        String prefix = player.getColor().getInitial();
        System.out.println("The " + colorName + " player has four (04) pieces named "
                + prefix + "1, " + prefix + "2, " + prefix + "3, and " + prefix + "4.");
    }

    public void printInitialRoll(Color color, int value) {
        System.out.println(color.getDisplayName() + " rolls " + value);
    }

    public void printFirstPlayer(Color color) {
        System.out.println(color.getDisplayName()
                + " player has the highest roll and will begin the game.");
    }

    public void printRoundOrder(Player[] playOrder) {
        StringBuilder sb = new StringBuilder("The order of a single round is ");
        for (int i = 0; i < playOrder.length; i++) {
            sb.append(playOrder[i].getColor().getDisplayName());
            if (i < playOrder.length - 2) sb.append(", ");
            else if (i == playOrder.length - 2) sb.append(", and ");
        }
        sb.append(".");
        System.out.println(sb.toString());
    }

    public void printDiceRoll(Color color, int value) {
        System.out.println(color.getDisplayName() + " player rolled " + value + ".");
    }

    public void printMoveFromBase(Player player, Piece piece) {
        System.out.println(player.getColor().getDisplayName()
                + " player moves piece " + piece.getName() + " to the starting point.");
    }

    public void printStandardMove(Piece piece, String fromLoc, String toLoc,
                                  int value, Direction direction) {
        System.out.println(piece.getColor().getDisplayName()
                + " moves piece " + piece.getName()
                + " from location " + fromLoc + " to " + toLoc
                + " by " + value + " units in " + direction.toDisplayString() + " direction.");
    }

    public void printBlocked(Piece blocked, String fromLoc, String toLoc,
                             Color blockColor, String blockPieceName) {
        System.out.println(blocked.getColor().getDisplayName()
                + " piece " + blocked.getName()
                + " is blocked from moving from " + fromLoc + " to " + toLoc
                + " by " + blockColor.getDisplayName() + " piece " + blockPieceName + ".");
    }

    public void printBlockedIgnore(Color color) {
        System.out.println(color.getDisplayName()
                + " does not have other pieces in the board to move instead of the blocked piece. "
                + "Ignoring the throw and moving on to the next player.");
    }

    public void printBlockedPartialMove(Color color, String cellBeforeBlock) {
        System.out.println(color.getDisplayName()
                + " does not have other pieces in the board to move instead of the blocked piece. "
                + "Moved the piece to square " + cellBeforeBlock
                + " which is the cell before the block.");
    }

    public void printCapture(Piece capturer, String location, Piece captured) {
        System.out.println(capturer.getColor().getDisplayName()
                + " piece " + capturer.getName()
                + " lands on square " + location
                + ", captures " + captured.getColor().getDisplayName()
                + " piece " + captured.getName()
                + ", and returns it to the base.");
    }

    public void printPieceCount(Player player) {
        System.out.println(player.getColor().getDisplayName()
                + " player now has " + player.getPiecesOnBoardCount()
                + "/4 on pieces on the board and "
                + player.getPiecesInBaseCount() + "/4 pieces on the base.");
    }

    public void printRoundStatus(Player[] playOrder, int mysteryCellPos, int mysteryRoundsLeft,
                                 boolean mysteryActive) {
        for (Player player : playOrder) {
            printPlayerRoundSummary(player);
        }
        if (mysteryActive) {
            printMysteryCellStatus(mysteryCellPos, mysteryRoundsLeft);
        }
    }

    public void printPlayerRoundSummary(Player player) {
        printPieceCount(player);
        printPieceLocations(player);
    }

    public void printPieceLocations(Player player) {
        System.out.println("============================");
        System.out.println("Location of pieces " + player.getColor().getDisplayName());
        for (Piece piece : player.getPieces()) {
            String locName = BoardUtils.locationName(piece.getState(), piece.getPosition(),
                    piece.getColor());
            System.out.println("Piece " + piece.getName() + " -> " + locName + ".");
        }
        System.out.println("============================");
    }

    public void printMysteryCellStatus(int position, int roundsLeft) {
        System.out.println("The mystery cell is at " + position
                + " and will be at that location for the next "
                + roundsLeft + " rounds.");
    }

    public void printMysteryCellLanding(Piece piece, String destination) {
        System.out.println(piece.getColor().getDisplayName()
                + " player lands on a mystery cell and is teleported to " + destination + ".");
    }

    public void printTeleportAlpha(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName() + " teleported to Alpha.");
    }

    public void printTeleportBeta(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName() + " teleported to Beta.");
    }

    public void printTeleportGamma(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName() + " teleported to Gamma.");
    }

    public void printTeleportApproach(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName() + " teleported to Approach.");
    }

    public void printTeleportX(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName() + " teleported to X.");
    }

    public void printTeleportBase(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName() + " teleported to Base.");
    }

    public void printEnergized(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName()
                + " feels energized, and movement speed doubles.");
    }

    public void printSick(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName()
                + " feels sick, and movement speed halves.");
    }

    public void printBriefing(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName()
                + " attends briefing and cannot move for four rounds.");
    }

    public void printBriefingConsecutiveThree(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName()
                + " is movement-restricted and has rolled three consecutively. Teleporting piece "
                + piece.getName() + " to base.");
    }

    public void printGammaDirectionChange(Piece piece) {
        System.out.println("The " + piece.getColor().getDisplayName()
                + " piece " + piece.getName()
                + ", which was moving clockwise, has changed to moving counterclockwise.");
    }

    public void printGammaToBeta(Piece piece) {
        System.out.println("The " + piece.getColor().getDisplayName()
                + " piece " + piece.getName()
                + " is moving in a counterclockwise direction. Teleporting to Beta from Gamma.");
    }

    public void printMysteryCellSpawn(int position) {
        System.out.println("A mystery cell has spawned in location " + position
                + " and will be at this location for the next four rounds.");
    }

    public void printWinner(Color color) {
        System.out.println(color.getDisplayName() + " player wins!!!");
    }

    public void printThirdConsecutiveSix(Color color) {
        System.out.println(color.getDisplayName()
                + " player rolled a third consecutive six. Roll is ignored.");
    }

    public void printBlockadeForceBreak(Color color) {
        System.out.println(color.getDisplayName()
                + " player rolled three consecutive sixes with a blockade. Breaking the blockade.");
    }

    public void printNoValidMove(Color color) {
        System.out.println(color.getDisplayName()
                + " player has no valid moves. Ignoring the throw.");
    }

    public void printBlockMove(Color color, int fromPos, int toPos, int effectiveMove,
                               Direction direction) {
        System.out.println(color.getDisplayName()
                + " block moves from location " + fromPos + " to " + toPos
                + " by " + effectiveMove + " units in " + direction.toDisplayString()
                + " direction.");
    }

    public void printCoinToss(Piece piece, Direction direction) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName()
                + " will move in " + direction.toDisplayString() + " direction.");
    }

    public void printPieceReachedHome(Piece piece) {
        System.out.println(piece.getColor().getDisplayName()
                + " piece " + piece.getName() + " has reached home.");
    }

    public void printTieDetected(int maxRoll) {
        System.out.println("Tie detected at value " + maxRoll + ". Re-rolling tied players.");
    }

    public void printMaxRoundsReached(int maxRounds) {
        System.out.println("Maximum rounds reached (" + maxRounds + "). Game ended.");
    }
}
