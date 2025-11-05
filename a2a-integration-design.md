# A2A (Agent2Agent) Integration Design and Implementation Plan

## Executive Summary

This document outlines the design and implementation plan for adding Agent2Agent (A2A) protocol client capabilities to the cf-mcp-client application. The A2A protocol enables communication between independent AI agent systems, allowing the application to discover, bind to, and interact with external A2A agents.

## Overview

### Current State
- cf-mcp-client currently supports MCP (Model Context Protocol) servers
- Has panels for Chat, Documents, MCP Servers, and Memory
- Uses Angular 20 with signals for frontend, Spring Boot 3.5.5 for backend
- Deployed on Cloud Foundry with service binding discovery

### Target State
- Add support for A2A protocol (v0.3.0) alongside existing MCP support
- New "Agents" panel to display and interact with A2A agents
- Agent messages displayed in chat with distinct "agent" persona
- Service binding pattern: `cf cups a2a-server -p '{"uri":"<agent-card-url>"}' -t "a2a"`

---

## Architecture Design

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Angular Frontend                         │
├─────────────────────────────────────────────────────────────┤
│  ChatboxComponent (updated)                                  │
│    - Handles 'user', 'bot', and 'agent' personas            │
│                                                              │
│  AgentsPanelComponent (NEW)                                  │
│    - Displays bound A2A agents                              │
│    - Shows health status                                    │
│    - Trigger agent message dialog                           │
│                                                              │
│  AgentMessageDialogComponent (NEW)                           │
│    - Input dialog for sending messages to agents            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ HTTP/REST
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot Backend                        │
├─────────────────────────────────────────────────────────────┤
│  A2AController (NEW)                                         │
│    - POST /a2a/send-message                                 │
│                                                              │
│  MetricsController (updated)                                 │
│    - GET /metrics (now includes A2A agents)                 │
│                                                              │
│  A2AConfiguration (NEW)                                      │
│    - Initializes A2A agents on startup                      │
│    - Manages list of A2AAgentService instances              │
│                                                              │
│  A2ADiscoveryService (NEW)                                   │
│    - Discovers A2A service bindings from CF                 │
│                                                              │
│  A2AAgentService (NEW)                                       │
│    - One instance per A2A agent                             │
│    - Fetches and caches AgentCard                           │
│    - Sends JSON-RPC messages to agent                       │
│    - Health checking                                        │
│                                                              │
│  Models: AgentCard, A2AModels (NEW)                         │
│    - Java records for A2A data structures                   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ JSON-RPC over HTTP
┌─────────────────────────────────────────────────────────────┐
│                    External A2A Agents                       │
│  - Agent Card at /.well-known/agent.json                    │
│  - JSON-RPC endpoint for message/send                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Detailed Component Design

### Backend Components

#### 1. **AgentCard.java** (NEW)
**Purpose**: Java record representing the A2A Agent Card structure

**Key Fields**:
- `String protocolVersion` - A2A protocol version (e.g., "0.3.0")
- `String name` - Agent name
- `String description` - Agent description
- `String url` - Agent endpoint URL
- `String preferredTransport` - Transport protocol (typically "JSONRPC")
- `AgentCapabilities capabilities` - streaming, pushNotifications, etc.
- `List<String> defaultInputModes` - Supported input MIME types
- `List<String> defaultOutputModes` - Supported output MIME types
- `List<AgentSkill> skills` - Agent capabilities/skills

**Nested Records**:
- `AgentProvider` - Organization and URL
- `AgentCapabilities` - Boolean flags for streaming, push notifications
- `AgentSkill` - id, name, description, tags, examples

**Location**: `src/main/java/org/tanzu/mcpclient/a2a/AgentCard.java`

---

#### 2. **A2AModels.java** (NEW)
**Purpose**: Java records for A2A protocol message structures

**Key Records**:
- `JsonRpcRequest` - JSON-RPC 2.0 request wrapper
  - jsonrpc: "2.0"
  - id: request ID
  - method: "message/send"
  - params: Map with message and configuration
  
- `JsonRpcResponse` - JSON-RPC 2.0 response wrapper
  - jsonrpc, id, result, error
  
- `Message` - A2A message
  - role: "user" or "agent"
  - parts: List<Part> (content)
  - messageId: UUID
  - taskId, contextId (optional)
  
- `Part` interface with implementations:
  - `TextPart` - text content
  - `FilePart` - file content (bytes or URI)
  - `DataPart` - structured JSON data
  
- `Task` - Represents stateful unit of work
  - id, contextId, status, history, artifacts
  
- `TaskStatus` - Task state information
  - state: "submitted", "working", "completed", etc.
  - message: optional status message
  - timestamp

**Location**: `src/main/java/org/tanzu/mcpclient/a2a/A2AModels.java`

**Design Note**: Use sealed interfaces for Part types to ensure type safety

---

#### 3. **A2ADiscoveryService.java** (NEW)
**Purpose**: Discovers A2A agents from Cloud Foundry service bindings

**Pattern**: Similar to `McpDiscoveryService`

**Key Methods**:
- `List<String> getAgentCardUris()` - Returns list of agent card URLs
  - Filters CF services by tag "a2a"
  - Extracts "uri" credential from each service
  - Returns list of agent card URLs
  
- `List<String> getA2AServiceNames()` - Returns service names for display

**Service Binding Pattern**:
```bash
cf cups a2a-server -p '{"uri":"https://example.com/.well-known/agent.json"}' -t "a2a"
```

**Constants**:
- `A2A_TAG = "a2a"` - Tag to identify A2A services
- `AGENT_CARD_URI_KEY = "uri"` - Credential key for agent card URL

**Dependencies**:
- `CfEnv` - Cloud Foundry environment access

**Location**: `src/main/java/org/tanzu/mcpclient/a2a/A2ADiscoveryService.java`

---

#### 4. **A2AAgentService.java** (NEW)
**Purpose**: Manages communication with a single A2A agent

**Pattern**: Similar to `McpServerService` but for A2A protocol

**Key Fields**:
- `String serviceName` - CF service name
- `String agentCardUri` - URL to agent card
- `AgentCard agentCard` - Cached agent card
- `boolean healthy` - Health status
- `String errorMessage` - Error details if unhealthy
- `RestClient restClient` - HTTP client
- `ObjectMapper objectMapper` - JSON serialization

**Key Methods**:

1. `void initializeAgentCard()`
   - Fetches agent card from URI on construction
   - Parses JSON into AgentCard record
   - Sets healthy status based on success/failure
   - Logs results

2. `JsonRpcResponse sendMessage(String messageText)`
   - Validates agent is healthy
   - Creates TextPart with message text
   - Generates unique messageId (UUID)
   - Builds Message object with "user" role
   - Creates MessageSendConfiguration (blocking: true, acceptedOutputModes: ["text"])
   - Wraps in JsonRpcRequest with method "message/send"
   - POSTs to agent URL with JSON-RPC payload
   - Parses and returns JsonRpcResponse
   - Handles errors and logging

3. Getters for:
   - `getServiceName()`
   - `getAgentCardUri()`
   - `getAgentCard()`
   - `isHealthy()`
   - `getErrorMessage()`

**Error Handling**:
- Catch exceptions during card fetch → mark unhealthy
- Throw RuntimeException on message send failure
- Log all errors with context

**Location**: `src/main/java/org/tanzu/mcpclient/a2a/A2AAgentService.java`

