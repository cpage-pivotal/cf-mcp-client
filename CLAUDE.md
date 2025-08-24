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
- **Backend**: `mvn test`
- **Frontend**: `cd src/main/frontend && npm test`
- **Single Test Class**: `mvn test -Dtest=ClassName`
- **Single Test Method**: `mvn test -Dtest=ClassName#methodName`

### Running the Application Locally
```bash
mvn spring-boot:run
```
Starts the Spring Boot application on port 8080. Frontend must be built first or served separately.

## Architecture Overview

### Technology Stack
- **Backend**: Spring Boot 3.5.3, Spring AI 1.0.1, Java 21
- **Frontend**: Angular 20, Angular Material, TypeScript
- **Database**: PostgreSQL with pgvector extension for vector storage
- **AI Integration**: Model Context Protocol (MCP) clients, OpenAI models
- **Message Queue**: RabbitMQ (optional, graceful degradation if unavailable)
- **Session Management**: JDBC-based or in-memory sessions

### Backend Architecture
The application is structured around several key service layers:

#### Core Services
- **ChatService** (`src/main/java/org/tanzu/mcpclient/chat/ChatService.java`): Central orchestrator for chat interactions, manages MCP client connections, tool callbacks, and streaming responses
- **ModelDiscoveryService** (`src/main/java/org/tanzu/mcpclient/model/ModelDiscoveryService.java`): Service discovery for chat and embedding models via CF service bindings and property-based providers  
- **DocumentService** (`src/main/java/org/tanzu/mcpclient/document/DocumentService.java`): Handles PDF document upload, processing, and vector storage integration
- **MetricsService** (`src/main/java/org/tanzu/mcpclient/metrics/MetricsService.java`): Event-driven service that collects platform metrics including connected models, MCP servers, and agent status
- **McpDiscoveryService** (`src/main/java/org/tanzu/mcpclient/mcp/McpDiscoveryService.java`): Discovers and manages MCP server connections

#### MCP Integration
The application dynamically connects to Model Context Protocol servers through:
- HTTP SSE transport with SSL context configuration
- Automatic tool discovery and registration from MCP servers
- Session-based conversation management with tool callback providers

#### Vector Storage
Uses PostgreSQL with pgvector extension for:
- Document embeddings and semantic search
- Conversation memory persistence across sessions
- RAG (Retrieval Augmented Generation) capabilities

### Frontend Architecture
Angular 20 standalone components architecture:

#### Key Components
- **AppComponent** (`src/main/frontend/src/app/app.component.ts`): Root component managing metrics polling (5-second interval) and document selection state using Angular signals
- **ChatboxComponent** (`src/main/frontend/src/chatbox/chatbox.component.ts`): Main chat interface with SSE streaming support, markdown rendering, and think-tag parsing
- **Panel Components**: Sidebar panels for different platform aspects:
  - **ChatPanelComponent**: Chat history and conversation management
  - **MemoryPanelComponent**: Vector store and memory configuration
  - **DocumentPanelComponent**: PDF document upload and management
  - **AgentsPanelComponent**: MCP server status and agent selection
- **SidenavService** (`src/main/frontend/src/services/sidenav.service.ts`): Manages exclusive sidebar navigation state ensuring only one panel is open at a time

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
- Default PostgreSQL connection: `localhost:5432/postgres` (user: postgres, password: postgres)
- Default RabbitMQ connection: `localhost:5672` (guest/guest)
- Session timeout: 1440 minutes (24 hours)
- Application gracefully starts without database or RabbitMQ if unavailable
- Spring AI BOM manages all AI-related dependencies
- Frontend uses Angular Material with custom theming (`src/main/frontend/src/custom-theme.scss`)
- Maven handles Node.js installation (v22.12.0) and frontend build integration
- Frontend build output copied to `target/classes/static` for JAR packaging

### Development Patterns
- Service-oriented backend with clear separation of concerns
- Reactive streaming for chat responses using Spring WebFlux
- Event-driven architecture for configuration changes and metrics collection
- Session-based conversation state management with optional vector store persistence
- Component-based frontend with Material Design and Angular signals for reactive state
- TypeScript interfaces for type safety across frontend-backend communication
- Graceful degradation when external services (database, message queue) are unavailable

### Cloud Foundry Deployment
The application is designed specifically for Cloud Foundry deployment with:
- Service binding auto-discovery for GenAI models and vector databases
- User-provided services for MCP agent integration
- Support for multiple service binding patterns (Tanzu GenAI, standard OpenAI)
- Manifest-driven deployment with `cf push`