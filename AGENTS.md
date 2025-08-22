# Agents Panel Design & Implementation Plan

## Implementation Status

**Overall Progress: Phase 1 - Item 1 Complete (12.5% of total plan)**

### ✅ Completed
- **Phase 1.1**: Create Panel Structure - Basic AgentsPanel component with UI framework

### 🔄 In Progress
- None currently

### ⏳ Pending
- **Phase 1.2**: Message Queue Setup
- **Phase 2**: Core Functionality (Agent Interaction & Response Handling)
- **Phase 3**: UI Polish & Integration
- **Phase 4**: Enhancement & Future-Proofing

---

## Overview

The Agents Panel is a new standalone component for cf-mcp-client that provides an interface for interacting with AI agents, starting with the Reviewer agent. This panel follows the established pattern of existing panels (chat-panel, document-panel, mcp-servers-panel, memory-panel) while introducing agent-specific functionality.

## Architecture Overview

### Component Hierarchy

```
AgentsPanel (Main Container)
├── AgentsList (Agent Selection)
│   └── AgentCard (Individual Agent Display)
├── AgentInteraction (Communication Interface)
│   ├── AgentPromptInput (Message Input)
│   └── AgentResponseDisplay (Response Viewer)
└── AgentMessageHistory (Conversation Thread)
    └── AgentMessage (Individual Message)
```

## Core Components

### 1. AgentsPanel Component

**Purpose**: Main container component that manages the overall agents interface.

**Responsibilities**:
- Manage panel visibility state
- Coordinate between agent selection and interaction
- Handle panel-level lifecycle events
- Maintain active agent context

**Key State**:
- `isOpen: boolean`
- `selectedAgent: Agent | null`
- `agents: Agent[]` (future: dynamically populated)
- `activeConversations: Map<string, AgentConversation>`

### 2. Agent Communication Layer

**Message Queue Integration**:
- **Outbound Queue**: `agent.reviewer.request`
- **Inbound Queue**: `agent.reviewer.reply`
- **Correlation**: Use conversation ID as correlationId

**Message Format**:
```typescript
interface AgentRequest {
  correlationId: string;
  agentType: 'reviewer';
  prompt: string;
  timestamp: number;
  userId?: string;
  context?: any;
}

interface AgentResponse {
  correlationId: string;
  agentType: 'reviewer';
  content: string;
  timestamp: number;
  isPartial?: boolean;
  isComplete?: boolean;
  metadata?: any;
}
```

### 3. Response Handling

**Streaming Response Management**:
- Implement response accumulator for handling multiple partial responses
- Use correlationId to match responses to requests
- Handle response completion signals
- Manage timeout scenarios

**Response State Machine**:
```
IDLE → SENDING → WAITING → RECEIVING → COMPLETE
                    ↓          ↓
                 TIMEOUT    STREAMING
```

## UI/UX Design

### Visual Design

**Panel Layout**:
- Consistent with existing panel designs
- Collapsible/expandable interface
- Resizable panel width
- Sticky header with agent selection

**Agent Message Styling**:
- **Persona**: "agent" (distinct from "user" and "bot")
- **Color Scheme**: Blue tone palette
  - Primary: `#0066CC` (agent avatar/header)
  - Background: `#E6F2FF` (message background)
  - Border: `#B3D9FF` (message border)
  - Text: Standard dark text for readability

**Message Display**:
- Clear visual distinction from user/bot messages
- Agent icon/avatar (e.g., gear/robot icon)
- Timestamp display
- Loading/streaming indicators
- Copy-to-clipboard functionality

### User Flow

1. User opens Agents Panel from toolbar/menu
2. Panel displays with Reviewer agent pre-selected
3. User enters prompt in input field
4. System sends request with correlationId
5. Loading indicator appears
6. Responses stream in and display progressively
7. User can send follow-up prompts
8. Conversation history is maintained

## Implementation Steps

### Phase 1: Basic Infrastructure (Week 1)

