import {Component, Input, ViewChild, AfterViewInit, OnChanges, SimpleChanges, OnDestroy} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatSidenav, MatSidenavModule} from '@angular/material/sidenav';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {PlatformMetrics} from '../app/app.component';
import {SidenavService} from '../services/sidenav.service';
import {AgentService, AgentMessageEvent, AgentStatus} from '../services/agent.service';
import {Subscription} from 'rxjs';

@Component({
  selector: 'app-agents-panel',
  standalone: true,
  imports: [
    CommonModule,
    MatSidenavModule,
    MatButtonModule,
    MatIconModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatTooltipModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './agents-panel.component.html',
  styleUrl: './agents-panel.component.css'
})
export class AgentsPanelComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() metrics!: PlatformMetrics;

  @ViewChild('sidenav') sidenav!: MatSidenav;

  selectedAgent: string = 'reviewer';
  isConnected: boolean = false;
  currentPrompt: string = '';
  isLoading: boolean = false;
  messages: AgentMessage[] = [];
  
  private statusSubscription?: Subscription;
  private messageSubscription?: Subscription;

  constructor(
    private sidenavService: SidenavService,
    private agentService: AgentService
  ) {}

  ngAfterViewInit(): void {
    this.sidenavService.registerSidenav('agents', this.sidenav);
    this.checkAgentStatus();
    
    // Check agent status periodically
    setInterval(() => this.checkAgentStatus(), 30000); // Check every 30 seconds
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['metrics'] && this.metrics) {
      // Future: Update agent-related metrics
    }
  }

  ngOnDestroy(): void {
    this.statusSubscription?.unsubscribe();
    this.messageSubscription?.unsubscribe();
  }

  toggleSidenav() {
    this.sidenavService.toggle('agents');
  }

  /**
   * Sends a message to the selected agent.
   */
  sendMessage(): void {
    if (!this.currentPrompt.trim() || this.isLoading) {
      return;
    }

    const userMessage: AgentMessage = {
      content: this.currentPrompt,
      sender: 'user',
      timestamp: Date.now(),
      agentType: this.selectedAgent
    };

    this.messages.push(userMessage);
    this.isLoading = true;

    const prompt = this.currentPrompt;
    this.currentPrompt = '';

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
            this.messages.push(errorMessage);
            this.isLoading = false;
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
            this.messages.push(agentMessage);
            
            if (agentEvent.isComplete) {
              this.isLoading = false;
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
          this.messages.push(errorMessage);
          this.isLoading = false;
        },
        complete: () => {
          this.isLoading = false;
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
    this.messages = [];
  }

  /**
   * Checks the agent system status and updates connection state.
   */
  private async checkAgentStatus(): Promise<void> {
    try {
      const status: AgentStatus = await this.agentService.getAgentStatus();
      this.isConnected = status.connectionStatus === 'connected';
    } catch (error) {
      console.error('Failed to check agent status:', error);
      this.isConnected = false;
    }
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