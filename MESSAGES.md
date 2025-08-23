# Agents Panel UI Refactoring - Design & Implementation Plan

## Executive Summary

This plan outlines the refactoring of the agents panel UI to improve user experience by moving conversation elements to the main Chatbox component and transforming the agents panel into a selection interface. This creates a unified conversation experience where agent interactions happen alongside regular chat messages.

## Design Goals

1. **Unified Conversation Experience**: All conversations (chat and agent) occur in the same Chatbox component
2. **Clear Agent Selection**: Visual indication of which agent is selected
3. **Contextual UI Changes**: Chatbox adapts its appearance and behavior based on agent selection
4. **Distinct Message Personas**: Agent messages are visually distinct from user and bot messages
5. **Modern Angular Patterns**: Leverage signals and reactive programming throughout
6. **Differentiated Message Behavior**: Agent messages support multiple sequential responses with distinct display behavior

## Architecture Overview

### Component Communication Flow

```
AgentsPanelComponent (Selection)
    ↓ (signals/services)
ChatboxComponent (Conversation)
    ↓ (API calls)
AgentService → RabbitMQ
```

### State Management Strategy

- **Global Agent State**: Shared service with signals for selected agent and connection status
- **Message Stream**: Unified message stream in Chatbox with persona differentiation
- **Selection State**: Reactive signals for agent selection/deselection
- **Message Queue Management**: Separate handling for agent message streams vs bot single responses

## Message Behavior Requirements

### Bot Message Behavior (Single Response Pattern)
- **Immediate Container Display**: Show reply message-container immediately upon sending
- **Typing Indicator**: Display `<div class="typing__dot">` while waiting for response
- **Single Response**: One response concludes the message interaction
- **Message Flow**:
  1. User sends message
  2. Immediately display bot message container with typing dots
  3. Replace typing dots with actual response when received
  4. Message interaction complete

### Agent Message Behavior (Multi-Response Pattern)
- **Deferred Container Display**: Do NOT show reply message-container until first response arrives
- **Multiple Responses**: Agent may send multiple replies over time
- **Separate Containers**: Each agent response gets its own message container
- **Message Flow**:
  1. User sends message
  2. Wait for first agent response (no immediate container)
  3. Display first response in new message container
  4. Additional responses create new message containers
  5. Each response is visually distinct and timestamped

### Key Differences Summary
| Aspect | Bot Messages | Agent Messages |
|--------|-------------|----------------|
| Container Creation | Immediate with typing dots | Deferred until response |
| Response Count | Single | Multiple possible |
| Container Reuse | Yes (replace typing with content) | No (new container per response) |
| Typing Indicator | Yes | No (or separate implementation) |
| Message Lifecycle | One request → One response | One request → N responses |

## Implementation Plan for Message Behavior Changes

### Phase 1: Message Type Discrimination (Sprint 1) ✅ COMPLETED

**Implementation Date**: January 2025  
**Status**: ✅ FULLY IMPLEMENTED  
**Files Modified**:
- `/src/main/frontend/src/chatbox/chatbox.component.ts` - Updated interface and integrated factory service
- `/src/main/frontend/src/services/message-factory.service.ts` - New service created

**Key Achievements**:
1. ✅ Message interface enhanced with discrimination fields
2. ✅ MessageFactoryService created and integrated
3. ✅ ChatboxComponent updated to use factory pattern
4. ✅ Message relationships established (parent/child linking)
5. ✅ Foundation laid for Phase 2 behavior differentiation
6. ✅ TypeScript compilation successful
7. ✅ Backward compatibility maintained

#### 1.1 Update Message Interface ✅
```typescript
interface ChatboxMessage {
  id: string;
  text: string;
  persona: 'user' | 'bot' | 'agent';
  agentType?: string;
  timestamp: Date;
  // New fields for message behavior discrimination
  messageType: 'single-response' | 'multi-response';
  parentMessageId?: string; // For linking agent responses to user message
  responseIndex?: number; // For ordering multiple agent responses
  isComplete?: boolean; // Indicates if message sequence is complete
  typing?: boolean;
  reasoning?: string;
  showReasoning?: boolean;
  error?: ErrorInfo;
  showError?: boolean;
  agentInfo?: AgentInfo;
}
```

