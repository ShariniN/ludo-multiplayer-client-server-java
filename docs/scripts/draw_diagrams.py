import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "figures")
os.makedirs(OUT, exist_ok=True)
plt.rcParams["font.family"] = "DejaVu Sans"

FIG_H = [6.2]
def sp(fs):  # line spacing in axes units for a font size (pt), 1.55x leading
    return fs * 1.55 / (FIG_H[0] * 72)

def box(ax, x, y, w, h, title, lines=(), fc="#EEF3FA", ec="#1F4E79", fs=8.5, tfs=9.5, lw=1.4, radius=0.02, auto=True):
    """Draws a titled box; if auto, h is grown to fit the text so nothing overflows."""
    need = sp(tfs) * 1.6 + len(lines) * sp(fs) + sp(fs) * 0.6
    if auto and need > h:
        y -= (need - h); h = need
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0.004,rounding_size={radius}", fc=fc, ec=ec, lw=lw))
    ax.text(x + w / 2, y + h - sp(tfs) * 0.5, title, ha="center", va="top", fontsize=tfs, fontweight="bold", color="#0B2545")
    for i, l in enumerate(lines):
        ax.text(x + 0.012, y + h - sp(tfs) * 1.8 - i * sp(fs), l, ha="left", va="top", fontsize=fs, color="#222")
    return y, h

def arrow(ax, x1, y1, x2, y2, text="", color="#333", style="-|>", lw=1.4, tpos=0.5, dy=0.012, fs=8, ls="-"):
    a = FancyArrowPatch((x1, y1), (x2, y2), arrowstyle=style, mutation_scale=14, lw=lw, color=color, linestyle=ls)
    ax.add_patch(a)
    if text:
        ax.text(x1 + (x2 - x1) * tpos, y1 + (y2 - y1) * tpos + dy, text, ha="center", va="bottom", fontsize=fs, color=color,
                bbox=dict(fc="white", ec="none", pad=1))

def new_fig(w=11, h=6.2):
    FIG_H[0] = h
    fig, ax = plt.subplots(figsize=(w, h), dpi=170)
    ax.set_xlim(0, 1); ax.set_ylim(0, 1); ax.axis("off")
    return fig, ax

# ── Figure 1: deployment / tiers ─────────────────────────────────────────────
# Fixed coordinates with generous gaps: no label sits on a box edge or another arrow.
fig, ax = new_fig(13, 8.4)
def fbox(x, y, w, h, title, lines, fc, ec, fs=9.2, tfs=10.5):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.004,rounding_size=0.012", fc=fc, ec=ec, lw=1.6, zorder=2))
    ax.text(x + w / 2, y + h - 0.018, title, ha="center", va="top", fontsize=tfs, fontweight="bold", color="#0B2545", zorder=3)
    for i, l in enumerate(lines):
        ax.text(x + 0.012, y + h - 0.062 - i * 0.036, l, ha="left", va="top", fontsize=fs, color="#222", zorder=3)
ax.text(0.5, 0.995, "LUDO-T deployment: three independently launched processes", ha="center", va="top", fontsize=14, fontweight="bold")
# Tier 1
ax.text(0.12, 0.93, "Tier 1 - Presentation\n(run_client.bat, one per player)", ha="center", va="top", fontsize=10, fontweight="bold", color="#444")
client_mid = []
for i, (nm, col) in enumerate([("Swing client A", "#D32F2F"), ("Swing client B", "#388E3C"), ("Swing client C", "#F57F17"), ("Swing client D", "#1976D2")]):
    y = 0.655 - i * 0.2
    fbox(0.01, y, 0.22, 0.17, nm, ["ClientMain, LudoGameFrame", "ClientController,", "ServerCommunicator"], "#FFFFFF", col)
    client_mid.append(y + 0.085)
