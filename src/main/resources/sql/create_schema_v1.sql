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

-- =============================================
-- ROLE-BASED AUTHORIZATION SYSTEM
-- =============================================

-- Roles table
CREATE TABLE IF NOT EXISTS ROLE (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL UNIQUE,        -- e.g., 'admin', 'editor', 'moderator', 'user'
    description TEXT,                           -- Human-readable description
    created_at  INTEGER NOT NULL,               -- When role was created
    updated_at  INTEGER                         -- When role was last updated
);

-- User-Role relationship (many-to-many)
CREATE TABLE IF NOT EXISTS USER_ROLE (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL,               -- Reference to user
    role_id     INTEGER NOT NULL,               -- Reference to role
    assigned_at INTEGER NOT NULL,               -- When role was assigned
    assigned_by INTEGER,                        -- User ID who assigned this role (optional)
    FOREIGN KEY (user_id) REFERENCES USER (id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES ROLE (id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_by) REFERENCES USER (id) ON DELETE SET NULL,
    UNIQUE(user_id, role_id)                   -- Prevent duplicate role assignments
);

-- Indexes for role system performance
CREATE INDEX IF NOT EXISTS idx_user_role_user_id ON USER_ROLE (user_id);
CREATE INDEX IF NOT EXISTS idx_user_role_role_id ON USER_ROLE (role_id);
CREATE INDEX IF NOT EXISTS idx_user_role_assigned_at ON USER_ROLE (assigned_at);
CREATE INDEX IF NOT EXISTS idx_role_name ON ROLE (name);

-- Insert default roles
INSERT INTO ROLE (name, description, created_at) VALUES 
('admin', 'Full system administrator', strftime('%s', 'now')),
('user', 'Standard user', strftime('%s', 'now'));

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

-- =============================================
-- JOB PROCESSING SYSTEM WITH RETRY SUPPORT
-- =============================================

-- Main Jobs table with retry functionality
CREATE TABLE IF NOT EXISTS JOBS (
    id                TEXT    PRIMARY KEY,      -- Unique job ID (UUID)
    type              TEXT    NOT NULL,         -- Job type (maps to JobRegistry)
    payload           TEXT,                     -- JSON serialized job payload
    priority          INTEGER DEFAULT 5,       -- Priority level (1-10, 1=highest)
    max_retries       INTEGER DEFAULT 3,       -- Maximum retry attempts
    current_retries   INTEGER DEFAULT 0,       -- Current retry count  
    timeout_ms        INTEGER DEFAULT 30000,   -- Timeout in milliseconds
    execution_mode    TEXT    DEFAULT 'PARALLEL', -- PARALLEL or SEQUENTIAL
    status            TEXT    DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED, TIMEOUT, RETRY_SCHEDULED, DEAD_LETTER
    created_at        INTEGER NOT NULL,        -- When job was created
    started_at        INTEGER,                 -- When processing started
    completed_at      INTEGER,                 -- When processing completed
    error_message     TEXT,                    -- Error details if failed
    client_id         TEXT,                    -- Client identifier (for response routing)
    user_id           INTEGER,                 -- Optional user context
    -- Retry system fields
    next_retry_at     INTEGER DEFAULT 0,       -- When job should be retried (epoch milliseconds)
    retry_backoff_ms  INTEGER DEFAULT 1000,    -- Current backoff delay for this job
    last_retry_at     INTEGER DEFAULT 0,       -- When last retry attempt was made
    FOREIGN KEY (user_id) REFERENCES USER (id) ON DELETE SET NULL
);

-- Job Results table (replaces ASYNC_RESPONSE for job system)
CREATE TABLE IF NOT EXISTS JOB_RESULTS (
    id              TEXT    PRIMARY KEY,       -- Result ID (UUID)
    job_id          TEXT    NOT NULL,          -- Link to job
    status_code     INTEGER DEFAULT 200,       -- HTTP-like status code
    headers         TEXT,                      -- JSON serialized headers
    body            TEXT,                      -- Result body/data
    content_type    TEXT,                      -- Content type
    created_at      INTEGER NOT NULL,          -- When result was generated
    expires_at      INTEGER NOT NULL,          -- When result expires (for cleanup)
    FOREIGN KEY (job_id) REFERENCES JOBS (id) ON DELETE CASCADE
);

-- Dead Letter Queue - for jobs that have exhausted all retry attempts
CREATE TABLE IF NOT EXISTS DEAD_LETTER_QUEUE (
    id                TEXT    PRIMARY KEY,     -- DLQ entry ID (UUID)
    original_job_id   TEXT    NOT NULL,        -- Original job ID
    job_type          TEXT    NOT NULL,        -- Job type
    execution_mode    TEXT    NOT NULL,        -- PARALLEL or SEQUENTIAL
    payload           TEXT    NOT NULL,        -- Original job payload
    priority          INTEGER NOT NULL,        -- Original job priority
    max_retries       INTEGER NOT NULL,        -- Max retries that were attempted
    failure_reason    TEXT    NOT NULL,        -- Why job finally failed
    failed_at         INTEGER NOT NULL,        -- When job was moved to DLQ
    retry_attempts    INTEGER NOT NULL,        -- Number of retry attempts made
    retry_history     TEXT,                    -- JSON array of retry attempts with timestamps
    can_be_retried    INTEGER DEFAULT 1,       -- Whether job can be manually retried (1=yes, 0=no)
    created_at        INTEGER NOT NULL         -- When DLQ entry was created
);

-- =============================================
-- INDEXES FOR JOB SYSTEM PERFORMANCE
-- =============================================

-- Job processing indexes
CREATE INDEX IF NOT EXISTS idx_jobs_status ON JOBS (status);
CREATE INDEX IF NOT EXISTS idx_jobs_created_at ON JOBS (created_at);
CREATE INDEX IF NOT EXISTS idx_jobs_priority_created ON JOBS (priority, created_at);
CREATE INDEX IF NOT EXISTS idx_jobs_execution_mode ON JOBS (execution_mode);
CREATE INDEX IF NOT EXISTS idx_jobs_type ON JOBS (type);
CREATE INDEX IF NOT EXISTS idx_jobs_client_id ON JOBS (client_id);
CREATE INDEX IF NOT EXISTS idx_jobs_user_id ON JOBS (user_id);

-- Retry system indexes
CREATE INDEX IF NOT EXISTS idx_jobs_retry_scheduled ON JOBS (status, next_retry_at) WHERE status = 'RETRY_SCHEDULED';
CREATE INDEX IF NOT EXISTS idx_jobs_current_retries ON JOBS (current_retries);
CREATE INDEX IF NOT EXISTS idx_jobs_next_retry_at ON JOBS (next_retry_at);

-- Job results indexes  
CREATE INDEX IF NOT EXISTS idx_job_results_job_id ON JOB_RESULTS (job_id);
CREATE INDEX IF NOT EXISTS idx_job_results_expires_at ON JOB_RESULTS (expires_at);
CREATE INDEX IF NOT EXISTS idx_job_results_created_at ON JOB_RESULTS (created_at);

-- Dead letter queue indexes
CREATE INDEX IF NOT EXISTS idx_dlq_failed_at ON DEAD_LETTER_QUEUE (failed_at);
CREATE INDEX IF NOT EXISTS idx_dlq_job_type ON DEAD_LETTER_QUEUE (job_type);
CREATE INDEX IF NOT EXISTS idx_dlq_can_be_retried ON DEAD_LETTER_QUEUE (can_be_retried);
CREATE INDEX IF NOT EXISTS idx_dlq_original_job_id ON DEAD_LETTER_QUEUE (original_job_id);

-- =============================================
-- IMAGE PROCESSING SYSTEM
-- =============================================

-- Images table - stores information about uploaded images
CREATE TABLE IF NOT EXISTS IMAGES (
    id               TEXT    PRIMARY KEY,      -- Unique image ID (UUID)
    original_filename TEXT   NOT NULL,         -- Original filename when uploaded
    file_path        TEXT    NOT NULL,         -- Path to original file
    file_size        INTEGER NOT NULL,         -- File size in bytes
    mime_type        TEXT    NOT NULL,         -- MIME type (image/jpeg, image/png, etc.)
    width            INTEGER,                  -- Image width in pixels
    height           INTEGER,                  -- Image height in pixels
    status           TEXT    DEFAULT 'pending', -- pending, processing, completed, failed
    upload_date      INTEGER NOT NULL,         -- Upload timestamp
    processed_date   INTEGER,                  -- When processing completed
    user_session     TEXT,                     -- Session ID of uploader (for anonymous uploads)
    user_id          INTEGER,                  -- Optional user who uploaded
    metadata_removed INTEGER DEFAULT 0,        -- Whether EXIF data was removed (0=no, 1=yes)
    FOREIGN KEY (user_id) REFERENCES USER (id) ON DELETE SET NULL
);

-- Image variants table - stores different sizes/formats of processed images
CREATE TABLE IF NOT EXISTS IMAGE_VARIANTS (
    id              TEXT    PRIMARY KEY,      -- Unique variant ID (UUID)
    image_id        TEXT    NOT NULL,         -- Reference to original image
    variant_type    TEXT    NOT NULL,         -- thumbnail, medium, large, webp, etc.
    file_path       TEXT    NOT NULL,         -- Path to variant file
    file_size       INTEGER NOT NULL,         -- File size in bytes
    width           INTEGER,                  -- Variant width in pixels
    height          INTEGER,                  -- Variant height in pixels
    mime_type       TEXT    NOT NULL,         -- MIME type of variant
    created_date    INTEGER NOT NULL,         -- When variant was created
    FOREIGN KEY (image_id) REFERENCES IMAGES (id) ON DELETE CASCADE
);

-- =============================================
-- INDEXES FOR IMAGE SYSTEM PERFORMANCE
-- =============================================

-- Image indexes
CREATE INDEX IF NOT EXISTS idx_images_status ON IMAGES (status);
CREATE INDEX IF NOT EXISTS idx_images_upload_date ON IMAGES (upload_date);
CREATE INDEX IF NOT EXISTS idx_images_user_id ON IMAGES (user_id);
CREATE INDEX IF NOT EXISTS idx_images_user_session ON IMAGES (user_session);
CREATE INDEX IF NOT EXISTS idx_images_mime_type ON IMAGES (mime_type);

-- Image variant indexes
CREATE INDEX IF NOT EXISTS idx_image_variants_image_id ON IMAGE_VARIANTS (image_id);
CREATE INDEX IF NOT EXISTS idx_image_variants_type ON IMAGE_VARIANTS (variant_type);
CREATE INDEX IF NOT EXISTS idx_image_variants_created_date ON IMAGE_VARIANTS (created_date);