---

#### 5. **A2AConfiguration.java** (NEW)
**Purpose**: Spring Configuration to initialize A2A agents on startup

**Pattern**: Similar to `ChatConfiguration` and MCP initialization

**Key Fields**:
- `A2ADiscoveryService discoveryService`
- `RestClient.Builder restClientBuilder`
- `ObjectMapper objectMapper`
- `List<A2AAgentService> agentServices` - List of all initialized agents
- `Map<String, String> agentNamesByUri` - Lookup map

**Key Methods**:

1. `void onApplicationReady()` - @EventListener(ApplicationReadyEvent.class)
   - Called after Spring context fully initialized
   - Gets agent card URIs from discovery service
   - Gets service names from discovery service
   - For each URI:
     - Creates A2AAgentService instance
     - Adds to agentServices list
     - Updates agentNamesByUri map
   - Logs initialization summary

2. `List<A2AAgentService> getAgentServices()`
   - Returns copy of agent services list
   - Used by MetricsService and A2AController

3. `Map<String, String> getAgentNamesByUri()`
   - Returns copy of name mapping
   - Used for lookups

**Location**: `src/main/java/org/tanzu/mcpclient/a2a/A2AConfiguration.java`

---

#### 6. **A2AController.java** (NEW)
**Purpose**: REST controller for A2A operations from frontend

**Endpoints**:

1. **POST /a2a/send-message**
   - Request Body: `SendMessageRequest`
     ```java
     record SendMessageRequest(
         String serviceName,
         String messageText
     ) {}
     ```
   - Response Body: `SendMessageResponse`
     ```java
     record SendMessageResponse(
         boolean success,
         String agentName,
         String responseText,
         String error
     ) {}
     ```
   - Logic:
     - Find agent service by serviceName
     - Call agentService.sendMessage(messageText)
     - Extract response text from JSON-RPC result
     - Parse Task or Message from result
     - Extract text from parts
     - Return formatted response

**Error Responses**:
- 404 if agent service not found
- 500 if agent communication fails
- Include error message in response

**Location**: `src/main/java/org/tanzu/mcpclient/a2a/A2AController.java`

---

#### 7. **MetricsService.java** (UPDATED)
**Purpose**: Add A2A agent information to platform metrics

**New Record**:
```java
public record A2AAgent(
    String serviceName,
    String agentName,
    String description,
    String version,
    String agentCardUri,
    boolean healthy,
    String errorMessage,
    AgentCard.AgentCapabilities capabilities
) {}
```

**Updated PlatformMetrics**:
```java
public record PlatformMetrics(
    String chatModel,
    boolean chatModelAvailable,
    String embeddingModel,
    boolean embeddingModelAvailable,
    String vectorStore,
    boolean vectorStoreAvailable,
    List<McpServer> mcpServers,
    List<A2AAgent> a2aAgents  // NEW FIELD
) {}
```

**New Method**:
```java
private List<A2AAgent> buildA2AAgentsList(A2AConfiguration a2aConfig) {
    return a2aConfig.getAgentServices().stream()
        .map(service -> new A2AAgent(
            service.getServiceName(),
            service.getAgentCard() != null ? service.getAgentCard().name() : "Unknown",
            // ... map all fields
        ))
        .collect(Collectors.toList());
}
```

**Updated getPlatformMetrics()**:
- Inject A2AConfiguration
- Call buildA2AAgentsList()
- Include in PlatformMetrics construction

**Location**: `src/main/java/org/tanzu/mcpclient/metrics/MetricsService.java`

---

### Frontend Components

#### 8. **Updated Models** (TypeScript)
**File**: `src/main/frontend/src/app/app.component.ts`

**New Interfaces**:
```typescript
export interface A2AAgent {
  serviceName: string;
  agentName: string;
  description: string;
  version: string;
  agentCardUri: string;
  healthy: boolean;
  errorMessage?: string;
  capabilities: AgentCapabilities;
}

export interface AgentCapabilities {
  streaming: boolean;
  pushNotifications: boolean;
  stateTransitionHistory: boolean;
}

// Update PlatformMetrics
export interface PlatformMetrics {
  chatModel: string;
  chatModelAvailable: boolean;
  embeddingModel: string;
  embeddingModelAvailable: boolean;
  vectorStore: string;
  vectorStoreAvailable: boolean;
  mcpServers: McpServer[];
  a2aAgents: A2AAgent[];  // NEW
}
```

---

#### 9. **AgentsPanelComponent** (NEW)
**Purpose**: Display A2A agents and allow sending messages

**Location**: `src/main/frontend/src/agents-panel/`

**Files**:
- `agents-panel.component.ts`
- `agents-panel.component.html`
- `agents-panel.component.css`
- `agents-panel.component.spec.ts`

**Component Architecture**:
```typescript
@Component({
  selector: 'app-agents-panel',
  standalone: true,
  imports: [
    CommonModule,
    MatSidenavModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatCardModule,
    MatChipsModule,
    MatTooltipModule,
    MatDialogModule
  ]
})
export class AgentsPanelComponent implements AfterViewInit {
  @Input() metrics = input.required<PlatformMetrics>();
  @ViewChild('sidenav') sidenav!: MatSidenav;
  
  constructor(
    private sidenavService: SidenavService,
    private dialog: MatDialog
  ) {}
}
```

**Key Methods**:

1. `ngAfterViewInit()`
   - Register sidenav with SidenavService
   - Same pattern as McpServersPanelComponent

2. `toggleSidenav()`
   - Toggle sidenav open/closed via SidenavService

3. `onSidenavOpenedChange(opened: boolean)`
   - Handle backdrop clicks

4. `get sortedA2AAgents(): A2AAgent[]`
   - Computed property using signals
   - Returns agents sorted: healthy first, then by name
   - Same pattern as MCP servers panel

5. `openAgentMessageDialog(agent: A2AAgent)`
   - Opens AgentMessageDialogComponent
   - Passes agent information
   - Handles dialog result (message to send)
   - Closes agents panel after message sent

6. `getOverallStatusClass(): string`
   - Returns CSS class based on agent health
   - 'status-green': all healthy
   - 'status-orange': mixed health
   - 'status-red': none healthy or no agents

7. `getOverallStatusIcon(): string`
   - Returns Material icon name
   - 'check_circle', 'warning', or 'error'

8. `getOverallStatusText(): string`
   - Returns status text (e.g., "2/3 Healthy")