# Tier 2 container
ax.add_patch(FancyBboxPatch((0.29, 0.03), 0.43, 0.87, boxstyle="round,pad=0.004,rounding_size=0.015", fc="#F5F8FC", ec="#1F4E79", lw=2, zorder=1))
ax.text(0.505, 0.89, "Tier 2 - Game Server process (run_server.bat)", ha="center", va="top", fontsize=11, fontweight="bold", color="#0B2545", zorder=3)
fbox(0.305, 0.66, 0.195, 0.17, "TCP listener :8080", ["ServerSocket.accept()", "ClientHandler per socket", "(cached thread pool)"], "#EEF3FA", "#1F4E79")
fbox(0.51, 0.66, 0.195, 0.17, "Tomcat :8081", ["Apache Tomcat 10.1", "8 servlets", "50 threads"], "#EEF3FA", "#1F4E79")
fbox(0.305, 0.40, 0.40, 0.17, "RequestQueueProcessor", ["ArrayBlockingQueue<RequestTask>(2000), offer(2 s)", "8 x QueueWorker consumer threads (fixed pool)", "AtomicLong metrics: enqueued, processed, dropped"], "#FFF4E0", "#B26A00")
fbox(0.305, 0.05, 0.40, 0.25, "GameRoomManager -> GameRoom", ["one ReentrantLock per room", "Assignment 1 domain unchanged: Board, Player,", "Piece, MoveProcessor, TurnManager, MysteryManager,", "PlayerStrategy, TeleportEffect, Dice, CoinToss", "PersistenceWriter: single-thread DB writes"], "#EAF7EA", "#2E7D32")
# Other clients + Tier 3
fbox(0.79, 0.62, 0.20, 0.21, "Other clients", ["Postman / Newman", "JMeter TCP + HTTP groups", "HttpTestClient bots", "browser: /api/stats"], "#FFFFFF", "#777")
fbox(0.79, 0.05, 0.20, 0.30, "Tier 3 - Database", ["run_database.bat  :8082", "H2 database over TCP", "one thread per connection", "MVStore row locks", "schema.sql at start-up", "data/ludo.mv.db (--reset)"], "#F3E8F8", "#6A1B9A")
# arrows: clients -> TCP listener (labelled once, away from the boxes)
for ym in client_mid:
    arrow(ax, 0.23, ym, 0.305, 0.745, "", "#555", lw=1.0)
arrow(ax, 0.79, 0.725, 0.72, 0.745, "", "#555", lw=1.2)
ax.text(0.755, 0.75, "HTTP", ha="center", va="bottom", fontsize=9, color="#555")
arrow(ax, 0.4025, 0.66, 0.4025, 0.57, "", "#B26A00", lw=1.4)
arrow(ax, 0.6075, 0.66, 0.6075, 0.57, "", "#B26A00", lw=1.4)
ax.text(0.505, 0.615, "enqueue()", ha="center", va="center", fontsize=9, color="#B26A00")
arrow(ax, 0.505, 0.40, 0.505, 0.30, "", "#2E7D32", lw=1.4)
ax.text(0.515, 0.35, "workers call the room", ha="left", va="center", fontsize=9, color="#2E7D32")
arrow(ax, 0.705, 0.18, 0.79, 0.18, "", "#6A1B9A", lw=1.4)
ax.text(0.755, 0.19, "JDBC", ha="center", va="bottom", fontsize=9, color="#6A1B9A")
ax.set_ylim(0.0, 1.0)
fig.savefig(os.path.join(OUT, "fig1_deployment.png"), bbox_inches="tight"); plt.close(fig)