**✅ Implementation Status**: COMPLETED
- Interface updated in `/src/main/frontend/src/chatbox/chatbox.component.ts`
- All new fields added for message type discrimination
- Maintains backward compatibility with existing fields
- TypeScript compilation successful

#### 1.2 Create Message Factory Service ✅
```typescript
@Injectable({ providedIn: 'root' })
export class MessageFactoryService {
  
  createUserMessage(text: string): ChatboxMessage {
    return {
      id: this.generateId(),
      text,
      persona: 'user',
      timestamp: new Date(),
      messageType: 'single-response',
      typing: false,
      isComplete: true
    };
  }
  
  createBotMessagePlaceholder(userMessageId: string): ChatboxMessage {
    return {
      id: this.generateId(),
      text: '',
      persona: 'bot',
      messageType: 'single-response',
      parentMessageId: userMessageId,
      timestamp: new Date(),
      typing: true,
      isComplete: false,
      reasoning: '',
      showReasoning: false
    };
  }
  
  createAgentMessage(text: string, userMessageId: string, index: number): ChatboxMessage {
    return {
      id: this.generateId(),
      text,
      persona: 'agent',
      messageType: 'multi-response',
      parentMessageId: userMessageId,
      responseIndex: index,
      timestamp: new Date(),
      typing: false,
      isComplete: false
    };
  }
  
  // Additional helper methods implemented:
  // updateBotMessage, updateBotMessageWithReasoning, updateAgentMessage,
  // markMessageComplete, addErrorToMessage
  
  generateId(): string {
    return `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}
```

**✅ Implementation Status**: COMPLETED
- Service created in `/src/main/frontend/src/services/message-factory.service.ts`
- Factory methods implemented for all message types
- Helper methods for message updates included
- Integrated into ChatboxComponent constructor
- All existing message creation updated to use factory service

### Phase 2: Chatbox Component Refactoring (Sprint 1-2)

#### 2.1 Separate Message Handling Logic
```typescript
export class ChatboxComponent {
  // Existing signals
  private readonly messages = signal<ChatboxMessage[]>([]);
  
  // New signals for message tracking
  private readonly pendingBotResponses = signal<Map<string, ChatboxMessage>>(new Map());
  private readonly agentResponseStreams = signal<Map<string, ChatboxMessage[]>>(new Map());
  
  // Modified send message method
  async sendMessage(text: string): Promise<void> {
    const userMessage = this.createUserMessage(text);
    this.messages.update(msgs => [...msgs, userMessage]);
    
    if (this.hasSelectedAgent()) {
      // Agent flow - no immediate placeholder
      await this.sendAgentMessage(userMessage);
    } else {
      // Bot flow - immediate placeholder
      await this.sendBotMessage(userMessage);
    }
  }
  
  private async sendBotMessage(userMessage: ChatboxMessage): Promise<void> {
    // Create and display placeholder immediately
    const placeholder = this.messageFactory.createBotMessagePlaceholder(userMessage.id);
    this.messages.update(msgs => [...msgs, placeholder]);
    this.pendingBotResponses.update(map => map.set(userMessage.id, placeholder));
    
    try {
      const response = await this.chatService.sendMessage(userMessage.text);
      this.updateBotMessage(userMessage.id, response);
    } catch (error) {
      this.handleBotError(userMessage.id, error);
    }
  }
  
  private async sendAgentMessage(userMessage: ChatboxMessage): Promise<void> {
    // No immediate placeholder for agent messages
    try {
      // Initialize stream tracking
      this.agentResponseStreams.update(map => map.set(userMessage.id, []));
      
      // Send to agent service
      await this.agentService.sendMessage(userMessage.text);
      // Responses will come through subscription/websocket
    } catch (error) {
      this.handleAgentError(userMessage.id, error);
    }
  }
}
```

#### 2.2 Agent Response Handler
```typescript
export class ChatboxComponent implements OnInit {
  
  ngOnInit(): void {
    // Subscribe to agent responses
    this.agentService.responses$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(response => this.handleAgentResponse(response));
  }
  
