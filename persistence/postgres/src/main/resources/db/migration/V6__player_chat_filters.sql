ALTER TABLE players
    ADD COLUMN public_chat_mode SMALLINT NOT NULL DEFAULT 0 CHECK (public_chat_mode BETWEEN 0 AND 3),
    ADD COLUMN private_chat_mode SMALLINT NOT NULL DEFAULT 0 CHECK (private_chat_mode BETWEEN 0 AND 2),
    ADD COLUMN trade_chat_mode SMALLINT NOT NULL DEFAULT 0 CHECK (trade_chat_mode BETWEEN 0 AND 2);
