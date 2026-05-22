# MCP Gateway

`mcp-gateway` is the standalone bridge between OpenAPI-described HTTP APIs and MCP-compatible tool exposure.

## What it does

- manages API sources and tool mappings
- parses OpenAPI or Swagger specs into callable tools
- exposes source-scoped MCP endpoints over SSE and streamable HTTP
- executes HTTP tools with auth/header/query/body mapping
- performs source health checks
- persists source and tool metadata in PostgreSQL
- stores MCP sessions in memory by default, with optional Redis backing

This repository is already standalone-oriented:

- no dependency on the original monorepo `shared-kernel`
- standalone Maven root (`pom.xml`)
- standalone Dockerfile and Compose
- standalone environment variables prefixed with `MCP_GATEWAY_`

## Run locally

Prerequisites:

- PostgreSQL compatible with the existing `mcp_gateway` schema migrations
- optional Redis if you want shared/distributed session storage
- Java 21+
- Maven 3.9+

Compile locally:

```powershell
cd mcp-gateway-standalone
mvn -DskipTests compile
```

The default active profile is now `dev`. If you need the old machine-specific `local` overlay, enable it explicitly.

Run locally:

```powershell
mvn -DskipTests spring-boot:run
```

Container-oriented standalone assets are also included:

```powershell
docker compose -f compose.yml up --build
```

The standalone compose file mounts `init-db.sql` to create the `mcp_gateway` schema explicitly, and Flyway is also configured with `create-schemas=true` as a fallback.

By default the service uses `src/main/resources/application.yml` and profile overlays in `application-dev.yml` and `application-local.yml`.

## Standalone-oriented environment variables

Use the variables in `.env.example`.

Core variables:

- `MCP_GATEWAY_SERVER_PORT`
- `MCP_GATEWAY_R2DBC_URL`
- `MCP_GATEWAY_JDBC_URL`
- `MCP_GATEWAY_DB_USERNAME`
- `MCP_GATEWAY_DB_PASSWORD`
- `MCP_GATEWAY_SESSION_STORE_TYPE`

Optional Redis variables:

- `MCP_GATEWAY_REDIS_HOST`
- `MCP_GATEWAY_REDIS_PORT`
- `MCP_GATEWAY_REDIS_PASSWORD`

## Session storage modes

- `memory`: default standalone mode, no Redis required
- `redis`: compatible with the current monorepo deployment and multi-instance sharing

## Validation

Useful commands from this repository root:

```powershell
mvn -q -DskipTests compile
mvn -q test -Dsurefire.failIfNoSpecifiedTests=false
```

## Suggested next steps

- add LICENSE / CONTRIBUTING / SECURITY policy files
- configure CI workflow for build and tests
- publish Docker image and versioned releases