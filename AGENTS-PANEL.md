# Agents Panel UI Refactoring - Design & Implementation Plan

## Executive Summary

This plan outlines the refactoring of the agents panel UI to improve user experience by moving conversation elements to the main Chatbox component and transforming the agents panel into a selection interface. This creates a unified conversation experience where agent interactions happen alongside regular chat messages.

## Design Goals

1. **Unified Conversation Experience**: All conversations (chat and agent) occur in the same Chatbox component
2. **Clear Agent Selection**: Visual indication of which agent is selected
3. **Contextual UI Changes**: Chatbox adapts its appearance and behavior based on agent selection
4. **Distinct Message Personas**: Agent messages are visually distinct from user and bot messages
5. **Modern Angular Patterns**: Leverage signals and reactive programming throughout

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

## Detailed Component Changes

### 1. AgentsPanelComponent Refactoring

#### Current State
- Contains full conversation UI (input, messages, send functionality)
- Manages agent connection and message handling
- Single agent display with embedded chat

#### Target State
- **Agent List Display**: Grid/list of available agents with selection indicators
- **Selection Management**: Radio-button style selection (max one agent)
- **Status Indicators**: Connection status per agent
- **No Conversation UI**: All conversation elements removed

#### Key Changes ✅ IMPLEMENTED
```typescript
// AgentInfo interface - IMPLEMENTED
interface AgentInfo {
  id: string;
  name: string;
  description: string;
  status: 'connected' | 'disconnected' | 'busy';
  icon?: string;
  color?: string; // For visual distinction
}

// AgentsPanelComponent now uses AgentSelectionService
// Signals moved to centralized service for better state management
readonly availableAgents = computed(() => this.agentSelectionService.availableAgents());
readonly selectedAgent = computed(() => this.agentSelectionService.selectedAgent());

// Selection methods
selectAgent(agent: AgentInfo): void {
  if (this.selectedAgent()?.id === agent.id) {
    this.agentSelectionService.deselectAgent(); // Toggle off
  } else {
    this.agentSelectionService.selectAgent(agent); // Select new
  }
}
```

#### UI Implementation Details
- **Agent Cards**: Responsive card layout with hover effects
- **Status Indicators**: Color-coded badges (green=connected, orange=busy, red=disconnected)
- **Selection Feedback**: Blue border and check icon for selected agent
- **Selection Banner**: Info banner showing selected agent with deselect option

### 2. ChatboxComponent Enhancement

#### New Responsibilities
- Handle both regular chat and agent conversations
- Dynamically adjust UI based on agent selection
- Route messages to appropriate handlers (chat vs agent)
- Display agent responses with distinct persona

#### State Additions
```typescript
// New input from AgentsPanelComponent
@Input() selectedAgent: AgentInfo | null = null;

// New signals
private readonly _agentMode = computed(() => this.selectedAgent !== null);
private readonly _chatboxColor = computed(() => 
  this._agentMode() ? 'agent-blue' : 'default'
);

// Extended message interface
interface ChatboxMessage {
  text: string;
  persona: 'user' | 'bot' | 'agent'; // Add 'agent' persona
  agentType?: string; // Which agent sent this
  typing?: boolean;
  reasoning?: string;
  showReasoning?: boolean;
  error?: ErrorInfo;
  showError?: boolean;
}
```

#### UI Behavior Changes

##### When Agent is Selected:
1. **Prompt Button → Agent Label**
   - Replace openPromptSelection() button with agent indicator
   - Display: "Agent: [Agent Name]" with agent icon
   - Include small "×" button to deselect agent

