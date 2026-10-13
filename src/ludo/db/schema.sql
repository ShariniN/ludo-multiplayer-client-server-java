-- LUDO-T Database Tier schema (H2, standard SQL).
-- Run by DatabaseServer on start-up; every statement is idempotent, so running it on an
-- existing database changes nothing. "run_database.bat --reset" drops all objects first,
-- which returns the database to its initial (empty) state for a demonstration.
-- Rows are only ever written by matches actually played on the game server.

CREATE TABLE IF NOT EXISTS players (
    player_id       VARCHAR(50)  PRIMARY KEY,
    player_name     VARCHAR(100) NOT NULL,
    games_played    INT          NOT NULL DEFAULT 0,
    games_won       INT          NOT NULL DEFAULT 0,
    total_captures  INT          NOT NULL DEFAULT 0,
    last_active     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS match_history (
    match_id          VARCHAR(50)  PRIMARY KEY,
    room_id           VARCHAR(50)  NOT NULL,
    winner_color      VARCHAR(20)  NOT NULL,
    winner_name       VARCHAR(100) NOT NULL,
    total_rounds      INT          NOT NULL,
    duration_seconds  INT          NOT NULL,
    completed_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saved_games (
    save_id           VARCHAR(50)    PRIMARY KEY,
    room_id           VARCHAR(50)    NOT NULL,
    serialized_state  VARCHAR(20000) NOT NULL,
    round_number      INT            NOT NULL,
    active_player     VARCHAR(20)    NOT NULL,
    saved_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_players_wins ON players (games_won DESC, player_name);