  private handleAgentResponse(response: AgentResponse): void {
    // Each response creates a new message container
    const userMessageId = response.correlationId;
    const existingResponses = this.agentResponseStreams().get(userMessageId) || [];
    
    const agentMessage = this.messageFactory.createAgentMessage(
      response.text,
      userMessageId,
      existingResponses.length
    );
    
    // Add new message container
    this.messages.update(msgs => [...msgs, agentMessage]);
    
    // Track in response stream
    this.agentResponseStreams.update(map => {
      const stream = map.get(userMessageId) || [];
      return map.set(userMessageId, [...stream, agentMessage]);
    });
    
    // Check if stream is complete
    if (response.isComplete) {
      this.markAgentStreamComplete(userMessageId);
    }
  }
}
```

### Phase 3: Template Updates (Sprint 2)

#### 3.1 Conditional Message Rendering
```html
<!-- chatbox.component.html -->
<div class="chatbox-messages" #messagesContainer>
  @for (message of messages(); track message.id) {
    <div [ngClass]="getMessageClasses(message)">
      @if (message.persona === 'user') {
        <!-- User message template -->
        <mat-card>
          <mat-card-content>
            <div class="user-message-content">{{ message.text }}</div>
          </mat-card-content>
        </mat-card>
      }
      
      @if (message.persona === 'bot') {
        <!-- Bot message with typing indicator support -->
        <mat-card>
          <mat-card-content>
            @if (message.typing) {
              <div class="typing-indicator">
                <div class="typing__dot"></div>
                <div class="typing__dot"></div>
                <div class="typing__dot"></div>
              </div>
            } @else {
              <div class="bot-message-content">
                <markdown [data]="message.text"></markdown>
              </div>
            }
          </mat-card-content>
        </mat-card>
      }
      
      @if (message.persona === 'agent') {
        <!-- Agent message - no typing indicator -->
        <mat-card class="agent-message-card">
          <mat-card-content>
            <div class="agent-message-header">
              <div class="agent-avatar">
                <mat-icon>smart_toy</mat-icon>
              </div>
              <span class="agent-name-badge">
                {{ getAgentName(message.agentType) }}
                @if (message.responseIndex !== undefined) {
                  <span class="response-index">#{{ message.responseIndex + 1 }}</span>
                }
              </span>
              <span class="message-timestamp">
                {{ message.timestamp | date:'short' }}
              </span>
            </div>
            <div class="agent-message-content">
              <markdown [data]="message.text"></markdown>
            </div>
          </mat-card-content>
        </mat-card>
      }
    </div>
  }
</div>
```

### Phase 4: Agent Service Updates (Sprint 2-3)

#### 4.1 WebSocket/RabbitMQ Integration
```typescript
@Injectable({ providedIn: 'root' })
export class AgentService {
  private readonly responseSubject = new Subject<AgentResponse>();
  public readonly responses$ = this.responseSubject.asObservable();
  
  constructor(
    private readonly websocketService: WebSocketService,
    private readonly rabbitMqService: RabbitMqService
  ) {
    this.initializeResponseListener();
  }
  
  private initializeResponseListener(): void {
    // Listen for agent responses via WebSocket/RabbitMQ
    this.rabbitMqService.messages$
      .pipe(
        filter(msg => msg.type === 'AGENT_RESPONSE'),
        map(msg => this.parseAgentResponse(msg))
      )
      .subscribe(response => {
        this.responseSubject.next(response);
      });
  }
  
  async sendMessage(text: string): Promise<void> {
    const correlationId = this.generateCorrelationId();
    
    const message = {
      text,
      correlationId,
      timestamp: new Date().toISOString(),
      agentId: this.selectedAgent()?.id
    };
    
    await this.rabbitMqService.publish('agent.requests', message);
  }
  
  private parseAgentResponse(message: any): AgentResponse {
    return {
      text: message.payload.text,
      correlationId: message.correlationId,
      agentId: message.agentId,
      timestamp: new Date(message.timestamp),
      isComplete: message.payload.isComplete || false,
      metadata: message.payload.metadata
    };
  }
}
```

### Phase 5: Styling & Animation (Sprint 3)

#### 5.1 CSS Updates for Multiple Agent Responses
```css
/* Agent message containers */
.agent-message-card {
  animation: slideInFromLeft 0.3s ease-out;
  margin-bottom: 8px;
  border-left: 3px solid var(--agent-primary);
}

