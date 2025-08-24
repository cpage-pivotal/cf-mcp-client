import { Injectable, signal, computed, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AgentService, AgentMessageEvent } from './agent.service';
import { ApiService } from './api.service';

export interface AgentInfo {
  id: string;
  name: string;
  description: string;
  status: 'available' | 'busy' | 'offline' | 'error';
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
  private readonly apiService = inject(ApiService);
  private readonly agentService = inject(AgentService);

  // Private state signals
  private readonly _selectedAgent = signal<AgentInfo | null>(null);
  private readonly _agentResponses = signal<AgentMessage[]>([]);
  private readonly _isAgentBusy = signal<boolean>(false);

  // 🔄 CHANGED: Now starts empty and loads from backend
  private readonly _availableAgents = signal<AgentInfo[]>([]);
  private readonly _isLoadingAgents = signal<boolean>(false);
  private readonly _agentLoadError = signal<string | null>(null);

  // Public readonly signals
  readonly selectedAgent = this._selectedAgent.asReadonly();
  readonly agentResponses = this._agentResponses.asReadonly();
  readonly isAgentBusy = this._isAgentBusy.asReadonly();
  readonly availableAgents = this._availableAgents.asReadonly();
  readonly isLoadingAgents = this._isLoadingAgents.asReadonly();
  readonly agentLoadError = this._agentLoadError.asReadonly();

  // Computed properties
  readonly hasSelectedAgent = computed(() => this._selectedAgent() !== null);
  readonly selectedAgentId = computed(() => this._selectedAgent()?.id || null);
  readonly hasAvailableAgents = computed(() => this._availableAgents().length > 0);

  constructor() {
    // 🆕 NEW: Load agents from backend on service initialization
    this.loadAvailableAgents();
  }

  /**
   * 🆕 NEW: Loads available agents from the backend API.
   * This replaces the hardcoded array with dynamic loading.
   */
  private loadAvailableAgents(): void {
    this._isLoadingAgents.set(true);
    this._agentLoadError.set(null);

    fetch(this.apiService.getApiUrl('/api/agents'))
      .then(response => {
        if (!response.ok) {
          throw new Error(`Failed to load agents: ${response.status} ${response.statusText}`);
        }
        return response.json();
      })
      .then((agents: AgentInfo[]) => {
        // 🔄 CHANGED: Set agents from backend instead of hardcoded
        this._availableAgents.set(agents);
        this._isLoadingAgents.set(false);
        console.log(`Loaded ${agents.length} agents from backend:`, agents);
      })
      .catch(error => {
        console.error('Failed to load agents from backend:', error);
        this._agentLoadError.set(error.message || 'Failed to load agents');
        this._isLoadingAgents.set(false);

        // 🔄 FALLBACK: Only use hardcoded reviewer if backend fails
        this._availableAgents.set([{
          id: 'reviewer',
          name: 'Literary Critic',
          description: 'Authors book reviews',
          status: 'available',
          icon: 'rate_review',
          color: '#0066CC'
        }]);
      });
  }

  /**
   * 🆕 NEW: Manually refresh agents from backend.
   */
  refreshAgents(): void {
    this.loadAvailableAgents();
  }

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
   * Updates the available agents list (for external updates).
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
