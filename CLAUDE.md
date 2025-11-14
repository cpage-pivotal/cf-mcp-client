# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Build Commands

### Full Application Build
```bash
mvn clean package
```
This builds both the Spring Boot backend and Angular frontend. The frontend-maven-plugin automatically installs Node.js, runs `npm ci`, builds the Angular app, and copies the built assets to `target/classes/static`.

### Backend Only
```bash
mvn clean compile
```

### Frontend Development
```bash
cd src/main/frontend
npm start
```
Runs Angular dev server on localhost:4200 with hot reload.

### Frontend Build Only
```bash
cd src/main/frontend
npm run build
```

### Running Tests
- **Backend**: `mvn test` (note: no test files currently exist)
- **Frontend**: `cd src/main/frontend && npm test`
- **Frontend Watch Mode**: `cd src/main/frontend && npm test -- --watch`

### Running the Application Locally
```bash
mvn spring-boot:run
```
Starts the Spring Boot application on port 8080. Frontend must be built first or served separately.

## Architecture Overview

### Technology Stack
- **Backend**: Spring Boot 3.5.5, Spring AI 1.1.0, Java 21
- **Frontend**: Angular 20, Angular Material, TypeScript
- **Database**: PostgreSQL with pgvector extension for vector storage
- **AI Integration**: Model Context Protocol (MCP) clients, Agent2Agent (A2A) protocol, OpenAI models

### Backend Architecture
The application is structured around several key service layers:

#### Core Services
- **ChatService** (`src/main/java/org/tanzu/mcpclient/chat/ChatService.java`): Central orchestrator for chat interactions, manages MCP client connections, tool callbacks, and streaming responses
- **ModelDiscoveryService** (`src/main/java/org/tanzu/mcpclient/model/ModelDiscoveryService.java`): Service discovery for AI models from multiple sources including Cloud Foundry GenAI services
- **DocumentService** (`src/main/java/org/tanzu/mcpclient/document/DocumentService.java`): Handles PDF document upload, processing, and vector storage integration
- **MetricsService** (`src/main/java/org/tanzu/mcpclient/metrics/MetricsService.java`): Provides platform metrics including connected models and agents
- **McpServerService** (`src/main/java/org/tanzu/mcpclient/mcp/McpServerService.java`): Manages individual MCP server connections with protocol support

#### MCP Integration
The application dynamically connects to Model Context Protocol servers through:
- **Dual Protocol Support**: Both SSE (Server-Sent Events) and Streamable HTTP protocols
- **HTTP SSE transport**: With SSL context configuration for legacy MCP servers (tag: `mcpSseURL`)
- **Streamable HTTP**: Modern protocol with improved performance and reliability (tag: `mcpStreamableURL`)
- Automatic tool discovery and registration from MCP servers
- Session-based conversation management with tool callback providers
- **Automatic Session Recovery**: Detects and recovers from MCP server restarts by automatically reconnecting with a fresh session when "Session not found" errors occur
- **Graceful Degradation**: If an MCP server is unavailable, it is skipped and the chat service continues with available servers

#### A2A Agent Integration
The application supports Agent2Agent (A2A) protocol for communication with independent AI agent systems:
- **Protocol Differences**: Unlike MCP servers (which provide tools your LLM invokes), A2A agents are independent AI systems that process messages and return complete responses
- **Agent Discovery**: Agents register via user-provided service URLs pointing to Agent Card (JSON descriptor at `/.well-known/agent.json`)
- **Service Binding**: Use Cloud Foundry user-provided services with tag `a2a`
- **UI Integration**: Agents panel (🤖) displays connected agents with health status, capabilities, and message interface
- **Capabilities**: Supports streaming, push notifications, and state history depending on agent implementation
- **Visual Attribution**: Agent responses displayed with distinct styling and clear agent identification

#### Vector Storage
Uses PostgreSQL with pgvector extension for:
- Document embeddings and semantic search
- Conversation memory persistence across sessions
- RAG (Retrieval Augmented Generation) capabilities

### Frontend Architecture
Angular 20 standalone components architecture:

#### Key Components
- **AppComponent**: Root component managing metrics polling and document selection state
- **ChatboxComponent**: Main chat interface with SSE streaming support
- **Chat/Memory/Document/AgentsPanelComponent**: Sidebar panels for different platform aspects
- **SidenavService**: Manages exclusive sidebar navigation state

#### Communication Patterns
- **SSE Streaming**: Real-time chat responses via Server-Sent Events
- **Metrics Polling**: 5-second interval updates of platform status
- **Document Selection**: Parent-child component communication for document context

### Cloud Foundry Integration
The application is designed for Cloud Foundry deployment with service binding support:
- **GenAI Services**: Automatic discovery of chat and embedding models
- **Vector Database**: PostgreSQL service binding for vector storage
- **MCP Agents**: User-provided service URLs for external tool integration
- **Session Management**: JDBC-based session storage for scaling

