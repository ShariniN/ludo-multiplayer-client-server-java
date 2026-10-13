package ludo.client;

import ludo.enums.Color;
import ludo.network.NetworkProtocol;

import javax.swing.*;
import java.awt.*;

/**
 * Main launcher for the Ludo Desktop Client (Tier 1 process).
 * Usage: ClientMain [host] [port] [playerName] [preferredColor]; with no args a
 * connection dialog is shown. Everything Swing happens on the EDT.
 */
public class ClientMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            String host = NetworkProtocol.DEFAULT_HOST;
            int port = NetworkProtocol.DEFAULT_PORT;
            String name = "Player-" + (int)(Math.random() * 900 + 100);
            Color preferredColor = null;

            if (args.length >= 2) {
                host = args[0];
                try { port = Integer.parseInt(args[1]); } catch (Exception ignored) {}
                if (args.length >= 3) name = args[2];
                if (args.length >= 4) {
                    try { preferredColor = Color.valueOf(args[3].toUpperCase()); } catch (Exception ignored) {}
                }
            } else {
                JPanel panel = new JPanel(new GridLayout(4, 2, 8, 8));
                JTextField txtHost = new JTextField(host);
                JTextField txtPort = new JTextField(String.valueOf(port));
                JTextField txtName = new JTextField(name);
                JComboBox<String> cmbColor = new JComboBox<>(new String[]{"Any (Auto-assign)", "RED", "GREEN", "YELLOW", "BLUE"});

                panel.add(new JLabel("Server Host:")); panel.add(txtHost);
                panel.add(new JLabel("Server Port:")); panel.add(txtPort);
                panel.add(new JLabel("Player Name:")); panel.add(txtName);
                panel.add(new JLabel("Preferred Color:")); panel.add(cmbColor);

                int result = JOptionPane.showConfirmDialog(null, panel, "Connect to LUDO-T Server", JOptionPane.OK_CANCEL_OPTION);
                if (result != JOptionPane.OK_OPTION) {
                    System.exit(0);
                    return;
                }

                host = txtHost.getText().trim();
                try { port = Integer.parseInt(txtPort.getText().trim()); } catch (Exception ignored) {}
                name = txtName.getText().trim();
                String colSel = (String) cmbColor.getSelectedItem();
                if (colSel != null && !colSel.startsWith("Any")) {
                    preferredColor = Color.valueOf(colSel);
                }
            }

            ServerCommunicator communicator = new ServerCommunicator(host, port);
            ClientController controller = new ClientController(communicator);
            LudoGameFrame frame = new LudoGameFrame(controller);
            frame.setVisible(true);

            // Connect off the EDT so a slow/unreachable server never freezes the window
            final String fHost = host, fName = name;
            final int fPort = port;
            final Color fColor = preferredColor;
            Thread connector = new Thread(() -> {
                boolean ok = communicator.connect();
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        controller.joinRoom("ROOM-1", fName, fColor);
                    } else {
                        JOptionPane.showMessageDialog(frame, "Could not connect to server at " + fHost + ":" + fPort
                                + "\nStart run_server.bat first (and run_database.bat for the leaderboard).",
                                "Connection Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }, "ClientConnector");
            connector.setDaemon(true);
            connector.start();
        });
    }
}
