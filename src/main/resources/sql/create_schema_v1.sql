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

CREATE TABLE IF NOT EXISTS PASTE
(
    id              TEXT    PRIMARY KEY,  -- Short unique ID for the paste
    content         TEXT    NOT NULL,     -- The actual paste content
    title           TEXT,                 -- Optional title
    language        TEXT,                 -- Programming language for syntax highlighting
    expires_at      INTEGER,              -- Expiration timestamp (null for no expiration)
    view_count      INTEGER DEFAULT 0,    -- Number of times viewed
    is_private      INTEGER DEFAULT 0,    -- 0 = public, 1 = private
    password_hash   TEXT,                 -- Optional password protection
    user_id         INTEGER,              -- Optional user who created it
    ip_address      TEXT,                 -- IP address of creator
    user_agent      TEXT,                 -- User agent of creator
    created_at      INTEGER NOT NULL,     -- Creation timestamp
    updated_at      INTEGER,              -- Last update timestamp
    FOREIGN KEY (user_id) REFERENCES USER (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_paste_created_at ON PASTE (created_at);
CREATE INDEX IF NOT EXISTS idx_paste_expires_at ON PASTE (expires_at);
CREATE INDEX IF NOT EXISTS idx_paste_user_id ON PASTE (user_id);

