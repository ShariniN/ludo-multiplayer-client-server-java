package ludo.client;

import ludo.enums.Color;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Swing desktop window for the LUDO-T client (Tier 1 presentation layer).
 *
 * Pure view: it renders whatever GAME_STATE_UPDATE the server last broadcast and
 * forwards clicks to ClientController. Every public mutator is called on the EDT
 * (ServerCommunicator guarantees this), so no Swing component is ever touched
 * from a network thread.
 *
 * Panels: header (player / round / connection), Graphics2D board, dice + turn
 * controls, four live player cards, and a tabbed pane with the activity log,
 * chat and the database-backed leaderboard.
 */
public class LudoGameFrame extends JFrame {

    private static final java.awt.Color BG = new java.awt.Color(240, 243, 246);
    private static final java.awt.Color[] PLAYER_COLORS = {
        new java.awt.Color(211, 47, 47), new java.awt.Color(56, 142, 60),
        new java.awt.Color(245, 127, 23), new java.awt.Color(25, 118, 210)
    };

    private final ClientController controller;
    private final LudoBoardCanvas boardCanvas;
    private final Random animationRandom = new Random();

    // Header
    private JLabel lblPlayerInfo;
    private JLabel lblRoundInfo;

    // Controls
    private JButton btnRollDice;
    private JPanel pnlDiceFace;
    private JLabel lblTurnStatus;
    private int renderedDiceValue = 1;
    private Timer diceAnimationTimer;

    // Player cards
    private final JPanel[] playerCards = new JPanel[4];
    private final JLabel[] lblPlayerCardName = new JLabel[4];
    private final JLabel[] lblPlayerCardStatus = new JLabel[4];

    // Tabs
    private JTextArea txtActivityLog;
    private JTextArea txtChat;
    private JTextField txtChatInput;
    private JTextArea txtLeaderboard;

    private Color myColor;

    public LudoGameFrame(ClientController controller) {
        this.controller = controller;
        this.controller.setView(this);
        this.boardCanvas = new LudoBoardCanvas();

        setTitle("LUDO-T - connecting...");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1180, 780);
        setMinimumSize(new Dimension(1000, 700));
        setLocationRelativeTo(null);

