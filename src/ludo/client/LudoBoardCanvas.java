package ludo.client;

import ludo.enums.Color;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Custom Swing component (Graphics2D) rendering the full 4-player Ludo board.
 * Draws the 15x15 track, corner bases, home straights, the mystery cell, and every
 * token from the last server snapshot; highlights movable tokens and maps mouse
 * clicks back to token names. It holds no rules — only render state.
 */
public class LudoBoardCanvas extends JPanel {

    private static final int GRID_SIZE = 15;
    private final Map<String, PieceRenderInfo> piecePositions = new HashMap<>();
    private final Set<String> movablePieceNames = new HashSet<>();
    private int mysteryCellPosition = -1;
    private boolean mysteryCellActive = false;
    private Color viewerColor;
    private int flashFrames = 0;
    private javax.swing.Timer flashTimer;

    private Consumer<String> onPieceClickedListener;

    public static class PieceRenderInfo {
        public String name;
        public Color color;
        public String state; // BASE, ACTIVE, HOME_STRAIGHT, HOME
        public int position;
        public String effect;

        public PieceRenderInfo(String name, Color color, String state, int position, String effect) {
            this.name = name;
            this.color = color;
            this.state = state;
            this.position = position;
            this.effect = effect;
        }
    }

    public LudoBoardCanvas() {
        setPreferredSize(new Dimension(600, 600));
        setBackground(new java.awt.Color(245, 247, 250));
        initDefaultPieces();

        // mouseReleased, not mouseClicked: Swing drops clicks if the pointer moves between press and release.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    handleCanvasClick(e.getX(), e.getY());
                }
            }
        });
    }

    private void initDefaultPieces() {
        Color[] colors = {Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE};
        String[] prefixes = {"R", "G", "Y", "B"};
        for (int c = 0; c < 4; c++) {
            for (int i = 1; i <= 4; i++) {
                String name = prefixes[c] + i;
                piecePositions.put(name, new PieceRenderInfo(name, colors[c], "BASE", -1, "NONE"));
            }
        }
    }

    public void setOnPieceClickedListener(Consumer<String> listener) {
        this.onPieceClickedListener = listener;
    }

    public synchronized void updatePieces(String piecesData) {
        if (piecesData == null || piecesData.trim().isEmpty()) return;
        piecePositions.clear();

        // Format: R1:ACTIVE:12:NONE;R2:BASE:-1:NONE;...
        String[] tokens = piecesData.split(";");
        for (String token : tokens) {
            String[] parts = token.split(":");
            if (parts.length >= 4) {
                String name = parts[0];
                Color color = getColorFromName(name);
                String state = parts[1];
                int pos = Integer.parseInt(parts[2]);
                String effect = parts[3];
                piecePositions.put(name, new PieceRenderInfo(name, color, state, pos, effect));
            }
        }
        repaint();
    }

    public synchronized void setMovablePieces(List<String> pieceNames) {
        movablePieceNames.clear();
        if (pieceNames != null) {
            movablePieceNames.addAll(pieceNames);
        }
        repaint();
    }

    public synchronized void setMysteryCell(int position, boolean active) {
        this.mysteryCellPosition = position;
        this.mysteryCellActive = active;
        repaint();
    }

    /** Marks which corner base belongs to the local player. */
    public void setViewerColor(Color color) {
        this.viewerColor = color;
        repaint();
    }

    /** Brief red pulse over the board, used when a capture happens (Swing Timer on the EDT). */
    public void flash() {
        flashFrames = 6;
        if (flashTimer != null && flashTimer.isRunning()) flashTimer.stop();
        flashTimer = new javax.swing.Timer(60, e -> {
            flashFrames--;
            if (flashFrames <= 0) ((javax.swing.Timer) e.getSource()).stop();
            repaint();
        });
        flashTimer.start();
    }

    private Color getColorFromName(String name) {
        if (name.startsWith("R")) return Color.RED;
        if (name.startsWith("G")) return Color.GREEN;
        if (name.startsWith("Y")) return Color.YELLOW;
        return Color.BLUE;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight()) - 20;
        int cellSize = size / GRID_SIZE;
        int startX = (getWidth() - (cellSize * GRID_SIZE)) / 2;
        int startY = (getHeight() - (cellSize * GRID_SIZE)) / 2;

        drawBoardGrid(g2, startX, startY, cellSize);
        drawPlayerBases(g2, startX, startY, cellSize);
        if (mysteryCellActive && mysteryCellPosition >= 0) {
            drawMysteryCell(g2, startX, startY, cellSize, mysteryCellPosition);
        }
        drawPieces(g2, startX, startY, cellSize);
        if (flashFrames > 0) {
            g2.setColor(new java.awt.Color(244, 67, 54, 25 * flashFrames));
            g2.fillRect(startX, startY, cellSize * GRID_SIZE, cellSize * GRID_SIZE);
        }

        g2.dispose();
    }

    private void drawBoardGrid(Graphics2D g2, int sx, int sy, int cs) {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int x = sx + c * cs;
                int y = sy + r * cs;

                if ((r < 6 && c < 6) || (r < 6 && c >= 9) || (r >= 9 && c < 6) || (r >= 9 && c >= 9)) {
                    continue; // corner bases are drawn separately
                }

                if (r >= 6 && r <= 8 && c >= 6 && c <= 8) {
                    drawCenterHome(g2, sx + 6 * cs, sy + 6 * cs, cs * 3);
                    continue;
                }

                java.awt.Color cellBg = java.awt.Color.WHITE;
                // Home straight paths
                if (c == 7 && r >= 1 && r <= 5) cellBg = new java.awt.Color(255, 235, 59, 180); // Yellow lane
                else if (r == 7 && c >= 9 && c <= 13) cellBg = new java.awt.Color(66, 165, 245, 180); // Blue lane
                else if (c == 7 && r >= 9 && r <= 13) cellBg = new java.awt.Color(239, 83, 80, 180); // Red lane
                else if (r == 7 && c >= 1 && c <= 5) cellBg = new java.awt.Color(102, 187, 106, 180); // Green lane

                // Start cells (matching track positions 0, 13, 26, 39)
                if (r == 1 && c == 6) cellBg = new java.awt.Color(255, 214, 0); // Yellow start (0)
                else if (r == 6 && c == 13) cellBg = new java.awt.Color(33, 150, 243); // Blue start (13)
                else if (r == 13 && c == 8) cellBg = new java.awt.Color(244, 67, 54); // Red start (26)
                else if (r == 8 && c == 1) cellBg = new java.awt.Color(76, 175, 80); // Green start (39)

                g2.setColor(cellBg);
                g2.fillRect(x, y, cs, cs);
                g2.setColor(new java.awt.Color(200, 205, 215));
                g2.drawRect(x, y, cs, cs);
            }
        }
    }

    private void drawCenterHome(Graphics2D g2, int x, int y, int size) {
        g2.setColor(new java.awt.Color(240, 240, 240));
        g2.fillRect(x, y, size, size);

        int cx = x + size / 2;
        int cy = y + size / 2;

        // Top triangle (Yellow)
        g2.setColor(new java.awt.Color(255, 214, 0));
        g2.fillPolygon(new int[]{x, x + size, cx}, new int[]{y, y, cy}, 3);

        // Right triangle (Blue)
        g2.setColor(new java.awt.Color(33, 150, 243));
        g2.fillPolygon(new int[]{x + size, x + size, cx}, new int[]{y, y + size, cy}, 3);

        // Bottom triangle (Red)
        g2.setColor(new java.awt.Color(244, 67, 54));
        g2.fillPolygon(new int[]{x, x + size, cx}, new int[]{y + size, y + size, cy}, 3);

        // Left triangle (Green)
        g2.setColor(new java.awt.Color(76, 175, 80));
        g2.fillPolygon(new int[]{x, x, cx}, new int[]{y, y + size, cy}, 3);

        g2.setColor(new java.awt.Color(120, 120, 120));
        g2.drawRect(x, y, size, size);
    }

    private void drawPlayerBases(Graphics2D g2, int sx, int sy, int cs) {
        int baseSize = cs * 6;

        // Top-Left: Yellow Base
        drawSingleBase(g2, sx, sy, baseSize, new java.awt.Color(255, 235, 59), new java.awt.Color(255, 214, 0));

        // Top-Right: Blue Base
        drawSingleBase(g2, sx + cs * 9, sy, baseSize, new java.awt.Color(100, 181, 246), new java.awt.Color(33, 150, 243));

        // Bottom-Right: Red Base
        drawSingleBase(g2, sx + cs * 9, sy + cs * 9, baseSize, new java.awt.Color(239, 83, 80), new java.awt.Color(244, 67, 54));

        // Bottom-Left: Green Base
        drawSingleBase(g2, sx, sy + cs * 9, baseSize, new java.awt.Color(129, 199, 132), new java.awt.Color(76, 175, 80));

        if (viewerColor != null) {
            int bx = sx, by = sy;
            if (viewerColor == Color.BLUE)  { bx = sx + cs * 9; }
            if (viewerColor == Color.RED)   { bx = sx + cs * 9; by = sy + cs * 9; }
            if (viewerColor == Color.GREEN) { by = sy + cs * 9; }
            g2.setColor(new java.awt.Color(33, 33, 33, 170));
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, cs / 2)));
            g2.drawString("YOU", bx + 24, by + 24 + cs / 2);
        }
    }

    private void drawSingleBase(Graphics2D g2, int x, int y, int size, java.awt.Color bg, java.awt.Color border) {
        g2.setColor(bg);
        g2.fillRoundRect(x + 4, y + 4, size - 8, size - 8, 16, 16);
        g2.setColor(border);
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(x + 4, y + 4, size - 8, size - 8, 16, 16);

        int inner = size - 36;
        g2.setColor(java.awt.Color.WHITE);
        g2.fillRoundRect(x + 18, y + 18, inner, inner, 12, 12);
        g2.setColor(border);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(x + 18, y + 18, inner, inner, 12, 12);

        int cs = size / 6;
        int slotR = (int)(cs * 0.9);
        int[][] offsets = {
            {(int)(cs * 1.5), (int)(cs * 1.5)},
            {(int)(cs * 3.5), (int)(cs * 1.5)},
            {(int)(cs * 1.5), (int)(cs * 3.5)},
            {(int)(cs * 3.5), (int)(cs * 3.5)}
        };

        for (int[] off : offsets) {
            int cx = x + off[0] + (cs - slotR) / 2;
            int cy = y + off[1] + (cs - slotR) / 2;
            g2.setColor(new java.awt.Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 60));
            g2.fillOval(cx, cy, slotR, slotR);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(cx, cy, slotR, slotR);
        }
    }

    private void drawMysteryCell(Graphics2D g2, int sx, int sy, int cs, int pos) {
        Point pt = getTrackCellCoordinates(pos, sx, sy, cs);
        if (pt != null) {
            g2.setColor(new java.awt.Color(156, 39, 176, 180));
            g2.fillRoundRect(pt.x + 2, pt.y + 2, cs - 4, cs - 4, 8, 8);
            g2.setColor(java.awt.Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, cs / 2));
            g2.drawString("?", pt.x + cs / 3, pt.y + 2 * cs / 3);
        }
    }

    private void drawPieces(Graphics2D g2, int sx, int sy, int cs) {
        Map<Point, List<PieceRenderInfo>> cellOccupants = new HashMap<>();

        for (PieceRenderInfo piece : piecePositions.values()) {
            Point pt = getPieceDrawLocation(piece, sx, sy, cs);
            if (pt != null) {
                cellOccupants.computeIfAbsent(pt, k -> new ArrayList<>()).add(piece);
            }
        }

        for (Map.Entry<Point, List<PieceRenderInfo>> entry : cellOccupants.entrySet()) {
            Point pt = entry.getKey();
            List<PieceRenderInfo> list = entry.getValue();

            for (int i = 0; i < list.size(); i++) {
                PieceRenderInfo piece = list.get(i);
                int offsetX = (i % 2) * 4;
                int offsetY = (i / 2) * 4;
                drawSinglePiece(g2, pt.x + offsetX, pt.y + offsetY, cs, piece);
            }
        }
    }

    private void drawSinglePiece(Graphics2D g2, int x, int y, int cs, PieceRenderInfo piece) {
        int tokenRadius = (int) (cs * 0.74);
        int px = x + (cs - tokenRadius) / 2;
        int py = y + (cs - tokenRadius) / 2;

        java.awt.Color baseColor = getAwtColor(piece.color);
        boolean isMovable = movablePieceNames.contains(piece.name);

        if (isMovable) {
            g2.setColor(new java.awt.Color(255, 215, 0, 180));
            g2.fillOval(px - 4, py - 4, tokenRadius + 8, tokenRadius + 8);
        }

        GradientPaint gp = new GradientPaint(px, py, baseColor.brighter(), px + tokenRadius, py + tokenRadius, baseColor.darker());
        g2.setPaint(gp);
        g2.fillOval(px, py, tokenRadius, tokenRadius);

        g2.setColor(isMovable ? java.awt.Color.YELLOW : java.awt.Color.WHITE);
        g2.setStroke(new BasicStroke(isMovable ? 2.5f : 1.5f));
        g2.drawOval(px, py, tokenRadius, tokenRadius);

        g2.setColor(java.awt.Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, tokenRadius / 2)));
        FontMetrics fm = g2.getFontMetrics();
        int tx = px + (tokenRadius - fm.stringWidth(piece.name)) / 2;
        int ty = py + ((tokenRadius - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(piece.name, tx, ty);

        // Mystery effect badge: E = ENERGIZED, S = SICK, B = BRIEFING
        if (piece.effect != null && !"NONE".equals(piece.effect)) {
            java.awt.Color badge = "ENERGIZED".equals(piece.effect) ? new java.awt.Color(255, 160, 0)
                    : "SICK".equals(piece.effect) ? new java.awt.Color(120, 144, 156)
                    : new java.awt.Color(156, 39, 176);
            int br = Math.max(9, tokenRadius / 3);
            g2.setColor(badge);
            g2.fillOval(px + tokenRadius - br, py - 2, br, br);
            g2.setColor(java.awt.Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, br - 2)));
            g2.drawString(piece.effect.substring(0, 1), px + tokenRadius - br + 2, py + br - 3);
        }
    }

    private java.awt.Color getAwtColor(Color c) {
        switch (c) {
            case RED: return new java.awt.Color(211, 47, 47);
            case GREEN: return new java.awt.Color(56, 142, 60);
            case YELLOW: return new java.awt.Color(245, 127, 23);
            case BLUE: return new java.awt.Color(25, 118, 210);
            default: return java.awt.Color.GRAY;
        }
    }

    private Point getPieceDrawLocation(PieceRenderInfo p, int sx, int sy, int cs) {
        if ("BASE".equalsIgnoreCase(p.state)) {
            return getBasePieceSlot(p, sx, sy, cs);
        } else if ("HOME".equalsIgnoreCase(p.state)) {
            // finished tokens rest inside their colour's triangle of the centre square
            switch (p.color) {
                case YELLOW: return new Point(sx + 7 * cs, sy + 6 * cs);
                case BLUE:   return new Point(sx + 8 * cs, sy + 7 * cs);
                case RED:    return new Point(sx + 7 * cs, sy + 8 * cs);
                default:     return new Point(sx + 6 * cs, sy + 7 * cs);
            }
        } else if ("HOME_STRAIGHT".equalsIgnoreCase(p.state)) {
            return getHomeStraightCoordinates(p, sx, sy, cs);
        } else {
            return getTrackCellCoordinates(p.position, sx, sy, cs);
        }
    }

    private Point getBasePieceSlot(PieceRenderInfo p, int sx, int sy, int cs) {
        int index = 0;
        try {
            index = Integer.parseInt(p.name.substring(1)) - 1;
        } catch (Exception ignored) {}
        if (index < 0 || index > 3) index = 0;

        int bx = sx, by = sy;
        if (p.color == Color.YELLOW) {
            bx = sx;
            by = sy;
        } else if (p.color == Color.BLUE) {
            bx = sx + cs * 9;
            by = sy;
        } else if (p.color == Color.RED) {
            bx = sx + cs * 9;
            by = sy + cs * 9;
        } else if (p.color == Color.GREEN) {
            bx = sx;
            by = sy + cs * 9;
        }

        int[][] offsets = {
            {(int)(cs * 1.5), (int)(cs * 1.5)},
            {(int)(cs * 3.5), (int)(cs * 1.5)},
            {(int)(cs * 1.5), (int)(cs * 3.5)},
            {(int)(cs * 3.5), (int)(cs * 3.5)}
        };

        return new Point(bx + offsets[index][0], by + offsets[index][1]);
    }

    private Point getHomeStraightCoordinates(PieceRenderInfo p, int sx, int sy, int cs) {
        int step = Math.min(5, Math.max(1, p.position));
        switch (p.color) {
            case YELLOW: return new Point(sx + 7 * cs, sy + step * cs);
            case BLUE: return new Point(sx + (14 - step) * cs, sy + 7 * cs);
            case RED: return new Point(sx + 7 * cs, sy + (14 - step) * cs);
            case GREEN: return new Point(sx + step * cs, sy + 7 * cs);
            default: return new Point(sx + 7 * cs, sy + 7 * cs);
        }
    }

    private Point getTrackCellCoordinates(int pos, int sx, int sy, int cs) {
        if (pos < 0 || pos >= 52) return null;

        int[][] trackGrid = {
            // Segment 1: Yellow Start (0) at (1,6) -> exits Yellow Base (Top-Left)
            {1, 6},  // 0: Yellow Start
            {0, 6},  // 1
            {0, 7},  // 2
            {0, 8},  // 3
            {1, 8},  // 4
            {2, 8},  // 5
            {3, 8},  // 6
            {4, 8},  // 7 (Alpha cell)
            {5, 8},  // 8
            {6, 9},  // 9
            {6, 10}, // 10
            {6, 11}, // 11 (Blue approach)
            {6, 12}, // 12

            // Segment 2: Blue Start (13) at (6,13) -> exits Blue Base (Top-Right)
            {6, 13}, // 13: Blue Start
            {6, 14}, // 14
            {7, 14}, // 15
            {8, 14}, // 16
            {8, 13}, // 17
            {8, 12}, // 18
            {8, 11}, // 19
            {8, 10}, // 20
            {8, 9},  // 21
            {9, 8},  // 22
            {10, 8}, // 23
            {11, 8}, // 24 (Red approach)
            {12, 8}, // 25 (Beta cell)

            // Segment 3: Red Start (26) at (13,8) -> exits Red Base (Bottom-Right)
            {13, 8}, // 26: Red Start
            {14, 8}, // 27
            {14, 7}, // 28
            {14, 6}, // 29
            {13, 6}, // 30
            {12, 6}, // 31
            {11, 6}, // 32
            {10, 6}, // 33
            {9, 6},  // 34
            {8, 5},  // 35
            {8, 4},  // 36
            {8, 3},  // 37 (Green approach)
            {8, 2},  // 38

            // Segment 4: Green Start (39) at (8,1) -> exits Green Base (Bottom-Left)
            {8, 1},  // 39: Green Start
            {8, 0},  // 40
            {7, 0},  // 41
            {6, 0},  // 42
            {6, 1},  // 43
            {6, 2},  // 44
            {6, 3},  // 45 (Gamma cell)
            {6, 4},  // 46
            {6, 5},  // 47
            {5, 6},  // 48
            {4, 6},  // 49
            {3, 6},  // 50 (Yellow approach)
            {2, 6}   // 51
        };

        if (pos < trackGrid.length) {
            int r = trackGrid[pos][0];
            int c = trackGrid[pos][1];
            return new Point(sx + c * cs, sy + r * cs);
        }
        return new Point(sx + 7 * cs, sy + 7 * cs);
    }

    private void handleCanvasClick(int mouseX, int mouseY) {
        if (onPieceClickedListener == null || movablePieceNames.isEmpty()) return;

        int size = Math.min(getWidth(), getHeight()) - 20;
        int cellSize = size / GRID_SIZE;
        int startX = (getWidth() - (cellSize * GRID_SIZE)) / 2;
        int startY = (getHeight() - (cellSize * GRID_SIZE)) / 2;

        for (String pieceName : movablePieceNames) {
            PieceRenderInfo p = piecePositions.get(pieceName);
            if (p != null) {
                Point pt = getPieceDrawLocation(p, startX, startY, cellSize);
                if (pt != null) {
                    Rectangle rect = new Rectangle(pt.x, pt.y, cellSize, cellSize);
                    if (rect.contains(mouseX, mouseY)) {
                        onPieceClickedListener.accept(pieceName);
                        break;
                    }
                }
            }
        }
    }
}