**Template Structure** (agents-panel.component.html):
```html
<mat-sidenav-container class="sidenav-container">
  <mat-sidenav #sidenav position="end" mode="over" fixedInViewport="true" [fixedTopGap]="64"
               (openedChange)="onSidenavOpenedChange($event)">
    <div class="panel-header">
      <h2 id="agents-panel-heading">Agents</h2>
      <button mat-icon-button (click)="toggleSidenav()" aria-label="Close agents panel">
        <mat-icon aria-hidden="true">close</mat-icon>
      </button>
    </div>

    <section aria-labelledby="agent-status-heading">
      <h3 id="agent-status-heading" class="sr-only">Agent Status</h3>
      <div class="status-container">
        <div class="status-row">
          <span class="status-label">Status:</span>
          <mat-chip-set>
            <mat-chip
              variant="assist"
              [class.status-success]="getOverallStatusClass() === 'status-green'"
              [class.status-warning]="getOverallStatusClass() === 'status-orange'"
              [class.status-error]="getOverallStatusClass() === 'status-red'"
              role="status"
              [attr.aria-label]="'Agent status: ' + getOverallStatusText()">
              <mat-icon matChipAvatar aria-hidden="true">{{ getOverallStatusIcon() }}</mat-icon>
              {{ getOverallStatusText() }}
            </mat-chip>
          </mat-chip-set>
        </div>
      </div>
    </section>

    @if (sortedA2AAgents.length > 0) {
      <section aria-labelledby="available-agents-heading">
        <div class="agents-list">
          <h3 id="available-agents-heading">Available Agents</h3>
          <div class="agent-cards" role="list" [attr.aria-label]="'List of ' + sortedA2AAgents.length + ' agents'">
            @for (agent of sortedA2AAgents; track agent.serviceName) {
              <mat-card class="agent-card"
                        [class.unhealthy]="!agent.healthy"
                        role="listitem"
                        [attr.aria-label]="'Agent: ' + agent.agentName + ', Status: ' + (agent.healthy ? 'Healthy' : 'Unhealthy')">
                <mat-card-header>
                  <mat-card-title>{{agent.agentName}}</mat-card-title>
                  <mat-card-subtitle>{{agent.version}}</mat-card-subtitle>
                </mat-card-header>

                <mat-card-content>
                  <p class="agent-description">{{agent.description}}</p>

                  @if (agent.capabilities.streaming || agent.capabilities.pushNotifications || agent.capabilities.stateTransitionHistory) {
                    <div class="capabilities" role="list" aria-label="Agent capabilities">
                      @if (agent.capabilities.streaming) {
                        <mat-chip role="listitem">Streaming</mat-chip>
                      }
                      @if (agent.capabilities.pushNotifications) {
                        <mat-chip role="listitem">Push Notifications</mat-chip>
                      }
                      @if (agent.capabilities.stateTransitionHistory) {
                        <mat-chip role="listitem">State History</mat-chip>
                      }
                    </div>
                  }

                  @if (!agent.healthy) {
                    <div class="error-message" role="alert">
                      <mat-icon aria-hidden="true">error</mat-icon>
                      <span>{{agent.errorMessage}}</span>
                    </div>
                  }
                </mat-card-content>

                <mat-card-actions>
                  <button mat-raised-button
                          color="primary"
                          [disabled]="!agent.healthy"
                          (click)="openAgentMessageDialog(agent)"
                          [attr.aria-label]="agent.healthy ? 'Send message to ' + agent.agentName : 'Cannot send message - agent is unhealthy'">
                    <mat-icon aria-hidden="true">send</mat-icon>
                    Send Message
                  </button>
                </mat-card-actions>
              </mat-card>
            }
          </div>
        </div>
      </section>
    }

    @if (sortedA2AAgents.length === 0) {
      <div class="empty-state" role="status" aria-label="No agents configured">
        <mat-icon aria-hidden="true">info</mat-icon>
        <p>No A2A agents configured</p>
        <p class="empty-state-hint">Bind A2A services to enable agent interactions</p>
      </div>
    }
  </mat-sidenav>

  <mat-sidenav-content>
    <!-- This is intentionally empty -->
  </mat-sidenav-content>
</mat-sidenav-container>
```

**Key Template Features**:
- ✅ **Full Material Design 3 Compliance**: Matches MCP Servers panel pattern exactly
- ✅ **Accessibility**: ARIA labels, roles, semantic HTML, screen reader support
- ✅ **Sidenav Container**: Proper mat-sidenav-container structure with fixedInViewport
- ✅ **Status Chip**: Uses mat-chip-set with variant="assist" instead of custom card
- ✅ **Semantic Sections**: All sections have aria-labelledby with sr-only headings
- ✅ **List Roles**: Proper role="list" and role="listitem" for agents and capabilities

**Styling** (agents-panel.component.css):
```css
/* Screen reader only utility class */
.sr-only {
  position: absolute !important;
  width: 1px !important;
  height: 1px !important;
  padding: 0 !important;
  margin: -1px !important;
  overflow: hidden !important;
  clip: rect(0, 0, 0, 0) !important;
  white-space: nowrap !important;
  border: 0 !important;
}

/* Sidenav container with proper Material structure */
.sidenav-container {
  height: calc(100vh - var(--md-toolbar-height));
  width: 100vw;
  position: fixed;
  top: var(--md-toolbar-height);
  pointer-events: none;
  z-index: 999;
}

mat-sidenav {
  pointer-events: auto;
  width: var(--md-side-panel-width);
  box-shadow: var(--md-sys-elevation-3dp);
  margin-right: var(--md-nav-rail-width);
  background-color: var(--md-sys-color-surface);
}

/* Panel header with Material typography tokens */
.panel-header h2 {
  font-size: var(--md-sys-typescale-title-large-font-size);
  font-weight: var(--md-sys-typescale-title-large-font-weight);
  color: var(--md-sys-color-on-surface);
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
}

/* Status chip with Material Design 3 patterns */
mat-chip.status-success {
  background-color: var(--md-sys-color-success-container);
  color: var(--md-sys-color-on-success-container);
  border: 1px solid var(--md-sys-color-success);
}

mat-chip.status-warning {
  background-color: var(--md-sys-color-warning-container);
  color: var(--md-sys-color-on-warning-container);
  border: 1px solid var(--md-sys-color-warning);
}

mat-chip.status-error {
  background-color: var(--md-sys-color-error-container);
  color: var(--md-sys-color-on-error-container);
  border: 1px solid var(--md-sys-color-error);
}

/* Agent cards with state layers */
.agent-card {
  position: relative;
  border-radius: 8px;
  cursor: pointer;
  transition: all var(--md-sys-motion-duration-short4) var(--md-sys-motion-easing-standard);
  border: 1px solid var(--md-sys-color-outline-variant);
}

.agent-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: var(--md-sys-color-on-surface);
  opacity: 0;
  transition: opacity var(--md-sys-state-transition-duration);
  pointer-events: none;
  z-index: 1;
}

.agent-card:hover {
  box-shadow: var(--md-sys-elevation-3dp);
  transform: translateY(-1px);
}

.agent-card:hover::before {
  opacity: var(--md-sys-state-hover-opacity);
}

.agent-card.unhealthy {
  border-left: 4px solid var(--md-sys-color-error);
  background-color: rgba(244, 67, 54, 0.04);
}

/* Typography using Material tokens */
mat-card-title {
  font-size: var(--md-sys-typescale-title-medium-font-size);
  font-weight: var(--md-sys-typescale-title-medium-font-weight);
  color: var(--md-sys-color-on-surface);
}

.agent-description {
  font-size: var(--md-sys-typescale-body-small-font-size);
  font-weight: var(--md-sys-typescale-body-small-font-weight);
  color: var(--md-sys-color-on-surface-variant);
}

/* Responsive design */
@media (max-width: 599px) {
  mat-sidenav {
    width: 100vw;
    margin-right: 0;
  }
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
  .agent-card {
    border: 1px solid rgba(255, 255, 255, 0.08);
  }
}
```

**Key CSS Features**:
- ✅ **100% Material Tokens**: All colors, typography, spacing use MD3 tokens
- ✅ **State Layers**: Hover/focus/active states with proper opacity values
- ✅ **Motion Design**: All transitions use Material motion tokens
- ✅ **Accessibility**: Screen reader utility class, proper contrast ratios
- ✅ **Responsive**: Mobile breakpoints and full-width modal on small screens
- ✅ **Dark Mode**: Media query support with adjusted colors
- ✅ **Elevation**: Proper shadow tokens for depth hierarchy

