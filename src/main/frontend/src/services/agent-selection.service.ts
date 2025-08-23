import { Injectable, signal, computed } from '@angular/core';
import { Observable } from 'rxjs';
import { AgentService, AgentMessageEvent } from './agent.service';

export interface AgentInfo {
  id: string;
  name: string;
  description: string;
  status: 'connected' | 'disconnected' | 'busy';
  icon?: string;
  color?: string;
}

export interface AgentMessage {
  content: string;
  sender: 'user' | 'agent';
  timestamp: number;
  agentType: string;
  isComplete?: boolean;
  isError?: boolean;
}

@Injectable({ providedIn: 'root' })
export class AgentSelectionService {
  // Private state signals
  private readonly _selectedAgent = signal<AgentInfo | null>(null);
  private readonly _agentResponses = signal<AgentMessage[]>([]);
  private readonly _isAgentBusy = signal<boolean>(false);
  private readonly _availableAgents = signal<AgentInfo[]>([
    {
      id: 'reviewer',
      name: 'Code Reviewer',
      description: 'Reviews code for best practices, bugs, and improvements',
      status: 'connected',
      icon: 'search',
      color: '#0066CC'
    }
  ]);

  // Public readonly signals
  readonly selectedAgent = this._selectedAgent.asReadonly();
  readonly agentResponses = this._agentResponses.asReadonly();
  readonly isAgentBusy = this._isAgentBusy.asReadonly();
  readonly availableAgents = this._availableAgents.asReadonly();

  // Computed properties
  readonly hasSelectedAgent = computed(() => this._selectedAgent() !== null);
  readonly selectedAgentId = computed(() => this._selectedAgent()?.id || null);

  constructor(private readonly agentService: AgentService) {}

  /**
   * Selects an agent for conversation.
   */
  selectAgent(agent: AgentInfo): void {
    this._selectedAgent.set(agent);
  }

  /**
   * Deselects the currently selected agent.
   */
  deselectAgent(): void {
    this._selectedAgent.set(null);
  }

  /**
   * Sends a message to the selected agent.
   */
  sendAgentMessage(message: string): Observable<AgentMessageEvent | { error: boolean; message: string; timestamp: number }> {
    const selectedAgent = this._selectedAgent();
    if (!selectedAgent) {
      throw new Error('No agent selected');
    }

    this._isAgentBusy.set(true);
    return this.agentService.sendMessage(selectedAgent.id, message);
  }

  /**
   * Handles agent response and updates state.
   */
  handleAgentResponse(response: AgentMessage): void {
    this._agentResponses.update(responses => [...responses, response]);
    
    if (response.isComplete || response.isError) {
      this._isAgentBusy.set(false);
    }
  }

  /**
   * Updates the available agents list.
   */
  updateAvailableAgents(agents: AgentInfo[]): void {
    this._availableAgents.set(agents);
  }

  /**
   * Updates the status of a specific agent.
   */
  updateAgentStatus(agentId: string, status: AgentInfo['status']): void {
    this._availableAgents.update(agents => 
      agents.map(agent => 
        agent.id === agentId ? { ...agent, status } : agent
      )
    );
  }

  /**
   * Clears all agent responses.
   */
  clearAgentResponses(): void {
    this._agentResponses.set([]);
  }
}