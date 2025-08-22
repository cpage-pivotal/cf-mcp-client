import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

/**
 * Agent request interface matching the backend AgentRequest structure.
 */
export interface AgentRequest {
  correlationId: string;
  agentType: string;
  prompt: string;
  timestamp: number;
  userId?: string;
  context?: any;
}

/**
 * Agent response interface matching the backend AgentResponse structure.
 * Simplified to only use isComplete flag.
 */
export interface AgentResponse {
  correlationId: string;
  agentType: string;
  content: string;
  timestamp: number;
  isComplete: boolean;  // true = final response, false = more responses coming
  metadata?: any;
}

/**
 * SSE event data structure for agent messages.
 */
export interface AgentMessageEvent {
  content: string;
  agentType: string;
  timestamp: number;
  isComplete: boolean;
  correlationId?: string;
  responseIndex?: number;
}

/**
 * Agent connection status interface.
 */
export interface AgentStatus {
  connectionStatus: 'connected' | 'disconnected' | 'error';
  activeHandlers: number;
}

/**
 * Service for communicating with AI agents via the backend message queue.
 * Implements SSE streaming for real-time responses as specified in AGENTS.md.
 */
@Injectable({
  providedIn: 'root'
})
export class AgentService {

  constructor(private apiService: ApiService) { }

  /**
   * Sends a message to an agent and returns an Observable for streaming responses.
   * Uses Server-Sent Events (SSE) for real-time communication.
   * Each response is delivered immediately as a separate event.
   *
   * @param agentType The type of agent (e.g., 'reviewer')
   * @param prompt The message/prompt to send to the agent
   * @returns Observable that emits agent response events
   */
  sendMessage(agentType: string, prompt: string): Observable<AgentMessageEvent | { error: boolean; message: string; timestamp: number }> {
    return new Observable(observer => {
      const params = new URLSearchParams({
        agentType: agentType,
        prompt: prompt
      });

      const url = this.apiService.getApiUrl(`/api/agents/chat?${params.toString()}`);
      const eventSource = new EventSource(url);

      // Handle agent message events
      eventSource.addEventListener('agent-message', (event: MessageEvent) => {
        try {
          if (!event.data || event.data.trim() === '') {
            console.warn('Received empty agent message event');
            return;
          }
          const data: AgentMessageEvent = JSON.parse(event.data);
          observer.next(data);
        } catch (error) {
          console.error('Failed to parse agent message:', error, 'Raw data:', event.data);
          observer.error(error);
        }
      });

      // Handle error events
      eventSource.addEventListener('error', (event: MessageEvent) => {
        try {
          // Check if event.data exists and is not empty
          if (event.data && event.data.trim() !== '') {
            const errorData = JSON.parse(event.data);
            observer.next(errorData);
          } else {
            // Handle generic EventSource errors (connection issues, etc.)
            console.warn('SSE connection error or empty error event');
            observer.next({
              error: true,
              message: 'Connection error or server unavailable',
              timestamp: Date.now()
            });
          }
        } catch (error) {
          console.error('Failed to parse error message:', error, 'Raw data:', event.data);
          observer.next({
            error: true,
            message: 'Failed to parse error response',
            timestamp: Date.now()
          });
        }
      });

      // Handle connection close
      eventSource.addEventListener('close', () => {
        eventSource.close();
        observer.complete();
      });

      // Handle connection errors
      eventSource.onerror = (error) => {
        console.warn('Agent SSE connection error - this is normal if the agent system is not fully configured:', error);
        eventSource.close();
        observer.next({
          error: true,
          message: 'Agent connection failed - agent system may not be available',
          timestamp: Date.now()
        });
        observer.complete();
      };

      // Cleanup function
      return () => {
        eventSource.close();
      };
    });
  }

  /**
   * Gets the current status of the agent messaging system.
   *
   * @returns Promise resolving to agent status information
   */
  async getAgentStatus(): Promise<AgentStatus> {
    try {
      const url = this.apiService.getApiUrl('/api/agents/status');
      const response = await fetch(url);
      if (!response.ok) {
        console.warn(`Agent status endpoint returned ${response.status}: ${response.statusText}`);
        return {
          connectionStatus: 'disconnected',
          activeHandlers: 0
        };
      }

      const contentType = response.headers.get('content-type');
      if (!contentType || !contentType.includes('application/json')) {
        console.warn('Agent status endpoint returned non-JSON response:', contentType);
        return {
          connectionStatus: 'error',
          activeHandlers: 0
        };
      }

      return await response.json();
    } catch (error) {
      console.warn('Failed to get agent status (agent system may not be available):', error);
      return {
        connectionStatus: 'disconnected',
        activeHandlers: 0
      };
    }
  }

  /**
   * Checks if a specific agent type is available.
   * Currently supports 'reviewer' agent as specified in AGENTS.md.
   *
   * @param agentType The agent type to check
   * @returns True if the agent type is supported
   */
  isAgentSupported(agentType: string): boolean {
    const supportedAgents = ['reviewer'];
    return supportedAgents.includes(agentType.toLowerCase());
  }

  /**
   * Gets the list of available agent types.
   *
   * @returns Array of supported agent types
   */
  getAvailableAgents(): string[] {
    return ['reviewer'];
  }
}