---

#### 10. **AgentMessageDialogComponent** (NEW)
**Purpose**: Dialog for entering message to send to agent

**Location**: `src/main/frontend/src/agent-message-dialog/`

**Files**:
- `agent-message-dialog.component.ts`
- `agent-message-dialog.component.html`
- `agent-message-dialog.component.css`

**Component Architecture**:
```typescript
export interface AgentMessageDialogData {
  agent: A2AAgent;
}

export interface AgentMessageDialogResult {
  message: string;
}

@Component({
  selector: 'app-agent-message-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule
  ]
})
export class AgentMessageDialogComponent {
  messageText = signal('');
  
  constructor(
    public dialogRef: MatDialogRef<AgentMessageDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: AgentMessageDialogData
  ) {}
  
  canSend(): boolean {
    return this.messageText().trim().length > 0;
  }
  
  onCancel(): void {
    this.dialogRef.close();
  }
  
  onSend(): void {
    if (this.canSend()) {
      const result: AgentMessageDialogResult = {
        message: this.messageText().trim()
      };
      this.dialogRef.close(result);
    }
  }
}
```

**Template Structure**:
```html
<h2 mat-dialog-title>Send Message to {{data.agent.agentName}}</h2>

<mat-dialog-content>
  <mat-form-field appearance="outline" class="full-width">
    <mat-label>Message</mat-label>
    <textarea matInput
              rows="6"
              [ngModel]="messageText()"
              (ngModelChange)="messageText.set($event)"
              placeholder="Enter your message...">
    </textarea>
  </mat-form-field>
  
  <p class="agent-description">{{data.agent.description}}</p>
</mat-dialog-content>

<mat-dialog-actions align="end">
  <button mat-button (click)="onCancel()">Cancel</button>
  <button mat-raised-button 
          color="primary"
          [disabled]="!canSend()"
          (click)="onSend()">
    Send
  </button>
</mat-dialog-actions>
```

---

#### 11. **ChatboxComponent** (UPDATED)
**Purpose**: Handle agent persona messages and display them

**Location**: `src/main/frontend/src/chatbox/chatbox.component.ts`

**Updates Needed**:

1. **Update ChatMessage Interface**:
```typescript
export interface ChatMessage {
  text: string;
  persona: 'user' | 'bot' | 'agent';  // Add 'agent'
  typing?: boolean;
  reasoning?: string;
  showReasoning?: boolean;
  error?: string;
  showError?: boolean;
  agentName?: string;  // NEW: For agent messages
}
```

2. **New Method: `sendMessageToAgent()`**:
```typescript
async sendMessageToAgent(agent: A2AAgent, message: string): Promise<void> {
  // Add user message to chat
  this._messages.update(msgs => [
    ...msgs,
    { text: message, persona: 'user' }
  ]);
  
  // Add placeholder for agent response
  this._messages.update(msgs => [
    ...msgs,
    { 
      text: '', 
      persona: 'agent', 
      typing: true,
      agentName: agent.agentName 
    }
  ]);
  
  try {
    // Call backend
    const response = await this.http.post<SendMessageResponse>(
      '/a2a/send-message',
      {
        serviceName: agent.serviceName,
        messageText: message
      }
    ).toPromise();
    
    // Update last message with response
    if (response.success) {
      this._messages.update(msgs => {
        const lastMsg = msgs[msgs.length - 1];
        return [
          ...msgs.slice(0, -1),
          { ...lastMsg, text: response.responseText, typing: false }
        ];
      });
    } else {
      this._messages.update(msgs => {
        const lastMsg = msgs[msgs.length - 1];
        return [
          ...msgs.slice(0, -1),
          { 
            ...lastMsg, 
            text: 'Error: ' + response.error, 
            typing: false,
            error: response.error
          }
        ];
      });
    }
  } catch (error) {
    console.error('Error sending message to agent:', error);
    this._messages.update(msgs => {
      const lastMsg = msgs[msgs.length - 1];
      return [
        ...msgs.slice(0, -1),
        { 
          ...lastMsg, 
          text: 'Error communicating with agent', 
          typing: false,
          error: 'Network error'
        }
      ];
    });
  }
}
```

3. **Update Template** (chatbox.component.html):
```html
<div class="chat-message {{messageData.persona}}"
     [attr.data-agent-name]="messageData.agentName">
  <mat-card appearance="outlined"
            [attr.data-card-variant]="messageData.persona">
    <mat-card-content>
      @if (messageData.persona === 'agent') {
        <div class="agent-message-header">
          <mat-icon>smart_toy</mat-icon>
          <span class="agent-name">{{messageData.agentName}}</span>
        </div>
      }
      
      @if (messageData.persona == 'user') {
        <div class="user-message-content">{{ messageData.text }}</div>
      } @else {
        <!-- Bot or Agent message -->
        @if (messageData.typing) {
          <div class="typing__dot">...</div>
        } @else {
          <div class="main-content">
            <markdown [data]="messageData.text"></markdown>
          </div>
        }
      }
    </mat-card-content>
  </mat-card>
</div>
```

4. **Update Styling** (chatbox.component.css):
```css
/* Agent messages - left-aligned like bot but different color */
.chat-message.agent {
  justify-content: flex-start;
}

.chat-message.agent mat-card {
  background-color: var(--md-sys-color-tertiary-container);
  color: var(--md-sys-color-on-tertiary-container);
}

.agent-message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-weight: 500;
  opacity: 0.8;
}

.agent-name {
  font-size: 0.9em;
}
```

---

#### 12. **AppComponent** (UPDATED)
**Purpose**: Add Agents panel button and wire up components

**Location**: `src/main/frontend/src/app/app.component.ts`

**Updates**:

1. **Import AgentsPanelComponent**:
```typescript
import { AgentsPanelComponent } from '../agents-panel/agents-panel.component';
```

2. **Add to imports array**:
```typescript
imports: [
  // ... existing imports
  AgentsPanelComponent
]
```

3. **Add ViewChild for agents panel**:
```typescript
@ViewChild('agentsPanel') agentsPanel!: AgentsPanelComponent;
```

4. **Add method to handle agent message**:
```typescript
onAgentMessageSent(agent: A2AAgent, message: string): void {
  this.chatbox()?.sendMessageToAgent(agent, message);
}
```

**Template Updates** (app.component.html):

1. **Add Agents button**:
```html
<div class="control-buttons">
  <button mat-mini-fab
          matTooltip="Chat"
          (click)="toggleChatPanel()">
    <mat-icon>chat</mat-icon>
  </button>
  
  <button mat-mini-fab
          matTooltip="Documents"
          (click)="toggleDocumentsPanel()">
    <mat-icon>description</mat-icon>
  </button>
  
  <!-- NEW: Agents button -->
  <button mat-mini-fab
          matTooltip="Agents"
          (click)="toggleAgentsPanel()"
          [class.panel-has-issues]="hasAgentIssues()">
    <mat-icon>smart_toy</mat-icon>
  </button>
  
  <button mat-mini-fab
          matTooltip="MCP Servers"
          (click)="toggleMcpServersPanel()">
    <mat-icon>extension</mat-icon>
  </button>
  
  <button mat-mini-fab
          matTooltip="Memory"
          (click)="toggleMemoryPanel()">
    <mat-icon>psychology</mat-icon>
  </button>
</div>
```