2. **Color Scheme Change**
   - Input border: Change to blue tone (#0066CC)
   - Send button: Blue background (#0066CC) instead of default
   - Input focus state: Blue glow/outline
   - Agent label: Blue background with white text

3. **Message Routing**
   - sendChatMessage() checks if agent is selected
   - If selected: routes to AgentsPanelComponent.sendMessage()
   - If not selected: uses existing chat flow

##### After Message Sent/Agent Deselected:
- Automatically deselect agent after successful message send
- Revert all UI elements to original state
- Transition animations for smooth visual feedback

### 3. New AgentSelectionService

Create a shared service for agent state management:

```typescript
@Injectable({ providedIn: 'root' })
export class AgentSelectionService {
  // State signals
  private readonly _selectedAgent = signal<AgentInfo | null>(null);
  private readonly _agentResponses = signal<AgentMessage[]>([]);
  private readonly _isAgentBusy = signal<boolean>(false);
  
  // Public observables
  readonly selectedAgent = this._selectedAgent.asReadonly();
  readonly agentResponses = this._agentResponses.asReadonly();
  readonly isAgentBusy = this._isAgentBusy.asReadonly();
  
  // Methods
  selectAgent(agent: AgentInfo): void;
  deselectAgent(): void;
  sendAgentMessage(message: string): Observable<void>;
  handleAgentResponse(response: AgentResponse): void;
}
```

### 4. Message Display Enhancement

#### Agent Message Styling
```css
.chat-message.agent {
  /* Distinct agent styling */
  --agent-primary: #0066CC;
  --agent-background: #E6F2FF;
  --agent-border: #B3D9FF;
  
  background: var(--agent-background);
  border-left: 3px solid var(--agent-primary);
}

.agent-avatar {
  background: var(--agent-primary);
  color: white;
  /* Use robot/gear icon */
}

.agent-name-badge {
  font-size: 11px;
  color: var(--agent-primary);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
```

## Implementation Phases

### Phase 1: Service & State Setup ✅ COMPLETED
1. ✅ **Create AgentSelectionService with signals**
   - Implemented reactive service with Angular signals
   - Includes agent state management, selection logic, and message handling
   - Supports agent metadata (name, description, status, visual customization)

2. ✅ **Update AgentsPanelComponent to remove conversation UI**
   - Transformed from conversation interface to agent selection interface
   - Removed message history, input fields, and conversation functionality
   - Added agent card-based selection UI with visual indicators

3. ✅ **Implement agent selection/deselection logic**
   - Radio-button style selection (one agent at a time)
   - Click to select/deselect with immediate visual feedback
   - Selection state persists across component interactions

4. ✅ **Set up communication between components**
   - ChatboxComponent integrates with AgentSelectionService
   - Message routing logic directs to agents when selected
   - Auto-deselection after successful agent interactions

#### Implementation Details Completed:
- **AgentSelectionService**: `/src/services/agent-selection.service.ts`
  - Reactive state management using signals
  - Methods: `selectAgent()`, `deselectAgent()`, `sendAgentMessage()`
  - Agent interface with status tracking and visual customization

- **AgentsPanelComponent**: Updated UI and logic
  - New agent card layout with status indicators
  - Selection highlighting and user feedback
  - Integration with centralized service

- **ChatboxComponent**: Agent integration
  - Extended message interface to support 'agent' persona
  - Agent message handling and routing
  - Reactive UI updates based on agent selection

### Phase 2: Chatbox Integration (PARTIALLY COMPLETED)
1. ✅ **Add selectedAgent input to ChatboxComponent**
   - ChatboxComponent now accesses selected agent via AgentSelectionService
   - Reactive computed properties for agent state

2. 🔄 **Implement conditional UI rendering based on agent selection**
   - Basic agent detection implemented
   - UI styling changes for agent mode still needed

3. ❌ **Create agent mode styling (blue theme)**
   - Agent message styling defined but not fully implemented
   - Blue theme for input/button when agent selected

4. ✅ **Implement message routing logic**
   - Messages route to agents when selected
   - Fallback to regular chat when no agent selected

### Phase 3: Message Handling (PARTIALLY COMPLETED)
1. ✅ **Extend message interface for agent persona**
   - ChatboxMessage interface updated with 'agent' persona
   - Agent type tracking in messages

2. ❌ **Implement agent message display styling**
   - Agent message styling designed but not applied in templates

3. ✅ **Connect RabbitMQ responses to Chatbox display**
   - Agent responses integrated into unified message stream
   - Error handling for agent communication failures

4. ✅ **Add proper message ordering and threading**
   - Messages appear in chronological order regardless of source

### Phase 4: Polish & Testing (NOT STARTED)
1. ❌ Add transition animations for mode changes
2. ❌ Implement error handling for agent disconnection
3. ❌ Add loading states during agent communication
4. ❌ Comprehensive testing of all flows

## Technical Considerations

### 1. Performance Optimization
- Use `trackBy` functions for agent lists and messages
- Implement virtual scrolling for long message histories
- Debounce rapid selection/deselection events
- Use OnPush change detection where possible

### 2. State Persistence
- Save selected agent in sessionStorage for page refreshes
- Maintain conversation history during agent switches
- Clear agent selection on navigation/logout

### 3. Error Handling
- Graceful degradation if agent connection fails
- Clear error messages for users
- Automatic retry with exponential backoff
- Fallback to regular chat if agent unavailable

### 4. Accessibility
- ARIA labels for agent selection
- Keyboard navigation for agent list
- Screen reader announcements for mode changes
- High contrast mode support for agent messages

## Current Status & Next Steps

### ✅ What's Working Now (Phase 1 Complete)
- **Agent Selection Interface**: Clean card-based UI for selecting agents
- **Reactive State Management**: Centralized service with Angular signals
- **Message Routing**: Messages automatically route to selected agents
- **Basic Integration**: ChatboxComponent responds to agent selection
- **Error Handling**: Graceful handling of agent communication failures
- **Auto-deselection**: Agents deselect after successful interactions

### 🔄 Immediate Next Steps (Phase 2 Completion)
1. **Chatbox UI Updates**: 
   - Add blue theme styling when agent is selected
   - Replace prompt button with agent indicator
   - Show selected agent name and deselect option

2. **Agent Message Styling**:
   - Apply distinct visual styling for agent messages
   - Add agent avatars and name badges
   - Implement agent-specific color theming

### 📋 Future Enhancements (Phase 3-4)
- Transition animations for smooth mode changes
- Enhanced loading states during agent communication
- Comprehensive error recovery mechanisms
- Performance optimizations for large message histories
- Accessibility improvements and keyboard navigation

### 🎯 Success Criteria Met
- ✅ Unified conversation experience (all messages in one place)
- ✅ Clear agent selection with visual feedback
- ✅ Reactive state management with modern Angular patterns
- ✅ Scalable architecture for future agent types
- ✅ Successful build and basic functionality

## Conclusion

Phase 1 of the agents panel refactoring has been successfully completed. The foundation for a unified conversation experience is now in place, with a clean separation between agent selection (AgentsPanelComponent) and conversation handling (ChatboxComponent). The implementation leverages modern Angular signals for reactive state management and provides a solid foundation for the remaining phases.

The next phases will focus on enhancing the visual experience and adding polish to create a seamless user interface that clearly distinguishes between regular chat and agent interactions.