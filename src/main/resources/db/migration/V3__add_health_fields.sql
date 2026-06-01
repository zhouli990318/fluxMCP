-- V3: Add MCP source health monitoring fields
ALTER TABLE api_source
    ADD COLUMN IF NOT EXISTS health_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS last_health_check_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_healthy_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS consecutive_failures INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error_message TEXT;

CREATE INDEX IF NOT EXISTS idx_api_source_active_health
    ON api_source (active, health_status);