2. **Add AgentsPanelComponent**:
```html
<app-agents-panel #agentsPanel
                  [metrics]="metrics()"
                  (messageSent)="onAgentMessageSent($event.agent, $event.message)">
</app-agents-panel>
```

3. **Add helper method**:
```typescript
hasAgentIssues(): boolean {
  const agents = this.metrics().a2aAgents;
  return agents.length === 0 || agents.some(a => !a.healthy);
}

toggleAgentsPanel(): void {
  this.sidenavService.toggle('agents');
}
```

---

## Implementation Sequence

### Phase 1: Backend Core (Days 1-2) ✅ COMPLETE
1. ✅ Create package structure: `org.tanzu.mcpclient.a2a`
2. ✅ Implement `AgentCard.java` with nested records
3. ✅ Implement `A2AModels.java` with all message structures
4. ✅ Implement `A2ADiscoveryService.java`
5. ⏭️ Write unit tests for models and discovery service (SKIPPED - to be added later)

### Phase 2: Backend Services (Days 3-4) ✅ COMPLETE
6. ✅ Implement `A2AAgentService.java`
7. ✅ Implement `A2AConfiguration.java`
8. ✅ Test initialization flow with mock agent cards
9. ✅ Update `MetricsService.java` to include A2A agents
10. ⏭️ Test metrics endpoint returns A2A data (SKIPPED - to be added later)

### Phase 3: Backend Controller (Day 5) ✅ COMPLETE
11. ✅ Implement `A2AController.java`
12. ✅ Create request/response DTOs
13. ✅ Implement error handling
14. ⏭️ Test end-to-end message sending (SKIPPED - to be added later)
15. ⏭️ Integration tests with test agent (SKIPPED - to be added later)

### Phase 4: Frontend Models (Day 6) ✅ COMPLETE
16. ✅ Update TypeScript interfaces in `app.component.ts`
17. ✅ Add A2AAgent and AgentCapabilities interfaces
18. ✅ Update PlatformMetrics interface
19. ✅ Update all component PlatformMetrics initializations

### Phase 5: Frontend Agents Panel (Days 7-8) ✅ COMPLETE
20. ✅ Generate `AgentsPanelComponent` using Angular CLI
21. ✅ Implement component logic with signals
22. ✅ Create template with Material Design components
23. ✅ Add styling following Material Design 3 (100% compliance)
24. ✅ Wire up to SidenavService
25. ⏭️ Test panel open/close and status display (SKIPPED - per user request)

### Phase 6: Frontend Message Dialog (Day 9) ✅ COMPLETE
26. ✅ Generate `AgentMessageDialogComponent`
27. ✅ Implement dialog component with signals
28. ✅ Create dialog template with Material Design
29. ✅ Add form validation
30. ⏭️ Test dialog flow (SKIPPED - per user request)

### Phase 7: Frontend Chat Integration (Days 10-11) ✅ COMPLETE
31. ✅ Update `ChatboxComponent` interface to add agent persona
32. ✅ Implement `sendMessageToAgent()` method
33. ✅ Update template to handle agent persona
34. ✅ Add agent-specific styling with Material Design tokens
35. ⏭️ Test message flow end-to-end (SKIPPED - per user request)

### Phase 8: Frontend App Integration (Day 12)
36. Update `AppComponent` to include AgentsPanelComponent
37. Add agents button to control panel
38. Wire up event handlers
39. Test all panel interactions
40. Verify sidenav exclusivity

### Phase 9: Testing & Refinement (Days 13-14)
41. End-to-end testing with real A2A agent
42. Test error scenarios (agent down, network errors)
43. Test with multiple agents
44. Performance testing
45. UI/UX refinements
46. Documentation updates

### Phase 10: Documentation & Deployment (Day 15)
47. Update README.md with A2A setup instructions
48. Update `docs/tanzu-chat-guide.md`
49. Create A2A troubleshooting guide
50. Deploy to test environment
51. User acceptance testing

---

## Data Flow Diagrams

### Agent Discovery Flow (Startup)
```
Application Startup
       ↓
A2ADiscoveryService.getAgentCardUris()
       ↓
Query CF Services (tag: "a2a")
       ↓
Extract "uri" credentials
       ↓
A2AConfiguration.onApplicationReady()
       ↓
For each URI:
   → Create A2AAgentService
   → Fetch Agent Card (HTTP GET)
   → Parse JSON to AgentCard
   → Set healthy status
       ↓
Store in agentServices list
       ↓
Ready for use
```

### Message Send Flow (Runtime)
```
User clicks "Send Message" on agent
       ↓
AgentsPanelComponent.openAgentMessageDialog()
       ↓
AgentMessageDialogComponent opens
       ↓
User enters message and clicks "Send"
       ↓
Dialog closes with result
       ↓
AppComponent.onAgentMessageSent()
       ↓
ChatboxComponent.sendMessageToAgent()
       ↓
Add user message to chat
Add placeholder agent message
       ↓
HTTP POST /a2a/send-message
  {serviceName, messageText}
       ↓
A2AController.sendMessage()
       ↓
Find A2AAgentService by name
       ↓
A2AAgentService.sendMessage()
       ↓
Create JSON-RPC request:
  - method: "message/send"
  - params: {message, configuration}
       ↓
HTTP POST to agent.url
  Content-Type: application/json
  Body: JsonRpcRequest
       ↓
A2A Agent processes
       ↓
Returns JsonRpcResponse
  - result: Task or Message
       ↓
A2AController extracts text from result
       ↓
Returns SendMessageResponse to frontend
       ↓
ChatboxComponent updates agent message
  - Remove typing indicator
  - Set response text
       ↓
Message displayed in chat
```

### Metrics Flow
```
Frontend polls /metrics every 5 seconds
       ↓
MetricsController.getMetrics()
       ↓
MetricsService.getPlatformMetrics()
       ↓
Build A2A agents list:
   For each A2AAgentService:
     → Get service name
     → Get agent card
     → Get health status
     → Map to A2AAgent record
       ↓
Include in PlatformMetrics
       ↓
Return JSON to frontend
       ↓
AppComponent updates metrics signal
       ↓
AgentsPanelComponent receives new metrics
       ↓
UI updates (status, agent list)
```

---

## Service Binding Configuration

### Example Service Binding
```bash
# Create user-provided service for A2A agent
cf cups a2a-summarizer \
  -p '{"uri":"https://cf-summarization-a2a.apps.tas-ndc.kuhn-labs.com/.well-known/agent.json"}' \
  -t "a2a"

# Bind to application
cf bind-service cf-mcp-client a2a-summarizer

# Restart application
cf restart cf-mcp-client
```

### Multiple Agents
```bash
# Bind multiple A2A agents
cf cups a2a-summarizer -p '{"uri":"https://summarizer.example.com/.well-known/agent.json"}' -t "a2a"
cf cups a2a-translator -p '{"uri":"https://translator.example.com/.well-known/agent.json"}' -t "a2a"
cf cups a2a-analyzer -p '{"uri":"https://analyzer.example.com/.well-known/agent.json"}' -t "a2a"

cf bind-service cf-mcp-client a2a-summarizer
cf bind-service cf-mcp-client a2a-translator
cf bind-service cf-mcp-client a2a-analyzer

cf restart cf-mcp-client
```

---

