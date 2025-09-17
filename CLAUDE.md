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
- **Backend**: Spring Boot 3.5.5, Spring AI 1.1.0-M1, Java 21
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
- Current version: 1.6.0
- Spring AI BOM manages all AI-related dependencies
- Frontend uses Angular Material with custom theming
- Maven handles Node.js installation (v22.12.0) and frontend build integration
- Actuator endpoints exposed: `/actuator/health`, `/actuator/metrics`

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