1. **Create Panel Structure** ✅ **COMPLETED**
   - ✅ Set up AgentsPanel component (`src/main/frontend/src/agents-panel/`)
   - ✅ Implement panel toggle mechanism (integrated with SidenavService)
   - ✅ Add to main layout system (added to app.component.ts/html)
   - ✅ Create basic styling framework (blue-toned agent styling implemented)
   
   **Implementation Details:**
   - Created standalone Angular component following established patterns
   - Agent card shows Reviewer agent with status indicators
   - Toggle button positioned at `top: 264px` (below memory panel)
   - Blue color scheme (#0066CC) implemented as specified
   - Placeholder interaction area prepared for Phase 2
   - Component builds successfully and integrates with existing sidenav system

2. **Message Queue Setup**
   - Implement RabbitMQ connection handlers
   - Create queue publishers for `agent.reviewer.request`
   - Set up listeners for `agent.reviewer.reply`
   - Implement correlation ID management

### Phase 2: Core Functionality (Week 2)

3. **Agent Interaction Components**
   - Build AgentPromptInput component
   - Create AgentResponseDisplay component
   - Implement message state management
   - Add loading/streaming indicators

4. **Response Handling**
   - Implement response accumulator
   - Handle partial/complete response flags
   - Add timeout handling
   - Create error recovery mechanisms

### Phase 3: UI Polish & Integration (Week 3)

5. **Visual Refinement**
   - Apply blue-toned agent styling
   - Add animations and transitions
   - Implement responsive design
   - Add accessibility features

6. **Integration & Testing**
   - Integrate with existing conversation context
   - Add unit tests for components
   - Implement integration tests for message flow
   - Performance optimization

### Phase 4: Enhancement & Future-Proofing (Week 4)

7. **Advanced Features**
   - Add conversation persistence
   - Implement message search/filter
   - Add export functionality
   - Create keyboard shortcuts

8. **Multi-Agent Support Preparation**
   - Abstract agent-specific logic
   - Create agent registry system
   - Design dynamic agent loading
   - Prepare for agent discovery mechanism

## State Management

### Redux Store Structure

```javascript
agentsState: {
  panel: {
    isOpen: boolean,
    width: number,
    position: string
  },
  agents: {
    available: Agent[],
    selected: string | null
  },
  conversations: {
    [correlationId]: {
      agent: string,
      messages: AgentMessage[],
      status: 'idle' | 'sending' | 'receiving' | 'error',
      lastActivity: timestamp
    }
  },
  queue: {
    connectionStatus: 'connected' | 'disconnected' | 'error',
    pendingRequests: Map<correlationId, Request>
  }
}
```

### Actions

- `OPEN_AGENTS_PANEL`
- `CLOSE_AGENTS_PANEL`
- `SELECT_AGENT`
- `SEND_AGENT_REQUEST`
- `RECEIVE_AGENT_RESPONSE`
- `UPDATE_AGENT_CONVERSATION`
- `CLEAR_AGENT_CONVERSATION`
- `SET_QUEUE_CONNECTION_STATUS`

## Technical Considerations

### Performance

- Implement virtual scrolling for long conversation histories
- Debounce input handling
- Lazy load agent configurations
- Use React.memo for message components
- Implement message pagination

### Error Handling

- Queue connection failures
- Request timeout (30s default)
- Invalid response format
- Network interruptions
- Agent unavailability

### Security

- Sanitize user input before sending
- Validate response content
- Implement rate limiting
- Add request authentication tokens
- Log security events

### Monitoring

- Track request/response times
- Monitor queue health
- Log error rates
- Track user engagement metrics
- Measure response quality metrics

## Dependencies

### External Libraries

- RabbitMQ client library (existing)
- UUID generation for correlationIds
- Markdown renderer (for formatted responses)
- Syntax highlighter (for code in responses)

### Internal Dependencies

- Existing message queue infrastructure
- Conversation context system
- Authentication/session management
- UI component library

## Future Enhancements

### Version 2.0

- Dynamic agent discovery and registration
- Multi-agent conversations
- Agent capability descriptions
- Custom agent configurations
- Agent performance metrics

## Migration Path

For future multi-agent support:

1. Abstract current Reviewer-specific code into agent adapters
2. Create agent manifest schema
3. Implement agent registry service
4. Add agent discovery mechanism
5. Update UI to handle dynamic agent lists
6. Implement agent-specific configuration panels

## Risk Mitigation

### Technical Risks

- **Queue overload**: Implement rate limiting and backpressure
- **Memory leaks**: Proper cleanup of listeners and subscriptions
- **Stale responses**: Implement request expiration
- **UI performance**: Virtual scrolling and pagination

### User Experience Risks

- **Response delays**: Show progress indicators and partial results
- **Connection issues**: Implement reconnection logic with user feedback
- **Confusing UI**: Clear visual hierarchy and intuitive interactions
- **Lost context**: Persist conversation state locally

## Success Metrics

- Response time < 2 seconds for initial feedback
- Queue reliability > 99.9%
- User engagement rate > 60%
- Error rate < 0.1%
- User satisfaction score > 4.5/5

## Documentation Requirements

- Component API documentation
- Message format specifications
- Integration guide
- Troubleshooting guide
- User manual for agents panel

## Conclusion

The Agents Panel represents a significant enhancement to cf-mcp-client, providing a dedicated interface for AI agent interactions. By following this implementation plan, we can ensure a robust, scalable, and user-friendly solution that seamlessly integrates with the existing architecture while laying the groundwork for future multi-agent capabilities.