CREATE TABLE IF NOT EXISTS chat_messages (
    id          BIGSERIAL PRIMARY KEY,
    player_id   BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    channel     SMALLINT NOT NULL CHECK (channel BETWEEN 0 AND 7),
    message     TEXT NOT NULL CHECK (char_length(message) BETWEEN 1 AND 100),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS chat_messages_player_created_idx
    ON chat_messages(player_id, created_at DESC);
