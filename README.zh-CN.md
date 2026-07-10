<p align="center">
  <h1 align="center">Flux MCP</h1>
  <p align="center"><strong>将任意 OpenAPI 描述的 REST API 转换为 MCP 兼容工具 — 无需修改任何业务代码。</strong></p>
</p>

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/lang-EN-blue" alt="English"></a>
  <a href="README.zh-CN.md"><img src="https://img.shields.io/badge/lang-中文-blue" alt="中文"></a>
</p>

<p align="center">
  <a href="https://github.com/your-org/flux-mcp/actions"><img src="https://img.shields.io/github/actions/workflow/status/your-org/flux-mcp/ci.yml?branch=main&label=CI" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue" alt="License"></a>
  <img src="https://img.shields.io/badge/version-2.0.0-green" alt="Version">
  <img src="https://img.shields.io/badge/Java-25-orange" alt="Java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6db33f" alt="Spring Boot">
  <img src="https://img.shields.io/badge/MCP-Compatible-brightgreen" alt="MCP Compatible">
</p>

---

Flux MCP 是一个独立的网关，用于桥接 HTTP REST API 与 [模型上下文协议 (MCP)](https://modelcontextprotocol.io)。只需粘贴或指定一个 OpenAPI/Swagger 规范，Flux MCP 会自动将每个 API 端点转换为 MCP 工具，供 AI Agent 发现和调用 — 无需修改任何业务代码。

## 功能亮点

- **OpenAPI 到 MCP 转换** — 粘贴或拉取 OpenAPI 3.x / Swagger 2.0 规范，每个接口自动成为可调用的 MCP 工具，包含完整参数 schema。
- **多数据源管理** — 支持注册多个 API 数据源，各自独立配置认证方式（API Key、Bearer Token、Basic Auth），可按源启停。
- **源级隔离的 MCP 端点** — 每个 API 数据源拥有独立的 MCP 端点 `/api/v1/mcp/sources/{id}/mcp/message`，会话互不干扰。
- **内置健康监控** — 定时健康检查，连续失败自动停用，支持指数退避与状态追踪（HEALTHY / DEGRADED / UNREACHABLE）。
- **管理控制台** — React SPA，支持数据源增删改查、手动新增工具、工具编辑（Monaco JSON 编辑器）、在线测试调用、连接信息一键复制。
- **灵活的会话存储** — 默认内存会话（零配置），可选 Redis（Redisson）支持多实例部署。
- **安全优先** — SSRF 防护、路径遍历防护、可配置的请求头透传黑名单、规范文件大小限制。

## 截图

<p align="center">
  <img src="docs/screenshot-tool-editor.png" alt="工具编辑对话框 — 编辑参数 Schema、响应 Schema 和调用示例" width="800">
</p>

## 快速开始

通过 Docker Compose 3 步启动 Flux MCP：

```bash
# 1. 克隆仓库
git clone https://github.com/your-org/flux-mcp.git
cd flux-mcp

# 2. 复制并检查环境变量文件
cp .env.example .env

# 3. 一键启动（PostgreSQL + Redis + Flux MCP）
docker compose up --build
```

打开 `http://localhost:8092` 访问管理控制台。

## 安装方式

### Docker Compose（推荐）

```bash
docker compose up --build
```

将启动以下三个服务：
| 服务 | 镜像 | 内部端口 | 外部端口 |
|------|------|---------|---------|
| PostgreSQL | `pgvector/pgvector:pg16` | 5432 | 5442 |
| Redis | `redis:7-alpine` | 6379 | 6399 |
| Flux MCP | 由 `Dockerfile` 构建 | 8092 | 8092 |

数据库 schema（`flux_mcp`）和 Flyway 迁移会在启动时自动执行。

### 源码构建

前置条件：Java 25、Maven 3.9+、PostgreSQL。

```bash
# 构建 JAR 包
mvn -DskipTests package

# 指定 profile 运行
java -jar target/flux-mcp-2.0.0.jar --spring.profiles.active=dev
```

### 前端开发

```bash
cd frontend
npm install
npm run dev       # Vite 开发服务器 :5174，自动代理 /api 到后端
npm run build     # 生产构建
```

## 配置说明

Flux MCP 通过环境变量配置，复制 `.env.example` 开始：

```bash
cp .env.example .env
```

### 核心变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `FLUX_MCP_SERVER_PORT` | `8092` | HTTP 服务端口 |
| `FLUX_MCP_R2DBC_URL` | `r2dbc:postgresql://localhost:5442/ai_rag_platform?schema=flux_mcp` | 响应式数据库连接（R2DBC） |
| `FLUX_MCP_JDBC_URL` | `jdbc:postgresql://localhost:5442/ai_rag_platform` | JDBC 连接（Flyway 迁移使用） |
| `FLUX_MCP_DB_USERNAME` | `postgres` | 数据库用户名 |
| `FLUX_MCP_DB_PASSWORD` | `123456` | 数据库密码 |

### 会话存储

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `FLUX_MCP_SESSION_STORE_TYPE` | `memory` | `memory`（单实例）或 `redis`（多实例） |
| `FLUX_MCP_SESSION_TTL` | `PT30M` | 会话有效期（ISO 8601 时长格式） |
| `FLUX_MCP_SESSION_CLEANUP_INTERVAL` | `PT5M` | 清理间隔 |

使用 Redis 时需设置 `FLUX_MCP_SESSION_STORE_TYPE=redis` 并配置以下变量：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `FLUX_MCP_REDIS_HOST` | `localhost` | Redis 主机地址 |
| `FLUX_MCP_REDIS_PORT` | `6379` | Redis 端口 |
| `FLUX_MCP_REDIS_PASSWORD` | _(空)_ | Redis 密码 |

### 健康监控

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `FLUX_MCP_HEALTH_ENABLED` | `true` | 是否启用定时健康检查 |
| `FLUX_MCP_HEALTH_INTERVAL` | `PT60S` | 检查间隔 |
| `FLUX_MCP_HEALTH_TIMEOUT` | `PT5S` | 探测超时时间 |
| `FLUX_MCP_HEALTH_SLOW_THRESHOLD` | `PT3S` | 响应慢阈值（超过则标记为 DEGRADED） |
| `FLUX_MCP_HEALTH_MAX_FAILURES` | `5` | 连续失败次数达到此值后自动停用 |
| `FLUX_MCP_HEALTH_AUTO_DEACTIVATE` | `true` | 是否自动停用不可达数据源 |
| `FLUX_MCP_HEALTH_CONCURRENCY` | `5` | 最大并发探测数 |

### MCP 传输协议

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `FLUX_MCP_SSE_PATH` | `/sse` | SSE 端点路径 |
| `FLUX_MCP_STREAMABLE_HTTP_PATH` | `/mcp/message` | Streamable HTTP 端点路径 |

## 架构设计

Flux MCP 采用**六边形架构**（端口与适配器模式）：

```
interfaces/        REST 控制器 + DTO（入站适配器）
application/       应用服务层（编排）
domain/            核心模型、领域服务、端口接口
infrastructure/    R2DBC 仓储、HTTP 客户端、OpenAPI 解析器、MCP 服务器、Redis（出站适配器）
```

核心组件：
- **DynamicApiToolCallbackProvider** — 根据解析的 OpenAPI 操作动态注册 MCP 工具。
- **SourceScopedMcpServerRegistry** — 为每个 API 数据源创建隔离的 MCP 服务实例。
- **SwaggerOpenApiParser** — 自动检测并解析 OpenAPI 3.x 和 Swagger 2.0 规范。
- **McpHealthCheckScheduler** — 定时健康探测与状态流转逻辑。

## REST API 参考

基础路径：`/api/v1/mcp`

| 方法 | 端点 | 说明 |
|------|------|------|
| `GET` | `/sources` | 获取所有 API 数据源 |
| `POST` | `/sources` | 创建新的 API 数据源 |
| `GET` | `/sources/{id}` | 获取数据源详情 |
| `PUT` | `/sources/{id}` | 更新数据源 |
| `DELETE` | `/sources/{id}` | 删除数据源 |
| `PATCH` | `/sources/{id}/toggle-active` | 切换数据源启停状态 |
| `GET` | `/sources/health` | 获取所有数据源健康状态 |
| `POST` | `/sources/{id}/health-check` | 手动触发健康检查 |
| `POST` | `/sources/{id}/parse` | 解析 OpenAPI 规范为工具 |
| `GET` | `/sources/{id}/tools` | 获取数据源的工具列表 |
| `POST` | `/sources/{id}/tools` | 为指定数据源手动创建工具映射 |
| `PUT` | `/tools/{id}` | 更新工具映射 |
| `POST` | `/tools/{id}/test` | 测试调用工具 |
| `GET` | `/connection-info` | 获取 MCP 连接信息 |
| `GET` | `/sources/{id}/connection-info` | 获取数据源级连接信息 |

## 许可证

本项目基于 [MIT 许可证](LICENSE) 开源。