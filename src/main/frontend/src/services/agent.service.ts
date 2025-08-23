import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

/**
 * Agent communication service interface.
 * Updated to focus solely on message communication since status is now handled
 * through the centralized metrics system.
 */
export interface AgentMessageEvent {
  content: string;
  agentType: string;
  timestamp: number;
  correlationId?: string;
  responseIndex?: number;
  isComplete?: boolean;
  metadata?: any;
}

/**
 * Service for agent communication.
 * Implements SSE streaming for real-time responses as specified in AGENTS.md.
 * Agent status information is now retrieved through the centralized MetricsController.
 */
@Injectable({
  providedIn: 'root'
})
export class AgentService {

  constructor(private readonly apiService: ApiService) { }

  /**
   * Sends a message to an agent and returns an Observable for streaming responses.
   * Uses Server-Sent Events (SSE) for real-time communication.
   * Each response is delivered immediately as a separate event.
   *
   * @param agentType The type of agent (e.g., 'reviewer')
   * @param prompt The message/prompt to send to the agent
   * @returns Observable that emits agent response events or error events
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

  isAgentSupported(agentType: string): boolean {
    const supportedAgents = ['reviewer'];
    return supportedAgents.includes(agentType.toLowerCase());
  }

  getAvailableAgents(): string[] {
    return ['reviewer'];
  }
}
