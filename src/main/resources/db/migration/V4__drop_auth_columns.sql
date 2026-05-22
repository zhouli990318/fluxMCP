-- Remove server-side authentication configuration columns.
-- Authentication is now handled via client header passthrough.
ALTER TABLE mcp_gateway.api_source DROP COLUMN IF EXISTS auth_type;
ALTER TABLE mcp_gateway.api_source DROP COLUMN IF EXISTS auth_config;