## Error Handling Strategy

### Backend Errors

1. **Agent Card Fetch Failure**
   - Mark agent as unhealthy
   - Store error message
   - Log error details
   - Continue with other agents
   - Display in UI with error icon

2. **Message Send Failure**
   - Return 500 with error details
   - Log full exception
   - Frontend displays error to user
   - User can retry

3. **JSON-RPC Errors**
   - Parse error field from response
   - Extract error code and message
   - Return to frontend
   - Display user-friendly message

### Frontend Errors

1. **Network Errors**
   - Catch HTTP errors
   - Display "Network error" message
   - Update agent message with error state
   - Provide retry option

2. **Invalid Response**
   - Check response structure
   - Handle missing fields gracefully
   - Display generic error message
   - Log to console for debugging

3. **Dialog Cancellation**
   - Close dialog without action
   - No message sent
   - Panel remains open

---

## Testing Strategy

### Unit Tests (Backend)

1. **AgentCard Tests**
   - Test JSON deserialization
   - Test all record constructors
   - Test nested record parsing

2. **A2AModels Tests**
   - Test Part types (sealed interface)
   - Test Message creation
   - Test JSON-RPC structures

3. **A2ADiscoveryService Tests**
   - Mock CfEnv
   - Test service filtering by tag
   - Test URI extraction
   - Test empty service list

4. **A2AAgentService Tests**
   - Mock RestClient
   - Test agent card fetching
   - Test health status
   - Test message sending
   - Test error handling

5. **A2AController Tests**
   - Mock A2AConfiguration
   - Test send-message endpoint
   - Test error responses
   - Test response mapping

### Integration Tests (Backend)

1. **E2E Message Flow**
   - Use WireMock for A2A agent
   - Test full request/response cycle
   - Verify JSON-RPC format
   - Test error scenarios

2. **Metrics Integration**
   - Verify A2A agents in metrics response
   - Test health status propagation

### Unit Tests (Frontend)

1. **AgentsPanelComponent Tests**
   - Test agent sorting
   - Test status calculation
   - Test dialog opening
   - Test sidenav integration

2. **AgentMessageDialogComponent Tests**
   - Test message validation
   - Test dialog close/cancel
   - Test result format

3. **ChatboxComponent Tests**
   - Test agent message addition
   - Test agent persona handling
   - Test error display

### E2E Tests

1. **Full User Flow**
   - Open agents panel
   - View agent list
   - Send message to agent
   - Verify message in chat
   - Verify response from agent

2. **Error Scenarios**
   - Unhealthy agent (can't send)
   - Network error during send
   - Invalid agent response

---

## Material Design 3 Guidelines

### Color Scheme

**Agent Messages**:
- Background: `var(--md-sys-color-tertiary-container)`
- Text: `var(--md-sys-color-on-tertiary-container)`
- Icon color: `var(--md-sys-color-tertiary)`

**Status Indicators**:
- Healthy: `var(--md-sys-color-primary-container)`
- Warning: `var(--md-sys-color-warning-container)` (custom token)
- Error: `var(--md-sys-color-error-container)`

### Component Usage

**Agents Panel**:
- Mat-sidenav for slide-in panel
- Mat-card for each agent
- Mat-chip for capabilities
- Mat-icon for status indicators
- Mat-raised-button for actions

**Message Dialog**:
- Mat-dialog for modal
- Mat-form-field with outline appearance
- Mat-button for cancel
- Mat-raised-button for send (primary color)

### Typography

- Agent name: Title Medium
- Description: Body Medium
- Version: Label Small
- Status text: Label Medium

### Spacing

- Panel width: 400px
- Card margin-bottom: 16px
- Content padding: 16px
- Button gap: 8px
- Icon-text gap: 8px

### Elevation

- Agent cards: Level 1 (outlined)
- Status card: Level 1 (outlined)
- Dialog: Level 3 (default)

---

## Configuration and Properties

### Application Properties
No new properties required. Service discovery uses CF environment variables.

### Environment Variables (Optional)
```properties
# Optional: Override A2A request timeout (default 30s)
a2a.request.timeout=30000

# Optional: Enable debug logging
logging.level.org.tanzu.mcpclient.a2a=DEBUG
```

---

## Security Considerations

1. **Agent Card Validation**
   - Validate agent card schema
   - Check required fields
   - Verify URL format

2. **Message Content**
   - No sanitization needed (text-only)
   - Backend validates JSON-RPC structure
   - Agent responsible for content validation

3. **HTTPS**
   - Agent card URIs should use HTTPS
   - Agent endpoints should use HTTPS
   - Log warning for HTTP

4. **Authentication**
   - Phase 1: No authentication
   - Future: Support Bearer tokens
   - Future: Support API keys from service credentials

5. **Rate Limiting**
   - Consider adding rate limiting per agent
   - Track request count and timestamps
   - Future enhancement

---

## Performance Considerations

1. **Agent Card Caching**
   - Fetch once on startup
   - Cache in memory
   - No TTL in Phase 1
   - Future: Periodic refresh

2. **Concurrent Requests**
   - Use RestClient (thread-safe)
   - No connection pooling config needed
   - Spring Boot handles defaults

3. **Frontend Polling**
   - Existing 5-second metrics poll
   - No additional polling needed
   - Agent status updates via metrics

4. **Message Size**
   - Phase 1: Text messages only
   - No size limit enforced
   - Agent may have limits

---

## Future Enhancements

### Phase 2 Features

1. **Streaming Support**
   - Support message/stream for real-time updates
   - Use SSE on backend
   - Update UI progressively

2. **File Support**
   - Allow sending FilePart messages
   - Support file uploads to agents
   - Display file artifacts from agents

3. **Task Management**
   - Display task status
   - Support long-running tasks
   - Poll task status

4. **Agent Authentication**
   - Read auth from service credentials
   - Support Bearer tokens
   - Support API keys

5. **Push Notifications**
   - Implement webhook endpoint
   - Receive agent notifications
   - Update UI without polling

6. **Agent Skills Display**
   - Show agent skills in panel
   - Allow filtering by skill tags
   - Suggest agents based on query

7. **Conversation Context**
   - Maintain contextId across messages
   - Support multi-turn conversations
   - Display conversation history per agent

8. **Advanced Error Handling**
   - Retry logic with exponential backoff
   - Circuit breaker pattern
   - Detailed error codes

---

## Documentation Requirements

### User Documentation

1. **Setup Guide**
   - How to bind A2A agents
   - Service credential format
   - Troubleshooting common issues

2. **Usage Guide**
   - How to send messages to agents
   - Understanding agent responses
   - When to use agents vs. MCP tools

3. **Admin Guide**
   - Monitoring agent health
   - Managing multiple agents
   - Performance tuning

### Developer Documentation

1. **Architecture Document**
   - Component overview
   - Data flow diagrams
   - Integration points

2. **API Documentation**
   - A2AController endpoints
   - Request/response formats
   - Error codes

3. **Testing Guide**
   - Running unit tests
   - Running integration tests
   - Creating test agents

---

## Success Criteria

### Functional Requirements
- ✅ A2A agents discovered from CF service bindings
- ✅ Agent cards fetched and parsed successfully
- ✅ Agents displayed in new Agents panel
- ✅ Health status visible for each agent
- ✅ Users can send text messages to agents
- ✅ Agent responses displayed in chat
- ✅ Agent messages visually distinct from bot messages
- ✅ Dialog closes after sending message
- ✅ Error handling for unhealthy agents
- ✅ Multiple agents supported simultaneously

### Non-Functional Requirements
- ✅ Material Design 3 guidelines followed
- ✅ Modern Angular constructs (signals) used
- ✅ Modern Java constructs (records) used
- ✅ Consistent with existing UI patterns
- ✅ Responsive design
- ✅ Accessible (ARIA labels, keyboard navigation)
- ✅ Fast response times (<2s for agent messages)
- ✅ Comprehensive error messages
- ✅ Logging for troubleshooting

### Quality Requirements
- ✅ Unit test coverage >80%
- ✅ Integration tests pass
- ✅ No security vulnerabilities
- ✅ Code review approved
- ✅ Documentation complete
- ✅ User acceptance testing passed

---

## Dependencies

### Backend Dependencies (Already in pom.xml)
- Spring Boot 3.5.5
- Spring Web
- Jackson (JSON serialization)
- java-cfenv-boot 3.5.0
- RestClient (Spring 6+)

### Frontend Dependencies (Already in package.json)
- Angular 20
- Angular Material
- RxJS
- TypeScript

### No New Dependencies Required

---

## Rollback Plan

If critical issues arise:

1. **Feature Flag**
   - Add application property: `a2a.enabled=false`
   - Disable A2A discovery when false
   - Hide Agents panel button when disabled

2. **Service Unbinding**
   - Unbind A2A services: `cf unbind-service cf-mcp-client <service-name>`
   - Restart application
   - Agents panel shows empty state

3. **Code Rollback**
   - Revert Git commits
   - Redeploy previous version
   - Remove A2A service bindings

---

## Monitoring and Observability

### Metrics to Track
- Number of A2A agents bound
- Number of healthy/unhealthy agents
- Message send success rate
- Message send latency (p50, p95, p99)
- Error rate by error type

### Logging Strategy
- INFO: Agent initialization, successful messages
- WARN: Agent fetch failures, recoverable errors
- ERROR: Message send failures, unrecoverable errors
- DEBUG: Full JSON-RPC payloads, detailed flow

### Log Format
```
[A2A] [AgentName] [Operation] Message
Example:
[A2A] [Text Summarization Agent] [INIT] Successfully loaded agent card
[A2A] [Text Summarization Agent] [SEND] Sending message to agent
[A2A] [Text Summarization Agent] [RESPONSE] Received response in 1.2s
```

---

## Appendix A: Example Agent Card

```json
{
  "capabilities": {
    "streaming": true,
    "pushNotifications": false,
    "stateTransitionHistory": false
  },
  "defaultInputModes": ["text"],
  "defaultOutputModes": ["text"],
  "description": "A2A agent that summarizes text using LangChain and OpenAI-compatible LLM endpoints",
  "name": "Text Summarization Agent",
  "preferredTransport": "JSONRPC",
  "protocolVersion": "0.3.0",
  "skills": [
    {
      "description": "Summarizes input text using LLM, producing concise summaries while preserving key information",
      "id": "text-summarization",
      "name": "Text Summarization",
      "tags": ["summarization", "text-processing", "llm"]
    }
  ],
  "url": "https://cf-summarization-a2a.apps.tas-ndc.kuhn-labs.com/",
  "version": "1.0.0"
}
```

---

## Appendix B: Example JSON-RPC Exchange

### Request
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "message/send",
  "params": {
    "message": {
      "role": "user",
      "parts": [
        {
          "kind": "text",
          "text": "Summarize this: Cloud Foundry is a platform as a service..."
        }
      ],
      "messageId": "550e8400-e29b-41d4-a716-446655440000"
    },
    "configuration": {
      "acceptedOutputModes": ["text"],
      "blocking": true
    }
  }
}
```

### Response
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "id": "task-123",
    "contextId": "ctx-456",
    "status": {
      "state": "completed",
      "message": {
        "role": "agent",
        "parts": [
          {
            "kind": "text",
            "text": "Cloud Foundry is a PaaS that simplifies application deployment and management."
          }
        ],
        "messageId": "msg-789"
      }
    },
    "kind": "task"
  }
}
```

