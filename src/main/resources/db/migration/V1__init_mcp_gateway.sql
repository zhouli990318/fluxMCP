-- MCP Gateway schema
CREATE TABLE api_source (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    protocol_type   VARCHAR(20) DEFAULT 'HTTP',
    base_url        VARCHAR(500),
    openapi_spec    TEXT,
    auth_type       VARCHAR(30) DEFAULT 'NONE',
    auth_config     TEXT,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE TABLE tool_mapping (
    id               BIGSERIAL PRIMARY KEY,
    api_source_id    BIGINT NOT NULL REFERENCES api_source(id) ON DELETE CASCADE,
    operation_id     VARCHAR(200),
    tool_name        VARCHAR(200) NOT NULL,
    tool_description VARCHAR(2000),
    http_method      VARCHAR(10),
    path             VARCHAR(500),
    parameter_schema TEXT,
    response_schema  TEXT,
    enabled          BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tool_mapping_source ON tool_mapping(api_source_id);
CREATE INDEX idx_tool_mapping_enabled ON tool_mapping(enabled);