        initUI();
        setupEvents();
    }

    // Layout

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(BG);

        add(createHeaderPanel(), BorderLayout.NORTH);

        JPanel pnlBoardWrapper = new JPanel(new BorderLayout());
        pnlBoardWrapper.setBackground(BG);
        pnlBoardWrapper.setBorder(new EmptyBorder(5, 10, 10, 5));
        pnlBoardWrapper.add(boardCanvas, BorderLayout.CENTER);
        add(pnlBoardWrapper, BorderLayout.CENTER);

        add(createSidePanel(), BorderLayout.EAST);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 10));
        header.setBackground(new java.awt.Color(33, 37, 41));
        header.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel lblTitle = new JLabel("LUDO-T");
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblTitle.setForeground(java.awt.Color.WHITE);

        lblPlayerInfo = new JLabel("Player: connecting...");
        lblPlayerInfo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        lblPlayerInfo.setForeground(new java.awt.Color(255, 193, 7));

        lblRoundInfo = new JLabel("Round: -");
        lblRoundInfo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        lblRoundInfo.setForeground(java.awt.Color.LIGHT_GRAY);

        JPanel pnlInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        pnlInfo.setOpaque(false);
        pnlInfo.add(lblPlayerInfo);
        pnlInfo.add(lblRoundInfo);

        header.add(lblTitle, BorderLayout.WEST);
        header.add(pnlInfo, BorderLayout.CENTER);
        return header;
    }

    private JPanel createSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(400, 0));
        side.setBackground(BG);
        side.setBorder(new EmptyBorder(5, 5, 10, 15));

        // 1. Player cards (live piece counts, effects, active-turn highlight)
        JPanel pnlPlayers = new JPanel(new GridLayout(2, 2, 8, 8));
        pnlPlayers.setBorder(titled("Players in match"));
        pnlPlayers.setBackground(java.awt.Color.WHITE);
        String[] colNames = {"Red", "Green", "Yellow", "Blue"};
        for (int i = 0; i < 4; i++) {
            JPanel card = new JPanel(new BorderLayout(5, 5));
            card.setBackground(new java.awt.Color(250, 250, 250));
            card.setBorder(BorderFactory.createLineBorder(PLAYER_COLORS[i], 1, true));

            lblPlayerCardName[i] = new JLabel(" " + colNames[i] + " - open slot");
            lblPlayerCardName[i].setFont(new Font("SansSerif", Font.BOLD, 12));
            lblPlayerCardName[i].setForeground(PLAYER_COLORS[i]);

            lblPlayerCardStatus[i] = new JLabel("<html>Base 4 &nbsp; Board 0<br>Straight 0 &nbsp; Home 0</html>");
            lblPlayerCardStatus[i].setFont(new Font("SansSerif", Font.PLAIN, 11));
            lblPlayerCardStatus[i].setBorder(new EmptyBorder(2, 5, 4, 2));

            card.add(lblPlayerCardName[i], BorderLayout.NORTH);
            card.add(lblPlayerCardStatus[i], BorderLayout.CENTER);
            playerCards[i] = card;
            pnlPlayers.add(card);
        }
        pnlPlayers.setMinimumSize(new Dimension(380, 150));
        pnlPlayers.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        side.add(pnlPlayers);
        side.add(Box.createVerticalStrut(10));

        // 2. Dice + turn controls
        JPanel pnlControls = new JPanel(new BorderLayout(10, 10));
        pnlControls.setBorder(titled("Turn & dice"));
        pnlControls.setBackground(java.awt.Color.WHITE);

        lblTurnStatus = new JLabel("Waiting for 4 players to join...", SwingConstants.CENTER);
        lblTurnStatus.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblTurnStatus.setForeground(new java.awt.Color(55, 71, 79));

        pnlDiceFace = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawDiceFace((Graphics2D) g, renderedDiceValue);
            }
        };
        pnlDiceFace.setPreferredSize(new Dimension(65, 65));
        pnlDiceFace.setBackground(java.awt.Color.WHITE);

        btnRollDice = new JButton("ROLL DICE");
        btnRollDice.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnRollDice.setFocusPainted(false);
        // Windows L&F ignores setBackground() on buttons, so paint the background ourselves.
        btnRollDice.setContentAreaFilled(false);
        btnRollDice.setOpaque(true);
        btnRollDice.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        enableDiceRoll(false);

        JPanel pnlDiceCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        pnlDiceCenter.setOpaque(false);
        pnlDiceCenter.add(pnlDiceFace);
        pnlDiceCenter.add(btnRollDice);

        pnlControls.add(lblTurnStatus, BorderLayout.NORTH);
        pnlControls.add(pnlDiceCenter, BorderLayout.CENTER);
        pnlControls.setMinimumSize(new Dimension(380, 140));
        pnlControls.setPreferredSize(new Dimension(380, 140));
        pnlControls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        side.add(pnlControls);
        side.add(Box.createVerticalStrut(10));

        // 3. Tabs: activity log, chat, leaderboard
        JTabbedPane tabPane = new JTabbedPane();
        tabPane.setFont(new Font("SansSerif", Font.BOLD, 11));

        txtActivityLog = readOnlyArea();
        tabPane.addTab("Activity log", new JScrollPane(txtActivityLog));

        JPanel pnlChat = new JPanel(new BorderLayout(5, 5));
        pnlChat.setBackground(java.awt.Color.WHITE);
        txtChat = readOnlyArea();
        txtChatInput = new JTextField();
        JButton btnSend = new JButton("Send");
        btnSend.addActionListener(e -> submitChat());
        txtChatInput.addActionListener(e -> submitChat());
        JPanel pnlChatInput = new JPanel(new BorderLayout(5, 0));
        pnlChatInput.setOpaque(false);
        pnlChatInput.add(txtChatInput, BorderLayout.CENTER);
        pnlChatInput.add(btnSend, BorderLayout.EAST);
        pnlChat.add(new JScrollPane(txtChat), BorderLayout.CENTER);
        pnlChat.add(pnlChatInput, BorderLayout.SOUTH);
        tabPane.addTab("Chat", pnlChat);

        JPanel pnlLeaderboard = new JPanel(new BorderLayout(5, 5));
        pnlLeaderboard.setBackground(java.awt.Color.WHITE);
        txtLeaderboard = readOnlyArea();
        txtLeaderboard.setLineWrap(false);
        txtLeaderboard.setText("Click Refresh to load the leaderboard from the database tier...");
        JButton btnRefreshLb = new JButton("Refresh leaderboard");
        btnRefreshLb.addActionListener(e -> {
            txtLeaderboard.setText("Requesting leaderboard from server...");
            controller.requestLeaderboard();
        });
        pnlLeaderboard.add(new JScrollPane(txtLeaderboard), BorderLayout.CENTER);
        pnlLeaderboard.add(btnRefreshLb, BorderLayout.SOUTH);
        tabPane.addTab("Leaderboard", pnlLeaderboard);

        tabPane.setPreferredSize(new Dimension(380, 320));
        side.add(tabPane);
        return side;
    }

    private static TitledBorder titled(String title) {
        return BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 12));
    }

    private static JTextArea readOnlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Monospaced", Font.PLAIN, 11));
        area.setBackground(new java.awt.Color(250, 250, 252));
        return area;
    }

    private void drawDiceFace(Graphics2D g2, int val) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = 60, h = 60;
        g2.setColor(new java.awt.Color(245, 245, 245));
        g2.fillRoundRect(2, 2, w - 4, h - 4, 12, 12);
        g2.setColor(new java.awt.Color(60, 60, 60));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(2, 2, w - 4, h - 4, 12, 12);

        g2.setColor(new java.awt.Color(33, 33, 33));
        int dotR = 8;
        int c = w / 2 - dotR / 2;
        int l = 14, r = w - 14 - dotR;
        int t = 14, b = h - 14 - dotR;
        // pip layout per face
        boolean centre = val % 2 == 1;
        boolean corners = val >= 2;
        boolean otherCorners = val >= 4;
        boolean middles = val == 6;
        if (centre) g2.fillOval(c, c, dotR, dotR);
        if (corners) { g2.fillOval(l, t, dotR, dotR); g2.fillOval(r, b, dotR, dotR); }
        if (otherCorners) { g2.fillOval(r, t, dotR, dotR); g2.fillOval(l, b, dotR, dotR); }
        if (middles) { g2.fillOval(l, c, dotR, dotR); g2.fillOval(r, c, dotR, dotR); }
    }

    private void setupEvents() {
        btnRollDice.addActionListener((ActionEvent e) -> {
            btnRollDice.setEnabled(false);
            controller.requestRollDice();
        });

        boardCanvas.setOnPieceClickedListener(controller::requestMovePiece);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.disconnect();
                dispose();
                System.exit(0);
            }
        });
    }

    private void submitChat() {
        String text = txtChatInput.getText();
        if (text != null && !text.trim().isEmpty()) {
            controller.sendChat(text);
            txtChatInput.setText("");
        }
    }

    // View updates (all invoked on the EDT)

    /** Only problems are shown (in the activity log); a healthy connection needs no on-screen label. */
    public void setConnectionStatus(String status) {
        if (status != null && !status.startsWith("Connected")) {
            appendActivityLog(status);
        }
    }

    public void setAssignedPlayerInfo(Color color, String name, String roomId) {
        this.myColor = color;
        lblPlayerInfo.setText("You: " + name + " (" + color.getDisplayName() + ")");
        setTitle("LUDO-T - " + name + " - " + color.getDisplayName() + " - " + roomId);
        boardCanvas.setViewerColor(color);
    }

    public void setMatchStarted(boolean started) {
        if (started) lblTurnStatus.setText("Match started");
    }

    public void updateLobby(String members) {
        // Format: RED=Player1;GREEN=Open Slot;...
        for (String entry : members.split(";")) {
            int eq = entry.indexOf('=');
            if (eq < 0) continue;
            try {
                int idx = Color.valueOf(entry.substring(0, eq)).ordinal();
                String name = entry.substring(eq + 1);
                lblPlayerCardName[idx].setText(" " + Color.values()[idx].getDisplayName() + " - "
                        + ("Open Slot".equals(name) ? "open slot" : name));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Applies an authoritative state broadcast: board tokens, mystery cell, round,
     * and the per-player scorecards (counts per lifecycle state + active effects).
     */
    public void updateGameState(int round, String activeColor, String piecesData, int mysteryPos,
                                boolean mysteryActive, int connectedPlayers) {
        lblRoundInfo.setText("Round: " + round + "   Players: " + connectedPlayers + "/4");
        boardCanvas.updatePieces(piecesData);
        boardCanvas.setMysteryCell(mysteryPos, mysteryActive);

        int[] base = new int[4], board = new int[4], straight = new int[4], home = new int[4];
        StringBuilder[] effects = new StringBuilder[4];
        for (int i = 0; i < 4; i++) effects[i] = new StringBuilder();
        if (piecesData != null && !piecesData.isEmpty()) {
            for (String token : piecesData.split(";")) {
                String[] f = token.split(":");
                if (f.length < 4) continue;
                int idx = colorIndexFromPieceName(f[0]);
                switch (f[1]) {
                    case "BASE": base[idx]++; break;
                    case "ACTIVE": board[idx]++; break;
                    case "HOME_STRAIGHT": straight[idx]++; break;
                    case "HOME": home[idx]++; break;
                    default: break;
                }
                if (!"NONE".equals(f[3])) {
                    if (effects[idx].length() > 0) effects[idx].append(", ");
                    effects[idx].append(f[0]).append(' ').append(f[3]);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            String effectLine = effects[i].length() == 0 ? "" : "<br><i>" + effects[i] + "</i>";
            lblPlayerCardStatus[i].setText("<html>Base " + base[i] + " &nbsp; Board " + board[i]
                    + "<br>Straight " + straight[i] + " &nbsp; Home " + home[i] + effectLine + "</html>");
            boolean active = activeColor != null && Color.values()[i].name().equals(activeColor);
            playerCards[i].setBackground(active ? new java.awt.Color(255, 249, 196) : new java.awt.Color(250, 250, 250));
            playerCards[i].setBorder(BorderFactory.createLineBorder(PLAYER_COLORS[i], active ? 3 : 1, true));
        }
    }

    private static int colorIndexFromPieceName(String name) {
        switch (name.charAt(0)) {
            case 'R': return 0;
            case 'G': return 1;
            case 'Y': return 2;
            default:  return 3;
        }
    }

    /** Spins the dice face through random pips for ~0.5 s, then settles on the real value. */
    public void animateDiceRoll(int value, String rollerColor) {
        if (diceAnimationTimer != null && diceAnimationTimer.isRunning()) diceAnimationTimer.stop();
        final int[] frames = {0};
        diceAnimationTimer = new Timer(45, null);
        diceAnimationTimer.addActionListener(e -> {
            frames[0]++;
            if (frames[0] >= 10) {
                renderedDiceValue = value;
                diceAnimationTimer.stop();
            } else {
                renderedDiceValue = 1 + animationRandom.nextInt(6);
            }
            pnlDiceFace.repaint();
        });
        diceAnimationTimer.start();
    }

    public void enableDiceRoll(boolean enabled) {
        btnRollDice.setEnabled(enabled);
        if (enabled) {
            btnRollDice.setBackground(new java.awt.Color(25, 118, 210));
            btnRollDice.setForeground(java.awt.Color.WHITE);
            btnRollDice.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            btnRollDice.setBackground(new java.awt.Color(225, 230, 235));
            btnRollDice.setForeground(new java.awt.Color(130, 140, 150));
            btnRollDice.setCursor(Cursor.getDefaultCursor());
        }
    }

    public void setTurnStatus(String status, boolean isMyTurn) {
        lblTurnStatus.setText(status);
        lblTurnStatus.setForeground(isMyTurn ? new java.awt.Color(46, 125, 50) : new java.awt.Color(97, 97, 97));
    }

    public void setMovablePieces(List<String> pieces) {
        boardCanvas.setMovablePieces(pieces);
    }

    public void clearMovablePieces() {
        boardCanvas.setMovablePieces(null);
    }

    public void flashBoard() {
        boardCanvas.flash();
    }

    public void appendActivityLog(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        txtActivityLog.append("[" + time + "] " + msg + "\n");
        txtActivityLog.setCaretPosition(txtActivityLog.getDocument().getLength());
    }

    /** Player chat goes to the Chat tab; SYSTEM notices (rule outcomes, lobby) go to the activity log. */
    public void appendChat(String sender, String message) {
        if ("SYSTEM".equals(sender)) {
            appendActivityLog(message);
            return;
        }
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        txtChat.append("[" + time + "] " + sender + ": " + message + "\n");
        txtChat.setCaretPosition(txtChat.getDocument().getLength());
    }

    /** Renders LEADERBOARD_DATA rows (id,name,played,won,captures;...) and match history. */
    public void showLeaderboard(String source, String rows, String history) {
        StringBuilder sb = new StringBuilder();
        sb.append("LUDO-T GLOBAL LEADERBOARD\n");
        sb.append("source: ").append(source).append("\n\n");
        sb.append(String.format("%-4s %-24s %5s %6s %5s %6s%n", "#", "PLAYER", "WON", "PLAYED", "CAPT", "WIN%"));
        sb.append("--------------------------------------------------------\n");
        int rank = 1;
        if (rows != null && !rows.isEmpty()) {
            for (String row : rows.split(";")) {
                String[] f = row.split(",", -1);
                if (f.length < 5) continue;
                int played = Integer.parseInt(f[2]), won = Integer.parseInt(f[3]);
                double rate = played > 0 ? won * 100.0 / played : 0.0;
                sb.append(String.format("%-4d %-24s %5d %6d %5s %5.1f%%%n", rank++, f[1], won, played, f[4], rate));
            }
        }
        sb.append("\nRECENT MATCHES\n");
        if (history == null || history.isEmpty()) {
            sb.append("  (no completed matches recorded yet)\n");
        } else {
            for (String row : history.split(";")) {
                String[] f = row.split(",", -1);
                if (f.length < 5) continue;
                sb.append(String.format("  %-10s %-7s %-20s %4s rounds %5ss%n", f[0], f[1], f[2], f[3], f[4]));
            }
        }
        txtLeaderboard.setText(sb.toString());
        txtLeaderboard.setCaretPosition(0);
    }

    public void showGameOver(String winnerColor, String winnerName, int rounds) {
        boolean me = myColor != null && myColor.name().equals(winnerColor);
        lblTurnStatus.setText(me ? "You won!" : winnerName + " won");
        JOptionPane.showMessageDialog(this,
                (me ? "Congratulations - you won!" : "Match finished.") + "\nWinner: " + winnerName
                        + " (" + winnerColor + ")\nRounds played: " + rounds
                        + "\n\nThe result has been recorded in the leaderboard.",
                "Game over", JOptionPane.INFORMATION_MESSAGE);
    }
}
