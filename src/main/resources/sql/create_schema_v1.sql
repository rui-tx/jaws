DROP TABLE IF EXISTS USER;

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