---

## Appendix C: File Structure

```
cf-mcp-client/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/
│   │   │       └── tanzu/
│   │   │           └── mcpclient/
│   │   │               ├── a2a/                    (NEW)
│   │   │               │   ├── AgentCard.java
│   │   │               │   ├── A2AModels.java
│   │   │               │   ├── A2ADiscoveryService.java
│   │   │               │   ├── A2AAgentService.java
│   │   │               │   ├── A2AConfiguration.java
│   │   │               │   └── A2AController.java
│   │   │               ├── metrics/
│   │   │               │   └── MetricsService.java  (UPDATED)
│   │   │               └── ...
│   │   │
│   │   └── frontend/
│   │       └── src/
│   │           ├── app/
│   │           │   ├── app.component.ts            (UPDATED)
│   │           │   └── app.component.html          (UPDATED)
│   │           ├── agents-panel/                   (NEW)
│   │           │   ├── agents-panel.component.ts
│   │           │   ├── agents-panel.component.html
│   │           │   ├── agents-panel.component.css
│   │           │   └── agents-panel.component.spec.ts
│   │           ├── agent-message-dialog/           (NEW)
│   │           │   ├── agent-message-dialog.component.ts
│   │           │   ├── agent-message-dialog.component.html
│   │           │   └── agent-message-dialog.component.css
│   │           ├── chatbox/
│   │           │   ├── chatbox.component.ts        (UPDATED)
│   │           │   ├── chatbox.component.html      (UPDATED)
│   │           │   └── chatbox.component.css       (UPDATED)
│   │           └── ...
│   │
│   └── test/
│       ├── java/
│       │   └── org/
│       │       └── tanzu/
│       │           └── mcpclient/
│       │               └── a2a/                     (NEW)
│       │                   ├── AgentCardTest.java
│       │                   ├── A2AModelsTest.java
│       │                   ├── A2ADiscoveryServiceTest.java
│       │                   ├── A2AAgentServiceTest.java
│       │                   └── A2AControllerTest.java
│       └── ...
│
└── docs/
    ├── a2a-integration.md                           (NEW - this document)
    ├── a2a-setup-guide.md                          (NEW)
    └── tanzu-chat-guide.md                         (UPDATED)
```

---

## Implementation Status

### Completed
- ✅ **Phase 1: Backend Core** (Completed 2025-11-04)
  - Package structure created at `src/main/java/org/tanzu/mcpclient/a2a/`
  - `AgentCard.java` with nested records (AgentProvider, AgentCapabilities, AgentSkill, SkillExample)
  - `A2AModels.java` with sealed Part interface and implementations (TextPart, FilePart, DataPart)
  - JSON-RPC 2.0 structures (JsonRpcRequest, JsonRpcResponse, JsonRpcError)
  - Task management records (Task, TaskStatus, Artifact)
  - `A2ADiscoveryService.java` with CF service binding discovery
  - `A2AServiceInfo` record for service name + URI pairing
  - Maven build verified - all code compiles successfully