### Configuration Notes
- Default PostgreSQL connection: `localhost:5432/postgres` (user: postgres, pass: postgres)
- Session timeout: 1440 minutes (24 hours)
- Current version: 2.2.0
- Spring AI BOM manages all AI-related dependencies
- Frontend uses Angular Material with custom theming
- Maven handles Node.js installation (v24.9.0) and frontend build integration
- Actuator endpoints exposed: `/actuator/health`, `/actuator/metrics`

#### Automatic Retry Configuration
The application includes automatic retry configuration for network exceptions introduced in Spring AI 1.1.0. This improves resilience when communicating with AI models and MCP servers:

**Retry Configuration** (`application.yaml`):
- `spring.ai.retry.max-attempts: 3` - Maximum number of retry attempts for failed requests
- `spring.ai.retry.backoff.initial-interval: 2000` - Initial delay (2 seconds) before first retry
- `spring.ai.retry.backoff.multiplier: 2.0` - Exponential backoff multiplier (2x, 4x, etc.)
- `spring.ai.retry.backoff.max-interval: 10000` - Maximum delay cap (10 seconds)

**Handled Exceptions**:
- `TransientAiException` - HTTP status-based errors (e.g., 429 Too Many Requests, 503 Service Unavailable)
- `ResourceAccessException` - Pre-response network failures (e.g., connection timeouts, connection refused)
- `WebClientRequestException` - WebClient-specific request failures

This configuration automatically retries transient network issues without manual intervention, improving the application's stability in distributed environments.

#### MCP Session Recovery
The application includes automatic session recovery for MCP server connections. When an MCP server restarts, the previous session ID becomes invalid, causing various session-related errors. The session recovery mechanism automatically handles this:

**Implementation** (`SessionRecoveringToolCallbackProvider.java`):
- Wraps `SyncMcpToolCallbackProvider` to intercept tool invocations
- Detects session errors through multiple patterns (transport-specific):
  - **Streamable HTTP**: "Session not found" with HTTP 404
  - **SSE**: "Invalid session ID" with HTTP 400 and JSON-RPC error -32602
  - **Generic**: "MCP session with server terminated"
  - **Exception type**: `McpTransportSessionNotFoundException`
- Automatically creates a new MCP client connection with a fresh session
- Retries the failed tool invocation once with the new session
- Thread-safe client recreation using `AtomicReference`
- Debug logging to trace error detection and recovery

**How it works**:
1. When a tool is invoked, the wrapper delegates to the underlying `SyncMcpToolCallbackProvider`
2. If a session error is detected (by traversing the full exception chain), it logs a warning and attempts recovery
3. A new MCP client is created and initialized with a fresh session
4. The tool invocation is retried with the new client
5. If recovery fails, the original error is propagated

**Graceful Degradation** (`McpToolCallbackCacheService.java`):
- During cache initialization/refresh, if an MCP server is unavailable, it is skipped rather than failing the entire chat service
- Returns `Optional.empty()` for unavailable servers, allowing other healthy servers to be used
- Logs warnings for unavailable servers with debug-level stack traces
- Chat requests can still proceed with tools from available MCP servers

This feature ensures that MCP server restarts and temporary unavailability don't disrupt ongoing conversations, providing a seamless experience even when external tools are temporarily down.

### Local Development Setup
For local development, you'll need PostgreSQL running:
```bash
# Using Docker (recommended)
docker run --name postgres-dev -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres

# Or use local PostgreSQL with database 'postgres', user 'postgres', password 'postgres'
```

### Cloud Foundry Deployment
The application is designed for Cloud Foundry deployment with service bindings:

#### Service Binding Tags
- **GenAI Chat Models**: Automatically discovered from GenAI tile services
- **GenAI Embedding Models**: For vector database operations
- **Vector Database**: PostgreSQL service with pgvector extension
- **MCP Servers**: User-provided services with tags:
  - `mcpSseURL` - SSE-based MCP servers
  - `mcpStreamableURL` - Streamable HTTP-based MCP servers
- **A2A Agents**: User-provided services with tag `a2a` pointing to `/.well-known/agent.json`

#### Example Deployment Commands
```bash
# Deploy application
cf push

# Bind GenAI services
cf bind-service ai-tool-chat chat-llm
cf bind-service ai-tool-chat embeddings-llm
cf bind-service ai-tool-chat vector-db

# Bind MCP server (SSE)
cf cups mcp-server-sse -p '{"uri":"https://mcp-server.example.com"}' -t "mcpSseURL"
cf bind-service ai-tool-chat mcp-server-sse

# Bind A2A agent
cf cups a2a-agent -p '{"uri":"https://agent.example.com/.well-known/agent.json"}' -t "a2a"
cf bind-service ai-tool-chat a2a-agent

# Restart to apply bindings
cf restart ai-tool-chat
```

### Development Patterns
- Service-oriented backend with clear separation of concerns
- Reactive streaming for chat responses using Spring WebFlux
- Session-based conversation state management
- Component-based frontend with Material Design
- TypeScript interfaces for type safety across frontend-backend communication