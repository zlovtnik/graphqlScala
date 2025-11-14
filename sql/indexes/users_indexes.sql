-- Indexes for users table
-- These indexes support common query patterns for user authentication and management

-- Index on username for fast login lookups
CREATE INDEX idx_users_username ON users(username);

-- Index on email for user management operations
CREATE INDEX idx_users_email ON users(email);

-- Composite index for username and email searches
CREATE INDEX idx_users_username_email ON users(username, email);

-- Index on created_at for user activity reports
CREATE INDEX idx_users_created_at ON users(created_at);