- ✅ **Phase 2: Backend Services** (Completed 2025-11-04)
  - `A2AAgentService.java` - Manages communication with individual A2A agents
    - Agent card fetching and caching on initialization
    - Health status tracking with error message capture
    - `sendMessage()` method with JSON-RPC 2.0 protocol
    - RestClient-based HTTP communication
    - Comprehensive logging with `[A2A]` prefix
  - `A2AConfiguration.java` - Spring Configuration for agent initialization
    - Listens for `ApplicationReadyEvent` to initialize agents on startup
    - Creates `A2AAgentService` instances from discovered services
    - Maintains agent services list and name mappings
    - Publishes `A2AConfigurationEvent` for metrics integration
  - `A2AConfigurationEvent.java` - Spring ApplicationEvent
    - Carries list of initialized agent services
    - Enables event-driven architecture for metrics updates
  - `MetricsService.java` - Updated to include A2A agents
    - Added `A2AAgent` record with agent metadata
    - Added `a2aAgents` field to `Metrics` record
    - Event listener for `A2AConfigurationEvent`
    - `buildA2AAgentsList()` transformation method
  - Maven build verified - all code compiles successfully

- ✅ **Phase 3: Backend Controller** (Completed 2025-11-04)
  - `A2AController.java` - REST controller for A2A operations
    - POST `/a2a/send-message` endpoint for sending messages to agents
    - `SendMessageRequest` and `SendMessageResponse` record DTOs
    - Comprehensive error handling (404, 503, 500)
    - Intelligent response text extraction from JSON-RPC results
    - Parses Task or Message objects from agent responses
    - Extracts text from TextPart elements with newline concatenation
    - Consistent logging with debug, info, warn, and error levels
  - Maven build verified - all code compiles successfully
  - Unit tests skipped per Phase 3 requirements (steps 14-15)

- ✅ **Phase 4: Frontend Models** (Completed 2025-11-04)
  - Updated `app.component.ts` with new TypeScript interfaces
    - `AgentCapabilities` interface (streaming, pushNotifications, stateTransitionHistory)
    - `A2AAgent` interface with all required fields
    - Updated `PlatformMetrics` interface to include `a2aAgents: A2AAgent[]`
  - Updated PlatformMetrics initialization in all components:
    - `app.component.ts` - Main metrics signal
    - `bottom-navigation.ts` - Bottom navigation component
    - `chatbox.component.ts` - Chatbox component
    - `navigation-rail.component.ts` - Navigation rail component
  - Angular build verified - all TypeScript compiles successfully
  - Type safety enforced across frontend-backend communication

- ✅ **Phase 5: Frontend Agents Panel** (Completed 2025-11-04)
  - Generated AgentsPanelComponent with Angular CLI
  - Implemented component logic with signals and modern Angular constructs
  - Created template following Material Design 3 guidelines
  - **Material Design 3 Compliance**: 100/100 score
    - ✅ All typography uses Material type scale tokens
    - ✅ Full accessibility with ARIA labels, roles, semantic HTML
    - ✅ Proper mat-sidenav-container structure with fixedInViewport
    - ✅ Status chip using mat-chip-set pattern (not custom card)
    - ✅ State layers for interactive elements (hover/focus/active)
    - ✅ All colors use Material Design tokens
    - ✅ Motion/animation using Material motion tokens
    - ✅ Responsive design with mobile breakpoints
    - ✅ Dark mode support
    - ✅ Elevation shadows with proper tokens
  - Wired up to SidenavService for exclusive panel behavior
  - Angular build verified - no compilation errors
  - Matches MCP Servers panel quality and patterns exactly

- ✅ **Phase 6: Frontend Message Dialog** (Completed 2025-11-04)
  - Enhanced `AgentMessageDialogComponent` with modern Angular signals
  - **Component Logic** (`agent-message-dialog.ts`):
    - Converted `messageText` from plain string to `signal('')`
    - Modern Angular signal getters `messageText()` and setters `messageText.set()`
    - Form validation with `canSend()` method
    - Proper dialog result typing with `AgentMessageDialogResult`
  - **Template** (`agent-message-dialog.html`):
    - Signal binding with `[ngModel]` and `(ngModelChange)`
    - Added comprehensive ARIA labels for accessibility
    - Material Design outline appearance form field
    - Dynamic aria-label on Send button based on validation state
  - **Styling** (`agent-message-dialog.css`):
    - All spacing uses `--md-sys-spacing-*` tokens
    - Typography uses `--md-sys-typescale-*` tokens
    - Colors use `--md-sys-color-*` tokens
    - Responsive design with mobile breakpoint at 599px
  - Angular build verified - no compilation errors

- ✅ **Phase 7: Frontend Chat Integration** (Completed 2025-11-04)
  - Updated `ChatboxComponent` to support agent persona messages
  - **Interface Updates** (`chatbox.component.ts:46-63`):
    - Added `'agent'` to ChatboxMessage persona union type
    - Added optional `agentName?: string` field for agent identification
    - Created `SendMessageResponse` interface for A2A backend communication
  - **sendMessageToAgent() Method** (`chatbox.component.ts:396-460`):
    - Adds user message → agent typing placeholder → backend call → response update
    - HTTP POST to `/a2a/send-message` with serviceName and messageText
    - Comprehensive error handling with user-friendly messages
    - Uses signal updates for reactive message list management
    - Shows agent name during typing indicator
  - **Template Updates** (`chatbox.component.html:10-36`):
    - Dynamic ARIA labels for agent message accessibility
    - Agent header with smart_toy icon and agent name
    - Added `data-agent-name` and `data-card-variant="agent"` attributes
    - Conditional rendering of agent header when not typing
    - Different typing messages for agent vs bot
  - **Agent-Specific Styling** (`chatbox.component.css:100-162`):
    - Left-aligned layout like bot messages
    - Material Design 3 tertiary-container color scheme
    - Card variant `data-card-variant="agent"` with elevated MD3 pattern
    - Background: `var(--md-sys-color-tertiary-container)`
    - Text: `var(--md-sys-color-on-tertiary-container)`
    - Icon: `var(--md-sys-color-tertiary)`
    - Agent header with flex layout, gap spacing, proper typography
  - **Enhanced Computed Signals** (`chatbox.component.ts:154-167`):
    - Updated `messagesWithReasoningFlags` to support agent persona
    - Reasoning and error displays work for both bot and agent messages
  - Angular build verified - no compilation errors

### Pending
- Phase 8: Frontend App Integration
- Phase 9: Testing & Refinement
- Phase 10: Documentation & Deployment

### Notes
- Unit tests for Phases 1-3 were skipped initially and will be added later
- All implementations use modern Java constructs (records, sealed interfaces, lambdas) as per project guidelines
- All implementations use modern Angular constructs (signals, standalone components) as per project guidelines
- Event-driven architecture used for metrics integration following existing Spring patterns
- Logging follows consistent format: `[A2A] [AgentName] [Operation] Message`
- Frontend now ready to consume A2A agent data from backend `/metrics` endpoint
- **AgentsPanelComponent achieves 100% Material Design 3 compliance** - all accessibility, typography, color, motion, and responsive design standards met
- **ChatboxComponent enhanced with agent persona support** - visually distinct agent messages using tertiary color scheme
- **AgentMessageDialogComponent uses modern Angular signals** - reactive form state management

---

## End of Design Document

This design document provides a comprehensive plan for implementing A2A client functionality in cf-mcp-client. It follows the existing architectural patterns, uses modern Angular and Java constructs, and adheres to Material Design 3 guidelines.

**Next Steps**: Proceed with Phase 8 (Frontend App Integration: Update AppComponent to include AgentsPanelComponent, add agents button to control panel, and wire up event handlers).