# ── Figure 2: thread model ───────────────────────────────────────────────────
fig, ax = new_fig(11, 6.2)
ax.text(0.5, 0.985, "Threads and hand-offs inside one game-server process", ha="center", va="top", fontsize=12, fontweight="bold")
ya, ha_ = box(ax, 0.02, 0.72, 0.21, 0.18, "main thread", ["ServerSocket.accept() loop", "hands each socket to the", "cached pool"], fs=7.6)
yb, hb = box(ax, 0.02, 0.38, 0.25, 0.28, "ClientHandler-n (producer)", ["one per socket, cached pool", "readLine() -> deserialize()", "enqueue(RequestTask)", "blocks <= 2 s if buffer full", "sendMessage() synchronized"], fs=7.6, fc="#FFFFFF", ec="#D32F2F")
yc, hc = box(ax, 0.02, 0.08, 0.25, 0.22, "Tomcat threads (producer)", ["doGet()/doPost() on a servlet", "enqueue(RequestTask)", "bounded wait for the outcome,", "or AsyncContext long-poll"], fs=7.6, fc="#FFFFFF", ec="#D32F2F")
yq, hq = box(ax, 0.33, 0.33, 0.2, 0.34, "ArrayBlockingQueue", ["capacity 2000", "FIFO, single lock", "offer(task, 2 s)", "poll(500 ms)", "peak depth tracked"], fs=7.8, fc="#FFF4E0", ec="#B26A00", tfs=9.5)
yw, hw = box(ax, 0.62, 0.33, 0.25, 0.34, "QueueWorker-1..8 (consumer)", ["fixed pool, daemon threads", "poll -> handleClientMessage", "room.handleRollDice()", "room.handleMovePiece()", "exceptions caught per task"], fs=7.6, fc="#EAF7EA", ec="#2E7D32", tfs=8.6)
yl, hl = box(ax, 0.55, 0.74, 0.43, 0.2, "GameRoom.roomLock (ReentrantLock)", ["lock(); try { validate, mutate Board/Piece, broadcast", "GAME_STATE_UPDATE } finally { unlock(); }", "one turn at a time per room; other rooms run in parallel"], fs=7.6, fc="#F5F8FC")
yp, hp = box(ax, 0.55, 0.05, 0.43, 0.2, "PersistenceWriter (single thread)", ["persistAsync(): saveGameState / recordMatchResult /", "recordPlayerStats -> JDBC pool -> H2 (Tier 3)", "ordered writes, never inside the room lock"], fs=7.6, fc="#F3E8F8", ec="#6A1B9A")
ys, hs = box(ax, 0.33, 0.05, 0.2, 0.2, "Delivery to clients", ["ClientHandler.sendMessage:", "synchronized PrintWriter", "HttpClientSession: outbox +", "AsyncContext.start() wake-up"], fs=7.4, tfs=8.4)
arrow(ax, 0.125, ya, 0.125, yb + hb, "submit(handler)", lw=1.2, dy=0.002)
arrow(ax, 0.27, yb + hb / 2, 0.33, yq + hq * 0.6, "offer()", "#B26A00", lw=1.5)
arrow(ax, 0.27, yc + hc / 2, 0.33, yq + hq * 0.25, "offer()", "#B26A00", lw=1.5, tpos=0.4)
arrow(ax, 0.53, yq + hq / 2, 0.62, yw + hw / 2, "poll()", "#2E7D32", lw=1.5)
arrow(ax, 0.73, yw + hw, 0.73, yl, "lock()", "#1F4E79", lw=1.2, dy=0.0)
arrow(ax, 0.73, yw, 0.73, yp + hp, "persistAsync()", "#6A1B9A", lw=1.2, dy=0.0)
arrow(ax, 0.63, yw + hw * 0.15, 0.53, ys + hs * 0.7, "broadcast()", "#333", lw=1.2, tpos=0.5)
ax.text(0.5, 0.005, "Only one lock is ever held at a time (roomLock); sendMessage() never waits for a room, so a circular wait cannot form.",
        ha="center", va="bottom", fontsize=7.6, color="#444", style="italic")
fig.savefig(os.path.join(OUT, "fig2_threads.png"), bbox_inches="tight"); plt.close(fig)

# ── Figure 3: sequence diagram of one turn ───────────────────────────────────
# One message per row with even spacing; long labels sit above their arrow on a white background.
fig, ax = new_fig(13, 9.0)
ax.text(0.5, 1.0, "Sequence diagram", ha="center", va="top", fontsize=14, fontweight="bold")
lanes = ["RED GUI\n(EDT)", "RED listener\nthread", "ClientHandler\n(RED)", "Request\nQueue", "QueueWorker-k", "GameRoom\n(roomLock)", "ClientHandler\n(GREEN..BLUE)", "GREEN GUI\n(EDT)"]
xs = [0.06 + i * 0.125 for i in range(len(lanes))]
for x, l in zip(xs, lanes):
    ax.text(x, 0.925, l, ha="center", va="center", fontsize=9, fontweight="bold", bbox=dict(fc="#EEF3FA", ec="#1F4E79", pad=4))
    ax.plot([x, x], [0.02, 0.885], color="#999", lw=0.8, ls="--")
def msg(y, i, j, text, color="#333"):
    arrow(ax, xs[i], y, xs[j], y, text, color, lw=1.2, dy=0.008, fs=8.6)