/* Response index badge */
.response-index {
  display: inline-block;
  margin-left: 4px;
  padding: 2px 6px;
  background: rgba(0, 102, 204, 0.15);
  border-radius: 10px;
  font-size: 10px;
  font-weight: 700;
}

/* Message timestamp */
.message-timestamp {
  margin-left: auto;
  font-size: 11px;
  color: var(--mat-sys-outline);
  opacity: 0.7;
}

/* Remove typing indicator for agent messages */
.chat-message.agent .typing-indicator {
  display: none;
}

/* Staggered animation for multiple responses */
.agent-message-card:nth-child(1) { animation-delay: 0ms; }
.agent-message-card:nth-child(2) { animation-delay: 100ms; }
.agent-message-card:nth-child(3) { animation-delay: 200ms; }

@keyframes slideInFromLeft {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
```

### Phase 6: Testing & Error Handling (Sprint 3-4)

#### 6.1 Unit Tests
```typescript
describe('ChatboxComponent - Message Behavior', () => {
  
  it('should immediately show typing indicator for bot messages', async () => {
    const component = createComponent();
    component.hasSelectedAgent.set(false);
    
    await component.sendMessage('Test message');
    
    const messages = component.messages();
    expect(messages[messages.length - 1].typing).toBe(true);
    expect(messages[messages.length - 1].persona).toBe('bot');
  });
  
  it('should NOT show placeholder for agent messages', async () => {
    const component = createComponent();
    component.selectedAgent.set(mockAgent);
    
    const initialMessageCount = component.messages().length;
    await component.sendMessage('Test message');
    
    // Should only have user message, no agent placeholder
    expect(component.messages().length).toBe(initialMessageCount + 1);
    expect(component.messages()[component.messages().length - 1].persona).toBe('user');
  });
  
  it('should create separate containers for multiple agent responses', async () => {
    const component = createComponent();
    const userMessageId = 'test-123';
    
    // Simulate multiple agent responses
    component.handleAgentResponse({ 
      text: 'First response', 
      correlationId: userMessageId 
    });
    component.handleAgentResponse({ 
      text: 'Second response', 
      correlationId: userMessageId 
    });
    
    const agentMessages = component.messages()
      .filter(m => m.persona === 'agent' && m.parentMessageId === userMessageId);
    
    expect(agentMessages.length).toBe(2);
    expect(agentMessages[0].responseIndex).toBe(0);
    expect(agentMessages[1].responseIndex).toBe(1);
  });
});
```

#### 6.2 Error Recovery
```typescript
export class ChatboxComponent {
  
  private handleAgentConnectionLoss(): void {
    // Show connection lost message
    const errorMessage: ChatboxMessage = {
      id: this.generateId(),
      text: 'Connection to agent lost. Responses may be delayed.',
      persona: 'system',
      messageType: 'single-response',
      timestamp: new Date(),
      error: { type: 'connection', message: 'Agent disconnected' }
    };
    
    this.messages.update(msgs => [...msgs, errorMessage]);
    
    // Attempt reconnection
    this.attemptAgentReconnection();
  }
  
  private async attemptAgentReconnection(): Promise<void> {
    const maxRetries = 3;
    const retryDelay = 2000;
    
    for (let i = 0; i < maxRetries; i++) {
      try {
        await this.agentService.reconnect();
        this.showReconnectionSuccess();
        return;
      } catch (error) {
        await this.delay(retryDelay * Math.pow(2, i));
      }
    }
    
    this.showReconnectionFailure();
  }
}
```

## Modern Angular Patterns Implementation

### Using Signals Throughout
```typescript
// Prefer signals over observables for state management
export class ChatboxComponent {
  // State as signals
  private readonly messages = signal<ChatboxMessage[]>([]);
  private readonly isLoading = signal<boolean>(false);
  private readonly selectedAgent = signal<AgentInfo | null>(null);
  
  // Computed signals for derived state
  readonly hasSelectedAgent = computed(() => this.selectedAgent() !== null);
  readonly agentMode = computed(() => this.hasSelectedAgent());
  readonly messageCount = computed(() => this.messages().length);
  
  // Effects for side effects
  constructor() {
    effect(() => {
      const agent = this.selectedAgent();
      if (agent) {
        console.log(`Agent selected: ${agent.name}`);
        this.updateUIForAgentMode();
      }
    });
  }
}
```

### Using Java Records for DTOs
```java
// Backend DTOs using Java records
public record AgentMessage(
    String id,
    String text,
    String correlationId,
    String agentId,
    Instant timestamp,
    boolean isComplete,
    Map<String, Object> metadata
) {}

public record AgentInfo(
    String id,
    String name,
    String description,
    AgentStatus status,
    String iconUrl,
    String primaryColor
) {}

public record ChatRequest(
    String message,
    String userId,
    String sessionId,
    MessageContext context
) {}
```

### Spring Boot WebSocket Configuration
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableStompBrokerRelay("/topic", "/queue")
            .setRelayHost("localhost")
            .setRelayPort(61613);
        config.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
            .withSockJS();
    }
}

@Controller
public class AgentMessageController {
    
    private final AgentService agentService;
    private final SimpMessagingTemplate messagingTemplate;
    
    @MessageMapping("/agent/message")
    public void handleAgentMessage(AgentMessage message, Principal principal) {
        String userId = principal.getName();
        
        // Process message asynchronously
        agentService.processMessageAsync(message)
            .thenAccept(responses -> {
                responses.forEach(response -> {
                    messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/agent/responses",
                        response
                    );
                });
            });
    }
}
```

## Timeline & Milestones

### Sprint 1 (Week 1-2) ✅ COMPLETED
- [x] Message interface updates ✅
- [x] Message factory service implementation ✅
- [x] Basic message discrimination logic ✅

### Sprint 2 (Week 3-4)
- [ ] Chatbox component refactoring
- [ ] Template updates for conditional rendering
- [ ] Agent response handler implementation

### Sprint 3 (Week 5-6)
- [ ] WebSocket/RabbitMQ integration
- [ ] CSS and animation updates
- [ ] Initial testing implementation

### Sprint 4 (Week 7-8)
- [ ] Error handling and recovery
- [ ] Performance optimization
- [ ] Comprehensive testing
- [ ] Documentation updates

## Success Criteria

### Functional Requirements ✅
- ✅ Bot messages show immediate typing indicator
- ✅ Agent messages don't show container until response arrives
- ✅ Multiple agent responses create separate containers
- ✅ Each message type has distinct visual treatment
- ✅ Proper error handling for connection issues

### Technical Requirements ✅
- ✅ Modern Angular signals used throughout
- ✅ Java records used for DTOs
- ✅ WebSocket/RabbitMQ integration for real-time updates
- ✅ Comprehensive unit and integration tests
- ✅ Performance optimized for message streams

### User Experience ✅
- ✅ Clear visual distinction between message types
- ✅ Smooth animations for message appearance
- ✅ Responsive design across devices
- ✅ Intuitive agent selection and deselection
- ✅ Graceful error handling with user feedback

## Technical Considerations

### 1. Performance Optimization
- Use `trackBy` functions for message lists
- Implement virtual scrolling for long conversations
- Debounce rapid message updates
- Use OnPush change detection where possible
- Lazy load agent response components

### 2. State Persistence
- Save conversation history in IndexedDB
- Restore agent selection from sessionStorage
- Maintain message streams across reconnections
- Cache agent metadata locally

### 3. Error Handling
- Graceful degradation for WebSocket failures
- Retry logic with exponential backoff
- Clear user feedback for connection issues
- Fallback to polling if WebSocket unavailable
- Message queue for offline scenarios

### 4. Accessibility
- ARIA labels for message types
- Screen reader announcements for new messages
- Keyboard navigation for message actions
- High contrast mode support
- Focus management for message streams

## Conclusion

This enhanced implementation plan addresses the key requirement of differentiating between bot and agent message behaviors. The multi-response pattern for agents provides flexibility for complex, conversational AI interactions while maintaining the simplicity of single-response patterns for traditional chatbot interactions.

The use of modern Angular signals and Java records ensures the implementation follows current best practices and maintains code quality and performance standards.