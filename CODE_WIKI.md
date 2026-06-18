# Flux MCP — Code Wiki

> **版本**: 2.0.0 | **语言**: Java 25 / TypeScript | **框架**: Spring Boot 3.5 + React 19  
> **架构模式**: 六边形架构 (Hexagonal Architecture / Ports & Adapters)  
> **许可证**: MIT

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈](#2-技术栈)
3. [项目结构总览](#3-项目结构总览)
4. [架构设计](#4-架构设计)
5. [后端模块详解](#5-后端模块详解)
   - [5.1 启动入口](#51-启动入口)
   - [5.2 Common 通用层](#52-common-通用层)
   - [5.3 Domain 领域层](#53-domain-领域层)
   - [5.4 Application 应用层](#54-application-应用层)
   - [5.5 Interfaces 接口层](#55-interfaces-接口层)
   - [5.6 Infrastructure 基础设施层](#56-infrastructure-基础设施层)
6. [前端模块详解](#6-前端模块详解)
7. [数据库设计](#7-数据库设计)
8. [配置说明](#8-配置说明)
9. [REST API 参考](#9-rest-api-参考)
10. [项目运行方式](#10-项目运行方式)
11. [依赖关系图](#11-依赖关系图)

---

## 1. 项目概述

Flux MCP 是一个独立的网关服务，用于将任意 OpenAPI/Swagger 描述的 REST API 自动转换为 MCP (Model Context Protocol) 兼容工具。AI Agent 可以通过标准 MCP 协议发现并调用这些工具，无需修改任何业务 API 代码。

### 核心能力

| 能力 | 说明 |
|------|------|
| **OpenAPI → MCP 转换** | 粘贴或拉取 OpenAPI 3.x / Swagger 2.0 规范，每个接口自动成为可调用的 MCP 工具 |
| **多数据源管理** | 支持注册多个 API 数据源，各自独立配置认证方式（API Key / Bearer Token / Basic Auth） |
| **源级隔离 MCP 端点** | 每个 API 源拥有独立的 MCP 端点，会话互不干扰 |
| **内置健康监控** | 定时健康检查，连续失败自动停用，支持指数退避与状态追踪 |
| **管理控制台** | React SPA，支持数据源 CRUD、工具编辑（Monaco JSON 编辑器）、在线测试调用 |
| **灵活会话存储** | 默认内存会话（零配置），可选 Redis（Redisson）支持多实例部署 |
| **安全防护** | SSRF 防护、路径遍历防护、请求头透传黑名单、规范文件大小限制 |

---

## 2. 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 25 | 运行环境 |
| Spring Boot | 3.5.13 | 应用框架 |
| Spring WebFlux | — | 响应式 Web 框架 |
| Spring Data R2DBC | — | 响应式数据库访问 |
| Spring AI MCP Server | 1.1.4 | MCP 协议服务端 |
| PostgreSQL (R2DBC) | — | 主数据库 |
| Flyway | 11.8.0 | 数据库迁移 |
| Redisson | 4.3.0 | Redis 客户端 |
| Swagger Parser | 2.1.25 | OpenAPI 规范解析 |
| Guava | 33.4.0 | 通用工具库 |
| Lombok | — | 代码简化 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 19 | UI 框架 |
| TypeScript | 5.8 | 类型安全 |
| Vite | 6 | 构建工具 |
| MUI (Material UI) | 6.4 | UI 组件库 |
| @tanstack/react-query | 5.65 | 服务端状态管理 |
| Monaco Editor | 4.7 | 代码/JSON 编辑器 |
| Axios | 1.7 | HTTP 客户端 |
| notistack | 3.0 | 消息通知 |

### 基础设施

| 组件 | 镜像/版本 | 用途 |
|------|-----------|------|
| PostgreSQL | pgvector/pgvector:pg16 | 主数据库 |
| Redis | redis:7-alpine | 缓存 / 会话存储 |
| Docker | — | 容器化部署 |

---

## 3. 项目结构总览

```
flux-mcp/
├── src/main/java/com/silver/ai/fluxmcp/   # 后端 Java 源码
│   ├── FluxMcpApplication.java            # Spring Boot 启动入口
│   ├── application/                        # 应用服务层
│   │   └── FluxMcpAppService.java          # 核心业务编排
│   ├── common/                             # 通用基础设施
│   │   ├── exception/
│   │   │   ├── BusinessException.java      # 业务异常
│   │   │   └── GlobalExceptionHandler.java # 全局异常处理
│   │   └── result/
│   │       ├── ApiResponse.java            # 统一响应包装
│   │       └── ErrorCode.java              # 错误码枚举
│   ├── domain/                             # 领域层（核心业务逻辑）
│   │   ├── model/                          # 领域模型
│   │   │   ├── ApiSource.java              # API 数据源聚合根
│   │   │   ├── ToolMapping.java            # 工具映射实体
│   │   │   ├── McpAgentSession.java        # MCP 代理会话
│   │   │   ├── AuthType.java               # 认证类型枚举
│   │   │   ├── ProtocolType.java           # 协议类型枚举
│   │   │   └── HealthStatus.java           # 健康状态枚举
│   │   ├── port/                           # 端口接口（依赖倒置）
│   │   │   ├── ApiSourceRepository.java
│   │   │   ├── ToolMappingRepository.java
│   │   │   ├── OpenApiParserPort.java
│   │   │   └── HttpClientPort.java
│   │   └── service/                        # 领域服务
│   │       ├── ToolInvocationDomainService.java
│   │       └── UrlNormalizer.java
│   ├── infrastructure/                     # 基础设施层（适配器实现）
│   │   ├── config/                         # Spring 配置
│   │   │   ├── HttpClientConfig.java
│   │   │   ├── McpHealthProperties.java
│   │   │   ├── McpSessionProperties.java
│   │   │   ├── McpWebConfig.java
│   │   │   ├── R2dbcConfig.java
│   │   │   └── RedisConfig.java
│   │   ├── health/
│   │   │   └── McpHealthCheckScheduler.java # 健康检查调度器
│   │   ├── http/
│   │   │   └── OkHttpClientAdapter.java     # HTTP 客户端适配器
│   │   ├── mcp/                             # MCP 核心基础设施
│   │   │   ├── DynamicApiToolCallbackProvider.java
│   │   │   ├── InMemoryMcpSessionStore.java
│   │   │   ├── McpSessionService.java
│   │   │   ├── McpSessionStore.java
│   │   │   ├── McpTransportMetadataKeys.java
│   │   │   ├── RedisMcpSessionStore.java
│   │   │   ├── SourceScopedMcpRouterConfiguration.java
│   │   │   └── SourceScopedMcpServerRegistry.java
│   │   ├── parser/
│   │   │   └── SwaggerOpenApiParser.java    # OpenAPI 解析器
│   │   └── persistence/                     # 持久化适配器
│   │       ├── adapter/
│   │       │   ├── ApiSourceRepositoryAdapter.java
│   │       │   └── ToolMappingRepositoryAdapter.java
│   │       ├── entity/
│   │       │   ├── ApiSourceEntity.java
│   │       │   └── ToolMappingEntity.java
│   │       └── r2dbc/
│   │           ├── R2dbcApiSourceRepository.java
│   │           └── R2dbcToolMappingRepository.java
│   └── interfaces/                          # 接口层（入站适配器）
│       ├── dto/                             # 数据传输对象
│       │   ├── ApiSourceResponse.java
│       │   ├── CreateApiSourceRequest.java
│       │   ├── ParseOpenApiRequest.java
│       │   ├── ParseSourceType.java
│       │   ├── SourceHealthDto.java
│       │   ├── ToolInvokeRequest.java
│       │   ├── ToolMappingResponse.java
│       │   ├── ToolMappingUpdateRequest.java
│       │   └── UpdateApiSourceRequest.java
│       └── rest/
│           └── FluxMcpController.java       # REST 控制器
├── src/main/java/io/modelcontextprotocol/   # 自定义 MCP 传输层
│   └── server/transport/
│       └── WebFluxStreamableServerTransportProvider.java
├── src/main/resources/
│   ├── application.yml                      # 主配置
│   ├── application-dev.yml                  # 开发环境配置
│   ├── application-local.yml                # 本地环境配置
│   ├── db/migration/                        # Flyway 迁移脚本
│   │   ├── V1__init_mcp_gateway.sql
│   │   ├── V2__add_tool_mapping_example_payload.sql
│   │   └── V3__add_health_fields.sql
│   └── META-INF/
│       └── additional-spring-configuration-metadata.json
├── src/test/java/                           # 测试代码
├── frontend/                                # React 前端
│   ├── src/
│   │   ├── api/
│   │   │   ├── client.ts                    # Axios HTTP 客户端
│   │   │   ├── mcpApi.ts                    # API 接口封装
│   │   │   └── types.ts                     # TypeScript 类型定义
│   │   ├── components/ink/                  # Ink 组件库（大部分已废弃）
│   │   ├── pages/
│   │   │   └── McpPage.tsx                  # 主页面（核心前端组件）
│   │   ├── styles/
│   │   │   └── global.css                   # 全局样式
│   │   ├── theme/
│   │   │   └── ThemeProvider.tsx            # MUI 主题
│   │   ├── App.tsx                          # 根组件
│   │   └── main.tsx                         # 入口文件
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── package.json
├── Dockerfile                               # 多阶段 Docker 构建
├── compose.yml                              # Docker Compose 编排
├── init-db.sql                              # 数据库初始化脚本
├── pom.xml                                  # Maven 项目配置
└── .env.example                             # 环境变量模板
```

---

## 4. 架构设计

### 4.1 六边形架构

Flux MCP 严格遵循**六边形架构**（端口与适配器模式），将核心业务逻辑与外部依赖完全解耦：

```
┌──────────────────────────────────────────────────────────┐
│                     Interfaces 层                        │
│              (入站适配器: REST Controller + DTO)          │
├──────────────────────────────────────────────────────────┤
│                    Application 层                        │
│               (应用服务: 业务用例编排)                     │
├──────────────────────────────────────────────────────────┤
│                      Domain 层                           │
│     (领域模型 + 端口接口 + 领域服务 = 核心业务逻辑)        │
├──────────────────────────────────────────────────────────┤
│                  Infrastructure 层                       │
│  (出站适配器: R2DBC / HTTP Client / MCP Server / Redis)  │
└──────────────────────────────────────────────────────────┘
```

**依赖方向**: Interfaces → Application → Domain ← Infrastructure  
（Domain 层不依赖任何外部框架，Infrastructure 层实现 Domain 层定义的端口接口）

### 4.2 核心数据流

```
用户/管理端 ──REST API──▶ FluxMcpController ──▶ FluxMcpAppService
                                                      │
                          ┌───────────────────────────┤
                          ▼                           ▼
                  ApiSourceRepository        SwaggerOpenApiParser
                  ToolMappingRepository              │
                          │                           ▼
                          ▼                   List<ToolMapping>
                     PostgreSQL                       │
                                                      ▼
              ┌──────────────────────────────────────────────┐
              │        SourceScopedMcpServerRegistry         │
              │  ┌─────────────────────────────────────────┐ │
              │  │  为每个激活源创建独立 McpSyncServer      │ │
              │  │  ┌──────────────────────────────────┐   │ │
              │  │  │  WebFluxStreamableTransport      │   │ │
              │  │  │  DynamicApiToolCallbackProvider   │   │ │
              │  │  └──────────────────────────────────┘   │ │
              │  └─────────────────────────────────────────┘ │
              └──────────────────────────────────────────────┘
                                 │
                    MCP 客户端 ◀──┘  /api/v1/mcp/sources/{id}/mcp/message
```

### 4.3 MCP 工具调用流程

```
MCP 客户端 ──JSON-RPC──▶ WebFluxStreamableServerTransportProvider
                                    │
                                    ▼
                          McpSyncServer.tools/call
                                    │
                                    ▼
                     DynamicApiToolCallbackProvider.invokeTool()
                                    │
                          ┌─────────┴─────────┐
                          ▼                   ▼
                  加载 ToolMapping      加载 ApiSource
                          │                   │
                          └─────────┬─────────┘
                                    ▼
                     ToolInvocationDomainService.invoke()
                                    │
                          ┌─────────┴─────────┐
                          ▼                   ▼
                    构建 URL + Headers   解析调用参数
                          │                   │
                          └─────────┬─────────┘
                                    ▼
                          OkHttpClientAdapter.execute()
                                    │
                                    ▼
                            外部 REST API
```

---

## 5. 后端模块详解

### 5.1 启动入口

#### [FluxMcpApplication](file:///workspace/src/main/java/com/silver/ai/fluxmcp/FluxMcpApplication.java)

Spring Boot 应用主类，关键配置：

- **排除默认 MCP 自动配置**: 显式排除了 Spring AI MCP 的 6 个默认自动配置类（SSE、Streamable HTTP、Server、Stateless、ToolCallbackConverter 等），因为本项目使用自定义的 `SourceScopedMcpServerRegistry` 和 `WebFluxStreamableServerTransportProvider` 替代。
- **启用定时任务**: `@EnableScheduling` 支持健康检查的定时调度。

---

### 5.2 Common 通用层

#### [ErrorCode](file:///workspace/src/main/java/com/silver/ai/fluxmcp/common/result/ErrorCode.java)

统一错误码枚举，定义所有业务错误码：

| 枚举常量 | 编码 | HTTP 状态 | 说明 |
|----------|------|-----------|------|
| `INTERNAL_ERROR` | 50000 | 500 | 系统内部错误 |
| `INVALID_PARAMETER` | 40000 | 400 | 参数校验失败 |
| `MCP_SOURCE_NOT_FOUND` | 44001 | 404 | MCP API 源不存在 |
| `MCP_PARSE_FAILED` | 44002 | 400 | OpenAPI 规范解析失败 |
| `MCP_TOOL_INVOCATION_FAILED` | 44003 | 500 | MCP 工具调用失败 |
| `MCP_TOOL_NOT_FOUND` | 44004 | 404 | MCP 工具不存在 |
| `REQUEST_TIMEOUT` | 50004 | 504 | 请求超时 |
| `DATA_INTEGRITY_CONFLICT` | 40901 | 409 | 数据完整性冲突 |
| `PAYLOAD_TOO_LARGE` | 40013 | 413 | 文件大小超过限制 |

#### [ApiResponse](file:///workspace/src/main/java/com/silver/ai/fluxmcp/common/result/ApiResponse.java)

泛型统一 API 响应包装类：
- `ok(T data)` / `ok()` — 成功响应 (code=200)
- `fail(ErrorCode)` / `fail(int code, String message)` — 失败响应

#### [BusinessException](file:///workspace/src/main/java/com/silver/ai/fluxmcp/common/exception/BusinessException.java)

业务异常类，封装 `ErrorCode` 和可选的详细消息、原始异常。

#### [GlobalExceptionHandler](file:///workspace/src/main/java/com/silver/ai/fluxmcp/common/exception/GlobalExceptionHandler.java)

`@RestControllerAdvice` 全局异常处理器，处理以下异常类型：

| 异常类型 | HTTP 状态 | 说明 |
|----------|-----------|------|
| `BusinessException` | 400 | 业务异常 |
| `WebExchangeBindException` | 400 | 参数校验失败 |
| `ServerWebInputException` | 400 | 输入异常 |
| `DataBufferLimitException` / 负载过大 | 413 | 请求体过大 |
| `IllegalArgumentException` | 400 | 非法参数 |
| `TimeoutException` | 504 | 请求超时 |
| `DataIntegrityViolationException` | 409 | 数据完整性冲突 |
| 其他未捕获异常 | 500 | 兜底处理 |

---

### 5.3 Domain 领域层

#### 领域模型

##### [ApiSource](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/model/ApiSource.java) — 聚合根

表示一个外部 API 数据源，核心字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键 |
| `name` | String | 数据源名称 |
| `description` | String | 描述 |
| `protocolType` | ProtocolType | 协议类型 (HTTP/GRPC) |
| `baseUrl` | String | 基础 URL |
| `openApiSpec` | String | OpenAPI 规范文本 |
| `authType` | AuthType | 认证类型 |
| `authConfig` | String | 认证配置 (JSON) |
| `active` | boolean | 是否激活 |
| `toolMappings` | List\<ToolMapping\> | 关联的工具映射列表 |
| `healthStatus` | HealthStatus | 健康状态 |
| `lastHealthCheckAt` | LocalDateTime | 最后健康检查时间 |
| `consecutiveFailures` | int | 连续失败次数 |
| `lastErrorMessage` | String | 最后错误消息 |

关键方法：
- `activate()` / `deactivate()` — 启用/停用
- `markHealthy()` / `markDegraded(reason)` / `markUnreachable(error)` — 健康状态转换
- `updateSpec(spec)` / `updateInfo(...)` — 更新信息
- `ensureActive()` — 断言激活状态，否则抛异常

##### [ToolMapping](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/model/ToolMapping.java) — 工具映射实体

将 OpenAPI 的一个 operation 映射为一个 MCP Tool：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键 |
| `apiSourceId` | Long | 所属 API 源 ID |
| `operationId` | String | OpenAPI operationId |
| `toolName` | String | MCP 工具名称 |
| `toolDescription` | String | 工具描述 |
| `httpMethod` | String | HTTP 方法 (GET/POST/PUT/DELETE 等) |
| `path` | String | 请求路径模板 |
| `parameterSchema` | String | 参数 JSON Schema |
| `responseSchema` | String | 响应 JSON Schema |
| `examplePayload` | String | 示例负载 |
| `enabled` | boolean | 是否启用 |

##### [McpAgentSession](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/model/McpAgentSession.java) — MCP 代理会话

记录 MCP 客户端与服务器的交互会话：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sourceId` | Long | 所属 API 源 ID |
| `sessionId` | String | 会话 ID |
| `createdAt` | LocalDateTime | 创建时间 |
| `lastSeenAt` | LocalDateTime | 最后活跃时间 |
| `initializedAt` | LocalDateTime | 初始化时间 |
| `transportHeaders` | Map\<String, String\> | 传输层头部 |
| `clientName` | String | 客户端名称 |
| `clientVersion` | String | 客户端版本 |
| `clientCapabilitiesJson` | String | 客户端能力 (JSON) |
| `lastToolName` | String | 最后调用的工具名 |
| `lastToolArgumentsJson` | String | 最后调用参数 |
| `lastToolCallAt` | LocalDateTime | 最后调用时间 |
| `toolCallCount` | long | 调用计数 (线程安全) |

##### 枚举类型

| 枚举 | 值 | 说明 |
|------|-----|------|
| [AuthType](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/model/AuthType.java) | `NONE`, `API_KEY`, `BEARER_TOKEN`, `BASIC_AUTH` | 认证方式 |
| [ProtocolType](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/model/ProtocolType.java) | `HTTP`, `GRPC` | 协议类型 |
| [HealthStatus](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/model/HealthStatus.java) | `UNKNOWN`, `HEALTHY`, `DEGRADED`, `UNREACHABLE` | 健康状态 |

#### 端口接口 (Port)

| 接口 | 职责 | 返回类型 |
|------|------|----------|
| [ApiSourceRepository](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/port/ApiSourceRepository.java) | API 源持久化 | `Mono`/`Flux` (Reactor) |
| [ToolMappingRepository](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/port/ToolMappingRepository.java) | 工具映射持久化 | `Mono`/`Flux` (Reactor) |
| [OpenApiParserPort](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/port/OpenApiParserPort.java) | OpenAPI 规范解析 | `List<ToolMapping>` |
| [HttpClientPort](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/port/HttpClientPort.java) | HTTP 请求执行 | `String` (响应体) |

#### 领域服务

##### [ToolInvocationDomainService](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/service/ToolInvocationDomainService.java)

工具调用核心领域服务，负责：

1. **参数解析**: 解析 MCP 调用参数，支持以下保留字段：
   - `_pathVariables` — 路径变量替换
   - `_query` — 查询参数
   - `_headers` — 自定义请求头
   - `_mcp.transportHeaders` — MCP 传输层透传头
   - `_body` — 请求体

2. **URL 构建**: 路径变量替换 + 路径穿越防护

3. **请求头构建**: 支持三种认证方式：
   - `API_KEY`: 从 authConfig 读取 key/header/value
   - `BEARER_TOKEN`: 添加 `Authorization: Bearer {token}`
   - `BASIC_AUTH`: Base64 编码 username:password

4. **HTTP 调用**: 委托给 `HttpClientPort.execute()`

##### [UrlNormalizer](file:///workspace/src/main/java/com/silver/ai/fluxmcp/domain/service/UrlNormalizer.java)

URL 规范化工具：
- 补全 scheme（默认 `http://`）
- 校验 URL 合法性
- 去掉尾部斜杠
- SSRF 防护（内网地址校验，当前大部分逻辑已注释）

---

### 5.4 Application 应用层

#### [FluxMcpAppService](file:///workspace/src/main/java/com/silver/ai/fluxmcp/application/FluxMcpAppService.java)

应用服务，编排领域服务和仓储，实现所有业务用例：

| 方法 | 职责 |
|------|------|
| `createApiSource(request)` | 创建 API 源，若提供 OpenAPI 规范则自动解析并保存工具映射 |
| `updateApiSource(id, request)` | 更新 API 源基本信息 |
| `getApiSource(id)` | 获取 API 源详情（含关联工具映射列表） |
| `listApiSources()` | 列出所有 API 源 |
| `toggleApiSourceActive(id)` | 切换激活/停用状态 |
| `deleteApiSource(id)` | 删除 API 源及其所有工具映射（事务性） |
| `parseOpenApiSpec(sourceId, spec)` | 解析 OpenAPI 规范文本，先删旧工具映射再保存新的 |
| `parseFromUrl(sourceId, url)` | 从 URL 获取并解析 OpenAPI 规范 |
| `getToolMappings(sourceId)` | 获取指定源的工具映射列表 |
| `getAllEnabledTools()` | 获取所有启用的工具 |
| `updateToolMapping(id, request)` | 更新工具映射配置 |
| `invokeTool(toolId, arguments)` | 调用工具（在 boundedElastic 调度器上执行，避免阻塞 Reactor 线程） |

---

### 5.5 Interfaces 接口层

#### [FluxMcpController](file:///workspace/src/main/java/com/silver/ai/fluxmcp/interfaces/rest/FluxMcpController.java)

REST 控制器，基础路径 `/api/v1/mcp`，暴露 14 个端点（详见 [REST API 参考](#9-rest-api-参考)）。

关键辅助方法：
- `refreshRegistrySafely(source)` — 安全刷新 MCP 注册表（错误时仅记录日志）
- `removeFromRegistrySafely(sourceId)` — 安全移除源
- `buildBaseUrl(request)` — 从请求构建基础 URL

#### DTO 类

| DTO | 方向 | 说明 |
|-----|------|------|
| `CreateApiSourceRequest` | 入 | 创建 API 源请求体 |
| `UpdateApiSourceRequest` | 入 | 更新 API 源请求体 |
| `ParseOpenApiRequest` | 入 | 解析 OpenAPI 请求体（支持 SPEC/URL 两种来源） |
| `ParseSourceType` | — | 解析来源类型枚举: `SPEC`, `URL` |
| `ToolInvokeRequest` | 入 | 工具调用请求体 |
| `ToolMappingUpdateRequest` | 入 | 工具映射更新请求体 |
| `ApiSourceResponse` | 出 | API 源响应（过滤掉 `openApiSpec` 大字段和 `authConfig` 敏感信息） |
| `ToolMappingResponse` | 出 | 工具映射响应 |
| `SourceHealthDto` | 出 | 源健康状态响应 |

---

### 5.6 Infrastructure 基础设施层

#### 5.6.1 配置类

| 配置类 | 职责 |
|--------|------|
| [HttpClientConfig](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/config/HttpClientConfig.java) | 全局 `WebClient.Builder` Bean（连接超时 30s、响应超时 60s、最大内存 10MB） |
| [McpHealthProperties](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/config/McpHealthProperties.java) | 健康检查配置属性 (`app.mcp.health.*`) |
| [McpSessionProperties](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/config/McpSessionProperties.java) | 会话配置属性 (`app.mcp.session.*`) |
| [McpWebConfig](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/config/McpWebConfig.java) | CORS 配置（允许本地开发端口 5173/5174/4173/4174/3000） |
| [R2dbcConfig](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/config/R2dbcConfig.java) | R2DBC 枚举读写转换器注册 |
| [RedisConfig](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/config/RedisConfig.java) | Redisson 客户端配置（条件启用: `store-type=redis`） |

#### 5.6.2 MCP 核心基础设施

##### [SourceScopedMcpServerRegistry](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/mcp/SourceScopedMcpServerRegistry.java) ⭐ 核心组件

为每个激活的 API 源创建和管理独立的 MCP 服务器实例。

| 方法 | 职责 |
|------|------|
| `initialize()` | 应用启动后刷新所有源 (`@EventListener(ApplicationReadyEvent)`) |
| `refreshAll()` | 全量刷新：加载所有激活源，创建/更新/移除 MCP 服务器 |
| `refreshSource(source)` | 同步刷新单个源（使用 `StampedLock` 写锁） |
| `refreshSourceAsync(source)` | 异步刷新（在 boundedElastic 调度器上执行） |
| `removeSource(sourceId)` | 移除源的 MCP 服务器 |
| `route(request)` | **核心路由方法**：从请求路径提取 sourceId，查找或懒初始化 MCP 服务器 |
| `createServer(source)` | 创建 `McpSyncServer`：构建传输提供者、注册工具回调、设置服务器信息 |
| `invokeCallback(...)` | 工具调用回调：记录会话、构建 payload、调用 `ToolCallback.call()` |

内部记录类 `RegisteredSourceServer` 封装 `RouterFunction`、`McpSyncServer`、`WebFluxStreamableServerTransportProvider`。

##### [DynamicApiToolCallbackProvider](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/mcp/DynamicApiToolCallbackProvider.java)

实现 `ToolCallbackProvider`，动态从数据库加载工具映射并创建 `ToolCallback`：

| 方法 | 职责 |
|------|------|
| `getToolCallbacks()` | 获取所有激活源的所有启用工具回调（全局模式，含冲突前缀） |
| `getToolCallbacksForSource(sourceId)` | 获取特定源的工具回调（源级别模式） |
| `createCallback(...)` | 创建 `FunctionToolCallback`，绑定到 `invokeTool` 方法 |
| `invokeTool(toolId, arguments, context)` | 实际执行工具调用：加载 ToolMapping 和 ApiSource，委托给领域服务 |
| `resolveToolName(...)` | 解决工具名冲突（添加源名前缀或 ID 后缀） |

##### 会话管理

| 类 | 职责 |
|----|------|
| [McpSessionStore](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/mcp/McpSessionStore.java) | 会话存储接口 |
| [InMemoryMcpSessionStore](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/mcp/InMemoryMcpSessionStore.java) | 内存实现（默认），使用 `ConcurrentHashMap`，支持 TTL 过期和定时清理 |
| [RedisMcpSessionStore](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/mcp/RedisMcpSessionStore.java) | Redis 实现（条件启用），使用 Redisson `RMapCache`，按 sourceId 分片 |
| [McpSessionService](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/mcp/McpSessionService.java) | 会话管理服务：预配会话、记录工具调用、构建调用元数据 |
| [McpTransportMetadataKeys](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/mcp/McpTransportMetadataKeys.java) | 传输元数据键常量：`SESSION_ID`, `SOURCE_ID`, `REQUEST_HEADERS` 等 |

##### [SourceScopedMcpRouterConfiguration](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/mcp/SourceScopedMcpRouterConfiguration.java)

WebFlux 路由配置：
- `faviconRouter()` — 处理 `/favicon.ico` 返回 204
- `sourceScopedMcpRouter(registry)` — 将所有 MCP 请求委托给 `registry::route`

#### 5.6.3 健康检查

##### [McpHealthCheckScheduler](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/health/McpHealthCheckScheduler.java)

MCP 源心跳检测 + 自动重连调度器：

| 方法 | 职责 |
|------|------|
| `runHealthCheck()` | 定时任务 (`@Scheduled`)：对所有激活源发起 HEAD 请求探测 |
| `probeOnce(sourceId)` | 手动触发单个源的健康检查 |
| `probeAndPersist(source)` | 探测并持久化：根据响应耗时判断 HEALTHY/DEGRADED，失败则标记 UNREACHABLE |
| `probe(source)` | 发起 HEAD 请求，返回响应耗时（5xx 视为不可达，4xx 视为可达） |
| `onHealthTransition(...)` | 状态转换处理：恢复时重建 MCP 服务器；连续失败达阈值时自动停用 |

#### 5.6.4 HTTP 客户端

##### [OkHttpClientAdapter](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/http/OkHttpClientAdapter.java)

实现 `HttpClientPort`，使用 Java 11 `java.net.http.HttpClient` 执行 HTTP 请求：
- 构建 URI（含查询参数编码）
- 设置请求头和方法
- 非 2xx 响应抛出 `BusinessException`

> **注意**: 类名虽为 `OkHttpClientAdapter`，但实际使用 JDK 内置 `HttpClient`，而非 OkHttp 库。

#### 5.6.5 OpenAPI 解析器

##### [SwaggerOpenApiParser](file:///workspace/src/main/java/com/silver/ai/fluxmcp/infrastructure/parser/SwaggerOpenApiParser.java)

实现 `OpenApiParserPort`，使用 Swagger Parser 库解析 OpenAPI 规范：

| 方法 | 职责 |
|------|------|
| `parse(spec, sourceId)` | 从规范文本解析 |
| `parseFromUrl(url, sourceId)` | 从 URL 获取并解析（含 SSRF 校验） |
| `extractToolMappings(openAPI, sourceId)` | 遍历所有路径和 HTTP 方法，提取工具映射 |
| `buildParameterSchema(operation)` | 构建 JSON Schema 参数定义（路径参数 + 查询参数 + 请求体） |
| `readContents(spec)` | 自动检测 Swagger 2.0 或 OpenAPI 3.x 并选择对应解析器 |

#### 5.6.6 持久化适配器

| 类 | 职责 |
|----|------|
| `ApiSourceEntity` | API 源数据库实体（映射 `flux_mcp.api_source` 表） |
| `ToolMappingEntity` | 工具映射数据库实体（映射 `flux_mcp.tool_mapping` 表） |
| `R2dbcApiSourceRepository` | Spring Data R2DBC 仓库接口 |
| `R2dbcToolMappingRepository` | Spring Data R2DBC 仓库接口 |
| `ApiSourceRepositoryAdapter` | 实现 `ApiSourceRepository` 端口，Entity ↔ Domain 转换 |
| `ToolMappingRepositoryAdapter` | 实现 `ToolMappingRepository` 端口，Entity ↔ Domain 转换 |

#### 5.6.7 自定义 MCP 传输提供者

##### [WebFluxStreamableServerTransportProvider](file:///workspace/src/main/java/io/modelcontextprotocol/server/transport/WebFluxStreamableServerTransportProvider.java)

基于 WebFlux 的 MCP Streamable HTTP 传输提供者实现，支持协议版本 `2024-11-05`、`2025-03-26`、`2025-06-18`：

| 方法 | 职责 |
|------|------|
| `handleGet(request)` | 处理 SSE 监听流：验证 Accept 头、查找会话、支持 Last-Event-ID 重放 |
| `handlePost(request)` | 处理 JSON-RPC 消息：initialize（创建会话）、Response、Notification、Request |
| `handleDelete(request)` | 处理会话删除 |
| `notifyClients(method, params)` | 向所有活跃会话广播通知 |
| `closeGracefully()` | 优雅关闭：关闭会话、清理、停止 keep-alive |
| `getRouterFunction()` | 返回 WebFlux 路由函数 |

内部类 `WebFluxStreamableMcpSessionTransport` 通过 `FluxSink<ServerSentEvent>` 发送 SSE 事件。

---

## 6. 前端模块详解

### 6.1 入口与路由

#### [main.tsx](file:///workspace/frontend/src/main.tsx)

React 应用入口，渲染层级：

```
React.StrictMode
  └── QueryClientProvider (@tanstack/react-query)
      └── ThemeProvider (自定义 MUI 主题)
          └── SnackbarProvider (notistack 消息通知)
              └── App
```

`QueryClient` 配置：`retry: 1`、`refetchOnWindowFocus: false`、`staleTime: 30s`。

#### [App.tsx](file:///workspace/frontend/src/App.tsx)

根组件，纯展示布局：标题栏 + `McpPage` 主页面。

### 6.2 API 层

#### [client.ts](file:///workspace/frontend/src/api/client.ts)

基于 Axios 的 HTTP 客户端：
- 自动解析 API 基础 URL（优先级：环境变量 → 开发代理 → 浏览器 location）
- 超时 60s
- 响应拦截器统一错误日志

#### [types.ts](file:///workspace/frontend/src/api/types.ts)

TypeScript 类型定义，核心类型：

| 类型 | 说明 |
|------|------|
| `ApiResponse<T>` | 通用 API 响应包装 |
| `McpApiSource` | MCP API 源实体 |
| `McpToolMapping` | MCP 工具映射实体 |
| `HealthStatus` | 健康状态枚举 |
| `McpConnectionInfo` | MCP 连接信息 |

#### [mcpApi.ts](file:///workspace/frontend/src/api/mcpApi.ts)

封装所有后端 API 调用，导出 `fluxMcpApi` 对象，包含 14 个方法对应后端的 14 个 REST 端点。

### 6.3 页面组件

#### [McpPage.tsx](file:///workspace/frontend/src/pages/McpPage.tsx) ⭐ 核心前端组件

约 897 行的主页面组件，包含 4 个内部子组件：

| 子组件 | 职责 |
|--------|------|
| `MonacoEditor` | 基于 `@monaco-editor/react` 的 JSON 编辑器（React.lazy 懒加载） |
| `MobileTextarea` | 移动端降级方案，用 MUI TextField 替代 Monaco |
| `CreateEditSourceDialog` | 创建/编辑 API 源的对话框（name, description, baseUrl, authType, authConfig） |
| `EditToolDialog` | 编辑工具映射的对话框（支持表格编辑和 JSON 编辑两种模式、调用示例编辑） |
| `TestToolDrawer` | 右侧抽屉，用于测试工具调用（输入 JSON 参数，查看返回结果） |

主组件 `McpPage` 状态管理：

| State | 说明 |
|-------|------|
| `createOpen` | 创建/编辑对话框开关 |
| `selectedSource` | 当前选中的 API 源 |
| `testTool` | 当前测试的工具 |
| `parseMode` | OpenAPI 解析模式 (paste/url) |
| `specContent` / `specUrl` | OpenAPI 规范内容/URL |
| `editingSource` / `editingTool` | 正在编辑的源/工具 |
| `toolSearch` | 工具搜索关键词 |
| `toolPage` / `toolRowsPerPage` | 工具列表分页 |

Hooks 使用：
- `useQuery` × 5：`sources`、`connectionInfo`、`tools`、`sourceHealth`、`healthCheck`
- `useMutation` × 8：创建、更新、删除、解析、切换等操作
- `useMemo` × 2：搜索过滤 + 分页

### 6.4 主题与样式

#### [ThemeProvider.tsx](file:///workspace/frontend/src/theme/ThemeProvider.tsx)

MUI 主题配置：
- 调色板：主色 `#1976d2`，辅色 `#9c27b0`，背景 `#fafafa`
- 圆角：全局 8px
- 字体：Inter 优先
- 组件覆盖：Button（圆角 6）、Card（圆角 8）、Dialog（圆角 12）

#### [global.css](file:///workspace/frontend/src/styles/global.css)

全局样式：body 重置、自定义滚动条（6px 宽、半透明滑块）。

### 6.5 Ink 组件库

| 组件 | 状态 |
|------|------|
| `InkBadge` | 已废弃（返回 null） |
| `InkEmptyState` | 已废弃（返回 null） |
| `InkSegmentedControl` | 已废弃（返回 null） |
| `InkSwitch` | 可用（MUI Switch 的简单透传封装） |

---

## 7. 数据库设计

### 7.1 Schema

数据库: `ai_rag_platform`，Schema: `flux_mcp`

### 7.2 表结构

#### `api_source` — API 数据源

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGSERIAL | PK | 主键 |
| `name` | VARCHAR(200) | NOT NULL | 数据源名称 |
| `description` | TEXT | — | 描述 |
| `protocol_type` | VARCHAR(20) | DEFAULT 'HTTP' | 协议类型 |
| `base_url` | VARCHAR(500) | — | 基础 URL |
| `openapi_spec` | TEXT | — | OpenAPI 规范内容 |
| `auth_type` | VARCHAR(30) | DEFAULT 'NONE' | 认证类型 |
| `auth_config` | TEXT | — | 认证配置 (JSON) |
| `active` | BOOLEAN | DEFAULT true | 是否启用 |
| `health_status` | VARCHAR(20) | DEFAULT 'UNKNOWN' | 健康状态 |
| `last_health_check_at` | TIMESTAMP | — | 最后健康检查时间 |
| `last_healthy_at` | TIMESTAMP | — | 最后健康时间 |
| `consecutive_failures` | INT | DEFAULT 0 | 连续失败次数 |
| `last_error_message` | TEXT | — | 最后错误消息 |
| `created_at` | TIMESTAMP | NOT NULL | 创建时间 |
| `updated_at` | TIMESTAMP | NOT NULL | 更新时间 |

索引: `idx_api_source_active_health` (active, health_status)

#### `tool_mapping` — 工具映射

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGSERIAL | PK | 主键 |
| `api_source_id` | BIGINT | FK → api_source(id) ON DELETE CASCADE | 关联数据源 |
| `operation_id` | VARCHAR(200) | — | OpenAPI operationId |
| `tool_name` | VARCHAR(200) | — | MCP 工具名称 |
| `tool_description` | VARCHAR(2000) | — | 工具描述 |
| `http_method` | VARCHAR(10) | — | HTTP 方法 |
| `path` | VARCHAR(500) | — | 请求路径模板 |
| `parameter_schema` | TEXT | — | 参数 JSON Schema |
| `response_schema` | TEXT | — | 响应 JSON Schema |
| `example_payload` | TEXT | — | 示例负载 |
| `enabled` | BOOLEAN | DEFAULT true | 是否启用 |
| `created_at` | TIMESTAMP | NOT NULL | 创建时间 |

索引: `idx_tool_mapping_source` (api_source_id), `idx_tool_mapping_enabled` (enabled)

### 7.3 迁移历史

| 版本 | 文件 | 变更 |
|------|------|------|
| V1 | `V1__init_mcp_gateway.sql` | 创建 `api_source` 和 `tool_mapping` 表 |
| V2 | `V2__add_tool_mapping_example_payload.sql` | 新增 `example_payload` 列 |
| V3 | `V3__add_health_fields.sql` | 新增健康监控字段和索引 |

---

## 8. 配置说明

### 8.1 环境变量

所有配置通过环境变量覆盖，前缀 `FLUX_MCP_`。

#### 核心配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FLUX_MCP_SERVER_PORT` | 8092 | HTTP 服务端口 |
| `FLUX_MCP_R2DBC_URL` | `r2dbc:postgresql://localhost:5442/ai_rag_platform?schema=flux_mcp` | R2DBC 连接 |
| `FLUX_MCP_JDBC_URL` | `jdbc:postgresql://localhost:5442/ai_rag_platform` | JDBC 连接 (Flyway) |
| `FLUX_MCP_DB_USERNAME` | postgres | 数据库用户名 |
| `FLUX_MCP_DB_PASSWORD` | 123456 | 数据库密码 |

#### 会话存储

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FLUX_MCP_SESSION_STORE_TYPE` | memory | memory / redis |
| `FLUX_MCP_SESSION_TTL` | PT30M | 会话有效期 |
| `FLUX_MCP_SESSION_CLEANUP_INTERVAL` | PT5M | 清理间隔 |
| `FLUX_MCP_REDIS_HOST` | localhost | Redis 主机 |
| `FLUX_MCP_REDIS_PORT` | 6379 | Redis 端口 |
| `FLUX_MCP_REDIS_PASSWORD` | (空) | Redis 密码 |

#### 健康监控

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FLUX_MCP_HEALTH_ENABLED` | true | 启用健康检查 |
| `FLUX_MCP_HEALTH_INTERVAL` | PT60S | 检查间隔 |
| `FLUX_MCP_HEALTH_TIMEOUT` | PT5S | 探测超时 |
| `FLUX_MCP_HEALTH_SLOW_THRESHOLD` | PT3S | 慢响应阈值 |
| `FLUX_MCP_HEALTH_MAX_FAILURES` | 5 | 最大连续失败次数 |
| `FLUX_MCP_HEALTH_AUTO_DEACTIVATE` | true | 自动停用不可达源 |
| `FLUX_MCP_HEALTH_CONCURRENCY` | 5 | 最大并发探测数 |

### 8.2 Spring Profile

| Profile | 说明 |
|---------|------|
| `local` (默认) | 本地开发，连接 `192.168.9.148` |
| `dev` | 开发环境，连接 `localhost`，启用虚拟线程 |

---

## 9. REST API 参考

基础路径: `/api/v1/mcp`

### API 源管理

| 方法 | 端点 | 说明 |
|------|------|------|
| `GET` | `/sources` | 列出所有 API 源 |
| `POST` | `/sources` | 创建 API 源 |
| `GET` | `/sources/{id}` | 获取源详情 |
| `PUT` | `/sources/{id}` | 更新源 |
| `DELETE` | `/sources/{id}` | 删除源 |
| `PATCH` | `/sources/{id}/toggle-active` | 切换激活状态 |

### OpenAPI 解析

| 方法 | 端点 | 说明 |
|------|------|------|
| `POST` | `/sources/{id}/parse` | 解析 OpenAPI 规范为工具 |

请求体:
```json
{
  "sourceType": "SPEC",
  "content": "openapi: 3.0.0\n..."
}
```
或
```json
{
  "sourceType": "URL",
  "content": "https://example.com/openapi.json"
}
```

### 工具管理

| 方法 | 端点 | 说明 |
|------|------|------|
| `GET` | `/sources/{id}/tools` | 获取工具列表 |
| `PUT` | `/tools/{id}` | 更新工具映射 |
| `POST` | `/tools/{id}/test` | 测试调用工具 |

### 健康检查

| 方法 | 端点 | 说明 |
|------|------|------|
| `GET` | `/sources/health` | 获取所有源健康状态 |
| `POST` | `/sources/{id}/health-check` | 手动触发健康检查 |

### 连接信息

| 方法 | 端点 | 说明 |
|------|------|------|
| `GET` | `/connection-info` | 获取全局 MCP 连接信息 |
| `GET` | `/sources/{id}/connection-info` | 获取源级 MCP 连接信息 |

### MCP 协议端点

| 端点 | 说明 |
|------|------|
| `/api/v1/mcp/sources/{id}/mcp/message` | 源级 MCP Streamable HTTP 端点 |

---

## 10. 项目运行方式

### 10.1 Docker Compose（推荐）

```bash
git clone <repo-url>
cd flux-mcp
cp .env.example .env
docker compose up --build
```

启动三个服务：

| 服务 | 镜像 | 内部端口 | 外部端口 |
|------|------|----------|----------|
| PostgreSQL | pgvector/pgvector:pg16 | 5432 | 5442 |
| Redis | redis:7-alpine | 6379 | 6399 |
| Flux MCP | 本地构建 | 8092 | 8092 |

访问 `http://localhost:8092` 打开管理控制台。

### 10.2 源码构建

**前置条件**: Java 25、Maven 3.9+、PostgreSQL

```bash
# 构建 JAR
mvn -DskipTests package

# 运行
java -jar target/flux-mcp-2.0.0.jar --spring.profiles.active=dev
```

### 10.3 前端开发

```bash
cd frontend
npm install
npm run dev       # Vite 开发服务器 :5174，自动代理 /api 到后端
npm run build     # 生产构建
```

### 10.4 构建产物

- 后端 JAR: `target/flux-mcp-2.0.0.jar`
- 前端构建: `frontend/dist/`
- Docker 镜像: 多阶段构建，运行阶段仅含 JRE

---

## 11. 依赖关系图

### 11.1 后端核心依赖关系

```
                        FluxMcpApplication
                    (Spring Boot + Scheduling)
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
   FluxMcpController   SourceScopedMcp      McpHealthCheck
    (REST API)         RouterConfiguration   Scheduler
          │                   │                   │
          ▼                   ▼                   │
   FluxMcpAppService   SourceScopedMcpServer ◄────┘
    (Application)      Registry (核心)
          │                   │
    ┌─────┼─────┐       ┌─────┼──────┬──────────┐
    ▼     ▼     ▼       ▼     ▼      ▼          ▼
 ApiSource Tool  OpenApi Dynamic Mcp   WebFlux   McpSession
 Repo     Map   Parser  ApiTool Server Streamable Service
          Repo          Callback (Sync) Transport
    │     │     │       │                  │          │
    ▼     ▼     ▼       ▼                  │          ▼
  R2DBC  R2DBC Swagger  ToolInvocation     │    McpSessionStore
  Adapter Adapter Parser DomainService     │    (Memory/Redis)
                         │                 │
                         ▼                 │
                   OkHttpClient            │
                   Adapter                 │
                         │                 │
                         ▼                 ▼
                    外部 REST API    MCP 客户端
```

### 11.2 前端依赖关系

```
main.tsx
  ├── @tanstack/react-query (QueryClientProvider)
  ├── ThemeProvider.tsx
  │     └── @mui/material
  ├── notistack (SnackbarProvider)
  ├── App.tsx
  │     └── McpPage.tsx
  │           ├── api/mcpApi.ts
  │           │     ├── api/client.ts (axios)
  │           │     └── api/types.ts
  │           ├── @mui/material
  │           ├── @mui/icons-material
  │           ├── @tanstack/react-query
  │           ├── notistack
  │           └── @monaco-editor/react (lazy)
  └── styles/global.css
```

### 11.3 Maven 依赖

| GroupId | ArtifactId | 版本 | 说明 |
|---------|------------|------|------|
| org.springframework.boot | spring-boot-starter-webflux | 3.5.13 | WebFlux 响应式框架 |
| org.springframework.boot | spring-boot-starter-data-r2dbc | 3.5.13 | R2DBC 响应式数据库 |
| org.postgresql | r2dbc-postgresql | — | PostgreSQL R2DBC 驱动 |
| org.springframework.boot | spring-boot-starter-jdbc | 3.5.13 | JDBC (Flyway 使用) |
| org.springframework.boot | spring-boot-starter-validation | 3.5.13 | Bean Validation |
| org.springframework.ai | spring-ai-starter-mcp-server-webflux | 1.1.4 | Spring AI MCP 服务端 |
| io.swagger.parser.v3 | swagger-parser | 2.1.25 | OpenAPI 规范解析 |
| org.redisson | redisson-spring-boot-starter | 4.3.0 | Redis 客户端 |
| org.flywaydb | flyway-core | — | 数据库迁移 |
| org.flywaydb | flyway-database-postgresql | — | PostgreSQL Flyway 支持 |
| com.google.guava | guava | 33.4.0 | 通用工具库 |
| org.projectlombok | lombok | — | 代码简化 |

### 11.4 NPM 依赖

| 包名 | 版本 | 说明 |
|------|------|------|
| react / react-dom | 19 | UI 框架 |
| @mui/material | 6.4 | Material UI 组件库 |
| @mui/icons-material | 6.4 | Material 图标 |
| @emotion/react / @emotion/styled | 11.14 | CSS-in-JS |
| @tanstack/react-query | 5.65 | 服务端状态管理 |
| @monaco-editor/react | 4.7 | 代码编辑器 |
| axios | 1.7 | HTTP 客户端 |
| notistack | 3.0 | 消息通知 |
| typescript | 5.8 | 类型检查 |
| vite | 6 | 构建工具 |
| @vitejs/plugin-react | 4.3 | Vite React 插件 |

---

> **文档生成时间**: 2026-06-18  
> **项目版本**: Flux MCP 2.0.0