steps = [
    (0, 2, "click ROLL DICE -> ROLL_DICE", "#D32F2F"),
    (2, 3, "enqueue(task)", "#B26A00"),
    (3, 4, "poll()", "#2E7D32"),
    (4, 5, "handleRollDice() [lock]", "#333"),
    (5, 6, "DICE_ROLLED (broadcast)", "#333"),
    (5, 2, "YOUR_TURN validPieces=R1,R2 [unlock]", "#333"),
    (2, 1, "socket line", "#555"),
    (1, 0, "invokeLater()", "#555"),
    (0, 2, "click R1 -> MOVE_PIECE", "#D32F2F"),
    (2, 3, "enqueue(task)", "#B26A00"),
    (3, 4, "poll()", "#2E7D32"),
    (4, 5, "handleMovePiece() [lock]", "#333"),
    (5, 6, "PIECE_MOVED, STATE_UPDATE [unlock]", "#333"),
    (6, 7, "invokeLater -> repaint", "#1976D2"),
]
row = 0.82 / len(steps)
for k, (i, j, text, color) in enumerate(steps):
    msg(0.84 - k * row, i, j, text, color)
fig.savefig(os.path.join(OUT, "fig3_sequence.png"), bbox_inches="tight"); plt.close(fig)

# ── Figure 4: clean architecture layers -> tiers ─────────────────────────────
# Four full-width bands, outermost first; arrows show that dependencies point inward (downward).
fig, ax = new_fig(11, 7.4)
ax.text(0.5, 0.995, "Clean Architecture layers and the tier each one runs in", ha="center", va="top", fontsize=13, fontweight="bold")
NL = chr(10)
bands = [
    ("Frameworks & drivers", "#F3E8F8", "#6A1B9A",
     "java.net sockets; Apache Tomcat (embedded); H2 database server + JDBC driver;" + NL
     + "Swing / Graphics2D: LudoGameFrame, LudoBoardCanvas, ServerCommunicator;" + NL
     + "ConsoleGameListener + GameLogger (kept for ludo.Main)",
     "Tier 1 (GUI)" + NL + "Tier 2 (Tomcat)" + NL + "Tier 3 (H2)"),
    ("Interface adapters", "#EEF3FA", "#1F4E79",
     "ClientHandler, ClientSession, RequestQueueProcessor, RequestTask;" + NL
     + "NetworkProtocol, GameMessage; LudoServlet + 7 servlets, HttpClientSession;" + NL
     + "ClientController; JdbcPersistenceGateway (SQL)",
     "Tier 2 and Tier 1"),
    ("Use cases", "#FFF4E0", "#B26A00",
     "GameRoom, GameRoomManager (added in A2);" + NL
     + "MoveProcessor, TurnManager, MysteryManager (from A1);" + NL
     + "PlayerStrategy + 4 strategies, TeleportEffect + 6 effects; PersistenceGateway (port)",
     "Tier 2 (server)"),
    ("Entities", "#EAF7EA", "#2E7D32",
     "Board, Piece, Player, Dice (Singleton), CoinToss," + NL
     + "MysteryCell, MoveResult, BoardUtils;" + NL
     + "Color, Direction, PieceState, MysteryEffect (Null Object)",
     "Tier 2 (server);" + NL + "enums shared"),
]
top, band_h, gap = 0.9, 0.16, 0.055
for i, (title, fc, ec, body, tier) in enumerate(bands):
    y = top - i * (band_h + gap) - band_h
    ax.add_patch(FancyBboxPatch((0.01, y), 0.98, band_h, boxstyle="round,pad=0.004,rounding_size=0.015",
                                fc=fc, ec=ec, lw=1.6))
    ax.text(0.025, y + band_h / 2, title, ha="left", va="center", fontsize=11, fontweight="bold", color="#0B2545")
    ax.text(0.25, y + band_h / 2, body, ha="left", va="center", fontsize=8.6, color="#222", linespacing=1.5)
    ax.text(0.975, y + band_h / 2, tier, ha="right", va="center", fontsize=8.6, color=ec, fontweight="bold", linespacing=1.4)
    if i < len(bands) - 1:
        arrow(ax, 0.5, y - 0.004, 0.5, y - gap + 0.004, "", "#333", style="-|>", lw=1.6)
ax.text(0.53, top - (band_h + gap) * 0.5 - band_h * 0.5 + 0.004, "depends on", ha="left", va="center", fontsize=8, color="#444", style="italic")
bottom = top - 4 * band_h - 3 * gap
ax.set_ylim(bottom - 0.02, 1.0)
fig.savefig(os.path.join(OUT, "fig4_clean_arch.png"), bbox_inches="tight"); plt.close(fig)
print("diagrams written to", OUT)
