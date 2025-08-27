import { Component, DestroyRef, computed, signal, inject, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

import { ChatPanelComponent } from '../chat-panel/chat-panel.component';
import { MemoryPanelComponent } from '../memory-panel/memory-panel.component';
import { DocumentPanelComponent } from '../document-panel/document-panel.component';
import { McpServersPanelComponent } from '../mcp-servers-panel/mcp-servers-panel.component';
import { AgentsPanelComponent } from '../agents-panel/agents-panel.component';
import { ChatboxComponent } from '../chatbox/chatbox.component';
import { ApiService } from '../services/api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbar,
    MatIconButton,
    MatIcon,
    ChatPanelComponent,
    MemoryPanelComponent,
    DocumentPanelComponent,
    McpServersPanelComponent,
    AgentsPanelComponent,
    ChatboxComponent
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'frontend';

  // Modern Angular signals for reactive state management
  private readonly _currentDocumentIds = signal<string[]>([]);
  private readonly _motivationalMessage = signal<string>('');
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

  // Public readonly signals
  readonly currentDocumentIds = this._currentDocumentIds.asReadonly();
  readonly motivationalMessage = this._motivationalMessage.asReadonly();
  readonly metrics = this._metrics.asReadonly();

  // Computed properties for specific metric aspects
  readonly agentConnected = computed(() => this._metrics().agentStatus.available);
  readonly vectorStoreAvailable = computed(() => this._metrics().vectorStoreName !== '');
  readonly embeddingModelAvailable = computed(() => this._metrics().embeddingModel !== '');

  private readonly destroyRef = inject(DestroyRef);
  private readonly httpClient = inject(HttpClient);
  private readonly apiService = inject(ApiService);

  // Predefined motivational messages
  private readonly motivationalMessages = [
    "You're doing amazing! Keep pushing forward! 🚀",
    "Every great achievement starts with a single step! ✨",
    "Believe in yourself - you've got this! 💪",
    "Success is the sum of small efforts repeated day in and day out! 🌟",
    "The only way to do great work is to love what you do! ❤️"
  ];

  constructor() {
    this.initMetricsPolling();

    // Use effect for side effects based on signal changes
    effect(() => {
      const documentIds = this.currentDocumentIds();
      if (documentIds.length > 0) {
        console.log('Documents selected with IDs:', documentIds);
      }
    });
  }

  // Method to handle document selection from DocumentPanelComponent
  onDocumentIdsChanged(documentIds: string[]): void {
    this._currentDocumentIds.set([...documentIds]);
  }

  // Method to show a random motivational message
  showMotivation(): void {
    const randomIndex = Math.floor(Math.random() * this.motivationalMessages.length);
    const randomMessage = this.motivationalMessages[randomIndex];
    
    this._motivationalMessage.set(randomMessage);
    
    // Clear the message after 3 seconds
    setTimeout(() => {
      this._motivationalMessage.set('');
    }, 3000);
  }

  // Initialize metrics polling with improved error handling
  private initMetricsPolling(): void {
    // Fetch initial metrics
    this.fetchMetrics();

    // Set up interval to fetch metrics every 5 seconds
    interval(5000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.fetchMetrics();
      });
  }

  private fetchMetrics(): void {
    const { protocol, host } = this.getApiBaseUrl();

    this.httpClient.get<PlatformMetrics>(`${protocol}//${host}/metrics`)
      .subscribe({
        next: (data) => {
          this._metrics.set(data);
        },
        error: (error) => {
          console.error('Error fetching metrics:', error);
        }
      });
  }

  private getApiBaseUrl(): { protocol: string; host: string } {
    return {
      protocol: this.apiService.getProtocol(),
      host: this.apiService.getHost()
    };
  }
}

export interface Tool {
  name: string;
  description: string;
}

export interface McpServer {
  name: string;
  serverName: string;
  healthy: boolean;
  tools: Tool[];
}

export interface PromptArgument {
  name: string;
  description: string;
  required: boolean;
  defaultValue?: any;
  schema?: any;
}

export interface McpPrompt {
  serverId: string;
  serverName: string;
  name: string;
  description: string;
  arguments: PromptArgument[];
}

export interface EnhancedPromptMetrics {
  totalPrompts: number;
  serversWithPrompts: number;
  available: boolean;
  promptsByServer: { [serverId: string]: McpPrompt[] };
}

export interface AgentStatus {
  connectionStatus: string;
  activeHandlers: number;
  implementation: string;
  available: boolean;
  message: string;
}

export interface PlatformMetrics {
  conversationId: string;
  chatModel: string;
  embeddingModel: string;
  vectorStoreName: string;
  mcpServers: McpServer[];
  agentStatus: AgentStatus;    // ← ADD THIS LINE
  prompts: EnhancedPromptMetrics;
}
