CREATE TABLE IF NOT EXISTS player_varps (
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    varp      INTEGER NOT NULL CHECK (varp BETWEEN 0 AND 65535),
    value     INTEGER NOT NULL CHECK (value <> 0),
    PRIMARY KEY (player_id, varp)
);
