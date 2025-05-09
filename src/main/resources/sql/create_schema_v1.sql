DROP TABLE IF EXISTS USER;
DROP TABLE IF EXISTS USER_SESSION;

CREATE TABLE IF NOT EXISTS USER
(
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    user                  TEXT    NOT NULL UNIQUE,
    password_hash         TEXT    NOT NULL,
    email                 TEXT UNIQUE,
    first_name            TEXT,
    last_name             TEXT,
    birthdate             INTEGER,
    gender                TEXT,
    phone_number          TEXT,
    profile_picture       TEXT,
    bio                   TEXT,
    location              TEXT,
    website               TEXT,
    last_login            INTEGER,
    is_active             INTEGER DEFAULT 1,
    is_superuser          INTEGER DEFAULT 0,
    failed_login_attempts INTEGER DEFAULT 0,
    lockout_until         INTEGER,
    created_at            INTEGER NOT NULL,
    updated_at            INTEGER
);

CREATE TABLE IF NOT EXISTS USER_SESSION
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL,
    refresh_token TEXT    NOT NULL,
    access_token  TEXT    NOT NULL,
    user_agent    TEXT,
    ip_address    TEXT,
    is_active     INTEGER DEFAULT 1,
    created_at    INTEGER NOT NULL,
    expires_at    INTEGER NOT NULL,
    last_used_at  INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES USER (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_session_refresh_token ON USER_SESSION (refresh_token);
CREATE INDEX IF NOT EXISTS idx_user_session_access_token ON USER_SESSION (access_token);

