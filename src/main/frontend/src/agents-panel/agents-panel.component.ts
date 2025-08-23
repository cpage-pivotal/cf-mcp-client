import { Component, Input, OnDestroy, computed, signal, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { Subscription } from 'rxjs';

import { AgentService, AgentMessageEvent } from '../services/agent.service';
import { PlatformMetrics, AgentStatus } from '../app/app.component';
import { SidenavService } from '../services/sidenav.service';

@Component({
  selector: 'app-agents-panel',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, FormsModule,
    MatProgressSpinnerModule, MatTooltipModule, MatSidenavModule
  ],
  templateUrl: './agents-panel.component.html',
  styleUrl: './agents-panel.component.css'
})
export class AgentsPanelComponent implements OnDestroy, AfterViewInit {
  @Input({ required: true }) set metrics(value: PlatformMetrics) {
    this._metrics.set(value);
  }

  @ViewChild('sidenav') sidenav!: MatSidenav;

  // Modern Angular signals for reactive state management
  private readonly _metrics = signal<PlatformMetrics>({
    conversationId: '',
    chatModel: '',
    embeddingModel: '',
    vectorStoreName: '',
    mcpServers: [],
    agentStatus: {
      connectionStatus: 'disconnected',
      activeHandlers: 0,
      implementation: 'none',
      available: false,
      message: 'Not initialized'
    },
    prompts: {
      totalPrompts: 0,
      serversWithPrompts: 0,
      available: false,
      promptsByServer: {}
    }
  });

  private readonly _messages = signal<AgentMessage[]>([]);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _currentPrompt = signal<string>('');

  // Computed signals for reactive UI updates
  readonly agentStatus = computed(() => this._metrics().agentStatus);
  readonly isConnected = computed(() => this.agentStatus().available);
  readonly messages = computed(() => this._messages());
  readonly isLoading = computed(() => this._isLoading());
  readonly currentPrompt = computed(() => this._currentPrompt());

  // Available agents - could be made dynamic from backend in the future
  readonly availableAgents = ['reviewer'];
  selectedAgent = 'reviewer';

  private messageSubscription?: Subscription;

  constructor(
    private readonly agentService: AgentService,
    private readonly sidenavService: SidenavService
  ) {}

  ngAfterViewInit(): void {
    this.sidenavService.registerSidenav('agents', this.sidenav);
  }

  ngOnDestroy(): void {
    this.messageSubscription?.unsubscribe();
  }

  toggleSidenav() {
    this.sidenavService.toggle('agents');
  }

  /**
   * Updates the current prompt value.
   */
  updatePrompt(value: string): void {
    this._currentPrompt.set(value);
  }

  /**
   * Sends a message to the selected agent via message queue.
   * Updated to use signals for state management.
   */
  sendMessage(): void {
    const prompt = this.currentPrompt().trim();
    if (!prompt || this.isLoading()) {
      return;
    }

    const userMessage: AgentMessage = {
      content: prompt,
      sender: 'user',
      timestamp: Date.now(),
      agentType: this.selectedAgent
    };

    this._messages.update(messages => [...messages, userMessage]);
    this._isLoading.set(true);
    this._currentPrompt.set('');

    // Send message to agent via message queue
    this.messageSubscription = this.agentService.sendMessage(this.selectedAgent, prompt)
      .subscribe({
        next: (event) => {
          if ('error' in event && event.error) {
            // Handle error response
            const errorMessage: AgentMessage = {
              content: `Error: ${event.message}`,
              sender: 'agent',
              timestamp: event.timestamp,
              agentType: this.selectedAgent,
              isError: true
            };
            this._messages.update(messages => [...messages, errorMessage]);
            this._isLoading.set(false);
          } else {
            // Handle normal agent response
            const agentEvent = event as AgentMessageEvent;
            const agentMessage: AgentMessage = {
              content: agentEvent.content,
              sender: 'agent',
              timestamp: agentEvent.timestamp,
              agentType: agentEvent.agentType,
              isComplete: agentEvent.isComplete
            };
            this._messages.update(messages => [...messages, agentMessage]);

            if (agentEvent.isComplete) {
              this._isLoading.set(false);
            }
          }
        },
        error: (error) => {
          console.error('Agent communication error:', error);
          const errorMessage: AgentMessage = {
            content: 'Failed to communicate with agent. Please try again.',
            sender: 'agent',
            timestamp: Date.now(),
            agentType: this.selectedAgent,
            isError: true
          };
          this._messages.update(messages => [...messages, errorMessage]);
          this._isLoading.set(false);
        },
        complete: () => {
          this._isLoading.set(false);
        }
      });
  }

  /**
   * Handles Enter key press in the input field.
   */
  onKeyPress(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  /**
   * Clears the conversation history.
   */
  clearMessages(): void {
    this._messages.set([]);
  }

  /**
   * TrackBy function for agent list performance optimization.
   */
  trackByAgent(index: number, agent: string): string {
    return agent;
  }

  /**
   * TrackBy function for message list performance optimization.
   */
  trackByMessage(index: number, message: AgentMessage): number {
    return message.timestamp;
  }
}

/**
 * Interface for agent messages in the conversation.
 */
interface AgentMessage {
  content: string;
  sender: 'user' | 'agent';
  timestamp: number;
  agentType: string;
  isComplete?: boolean;
  isError?: boolean;
}
