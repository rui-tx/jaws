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

-- Async Request Support Tables
CREATE TABLE IF NOT EXISTS ASYNC_REQUEST (
    id              TEXT PRIMARY KEY,           -- Unique request ID (UUID)
    endpoint        TEXT NOT NULL,              -- Target endpoint
    method          TEXT NOT NULL,              -- HTTP method
    headers         TEXT,                       -- JSON serialized headers
    body            TEXT,                       -- Request body
    client_id       TEXT,                       -- Client identifier (for response routing)
    user_id         INTEGER,                    -- Optional user context
    priority        INTEGER DEFAULT 5,          -- Priority level (1-10, 1=highest)
    max_retries     INTEGER DEFAULT 3,          -- Maximum retry attempts
    current_retries INTEGER DEFAULT 0,          -- Current retry count
    timeout_ms      INTEGER DEFAULT 30000,      -- Timeout in milliseconds
    status          TEXT DEFAULT 'PENDING',     -- PENDING, PROCESSING, COMPLETED, FAILED, TIMEOUT
    created_at      INTEGER NOT NULL,           -- When request was created
    started_at      INTEGER,                    -- When processing started
    completed_at    INTEGER,                    -- When processing completed
    error_message   TEXT,                       -- Error details if failed
    metadata        TEXT,                       -- Additional metadata as JSON
    controller_class TEXT,                      -- Route optimization: controller class name
    method_name     TEXT,                       -- Route optimization: method name
    path_params     TEXT,                       -- Route optimization: JSON serialized path parameters
    FOREIGN KEY (user_id) REFERENCES USER (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS ASYNC_RESPONSE (
    id              TEXT PRIMARY KEY,           -- Response ID (same as request ID)
    request_id      TEXT NOT NULL,              -- Link to request
    status_code     INTEGER,                    -- HTTP status code
    headers         TEXT,                       -- JSON serialized response headers
    body            TEXT,                       -- Response body
    content_type    TEXT,                       -- Response content type
    created_at      INTEGER NOT NULL,           -- When response was generated
    expires_at      INTEGER,                    -- When response expires (for cleanup)
    FOREIGN KEY (request_id) REFERENCES ASYNC_REQUEST (id) ON DELETE CASCADE
);

-- Indexes for async tables performance
CREATE INDEX IF NOT EXISTS idx_async_request_status ON ASYNC_REQUEST (status);
CREATE INDEX IF NOT EXISTS idx_async_request_created_at ON ASYNC_REQUEST (created_at);
CREATE INDEX IF NOT EXISTS idx_async_request_priority ON ASYNC_REQUEST (priority, created_at);
CREATE INDEX IF NOT EXISTS idx_async_request_client_id ON ASYNC_REQUEST (client_id);
CREATE INDEX IF NOT EXISTS idx_async_request_controller_method ON ASYNC_REQUEST (controller_class, method_name);
CREATE INDEX IF NOT EXISTS idx_async_response_expires_at ON ASYNC_RESPONSE (expires_at);
CREATE INDEX IF NOT EXISTS idx_async_response_request_id ON ASYNC_RESPONSE (request_id);

