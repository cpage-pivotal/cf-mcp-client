# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is Tanzu Platform Chat (cf-mcp-client), a Spring Boot 3.5 chatbot application designed for Cloud Foundry deployment. It integrates with multiple AI services including LLMs, Vector Databases, and Model Context Protocol (MCP) agents. The application uses Spring AI framework and includes both a Java backend and Angular frontend.

## Build and Development Commands

### Java Backend
- **Build**: `mvn clean package` - Compiles both backend and frontend, copying Angular dist to Spring static resources
- **Run locally**: `mvn spring-boot:run`
- **Test**: `mvn test`

### Angular Frontend (in src/main/frontend/)
- **Install dependencies**: `npm ci`
- **Development server**: `npm start` (runs on ng serve)
- **Build**: `npm run build`
- **Test**: `npm test`
- **Watch mode**: `npm run watch`

### Cloud Foundry Deployment
- **Deploy**: `cf push`
- **Bind services**: `cf bind-service ai-tool-chat <service-name>`
- **Restart**: `cf restart ai-tool-chat`

## Architecture

### Dual Chat System
The application supports two different chat interfaces:
1. **Standard Chat** (`/chat`) - Uses Spring AI with configurable LLM backends
2. **Zantu Chat** (`/zantu/chat`) - RabbitMQ-based integration with external Zantu service

### Key Components
- **ChatController**: SSE-based streaming chat with Spring AI integration
- **ZantuChatController**: Event-driven chat using RabbitMQ messaging
- **Document Service**: PDF upload and vector store integration for RAG
- **Memory Service**: Persistent conversation memory using vector stores
- **MCP Client**: Integration with Model Context Protocol agents

### Configuration-Driven Services
Services are conditionally enabled based on Cloud Foundry bindings:
- LLM services auto-detected from CF service bindings
- Vector store enabled when both embedding model and Postgres DB are bound
- MCP agents discovered from user-provided services with `mcpServiceURL`
- Session storage switches between in-memory and JDBC based on DB availability

### Frontend Structure
Angular application with Material Design components:
- **Chat Panel**: Main chat interface
- **Document Panel**: File upload for RAG
- **Memory Panel**: Conversation history
- **Agents Panel**: MCP agent status
- **Tools Modal**: Available tool display

### Service Binding Pattern
The application heavily uses Spring Cloud Foundry's auto-configuration to detect and configure services based on CF bindings, making it deployment-environment aware without code changes.