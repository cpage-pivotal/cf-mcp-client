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
- **Backend**: Spring Boot 3.5.5, Spring AI 1.1.0-RC1, Java 21
- **Frontend**: Angular 20, Angular Material, TypeScript
- **Database**: PostgreSQL with pgvector extension for vector storage
- **AI Integration**: Model Context Protocol (MCP) clients, OpenAI models

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
- **HTTP SSE transport**: With SSL context configuration for legacy MCP servers
- **Streamable HTTP**: Modern protocol with improved performance and reliability
- Automatic tool discovery and registration from MCP servers
- Session-based conversation management with tool callback providers
- **Automatic Session Recovery**: Detects and recovers from MCP server restarts by automatically reconnecting with a fresh session when "Session not found" errors occur

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
- Current version: 2.1.1
- Spring AI BOM manages all AI-related dependencies
- Frontend uses Angular Material with custom theming
- Maven handles Node.js installation (v22.12.0) and frontend build integration
- Actuator endpoints exposed: `/actuator/health`, `/actuator/metrics`

#### Automatic Retry Configuration
The application includes automatic retry configuration for network exceptions introduced in Spring AI 1.1.0-RC1. This improves resilience when communicating with AI models and MCP servers:

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
The application includes automatic session recovery for MCP server connections. When an MCP server restarts, the previous session ID becomes invalid, causing "Session not found" errors. The session recovery mechanism automatically handles this:

**Implementation** (`SessionRecoveringToolCallbackProvider.java`):
- Wraps `SyncMcpToolCallbackProvider` to intercept tool invocations
- Detects session errors (404 HTTP status with "Session not found" message)
- Automatically creates a new MCP client connection with a fresh session
- Retries the failed tool invocation once with the new session
- Thread-safe client recreation using `AtomicReference`

**How it works**:
1. When a tool is invoked, the wrapper delegates to the underlying `SyncMcpToolCallbackProvider`
2. If a session error is detected, it logs a warning and attempts recovery
3. A new MCP client is created and initialized with a fresh session
4. The tool invocation is retried with the new client
5. If recovery fails, the original error is propagated

This feature ensures that MCP server restarts don't disrupt ongoing conversations, providing a seamless experience even when external tools are temporarily unavailable.

### Local Development Setup
For local development, you'll need PostgreSQL running:
```bash
# Using Docker (recommended)
docker run --name postgres-dev -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres

# Or use local PostgreSQL with database 'postgres', user 'postgres', password 'postgres'
```

### Development Patterns
- Service-oriented backend with clear separation of concerns
- Reactive streaming for chat responses using Spring WebFlux
- Session-based conversation state management
- Component-based frontend with Material Design
- TypeScript interfaces for type safety across frontend-backend communication