CREATE TABLE IF NOT EXISTS players (
    id            BIGSERIAL PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name  TEXT NOT NULL,
    x             INTEGER NOT NULL CHECK (x BETWEEN 0 AND 16383),
    y             INTEGER NOT NULL CHECK (y BETWEEN 0 AND 16383),
    plane         INTEGER NOT NULL CHECK (plane BETWEEN 0 AND 3),
    play_time_seconds BIGINT NOT NULL DEFAULT 0 CHECK (play_time_seconds >= 0),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS player_skills (
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    skill     INTEGER NOT NULL CHECK (skill BETWEEN 0 AND 22),
    xp        BIGINT NOT NULL DEFAULT 0 CHECK (xp >= 0),
    PRIMARY KEY (player_id, skill)
);

CREATE TABLE IF NOT EXISTS player_items (
    player_id    BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    inventory_id INTEGER NOT NULL CHECK (inventory_id >= 0),
    slot         INTEGER NOT NULL CHECK (slot >= 0),
    item_id      INTEGER NOT NULL CHECK (item_id >= 0),
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (player_id, inventory_id, slot)
);
