import { Component, DestroyRef, Inject, inject, signal, effect } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { CommonModule } from '@angular/common';
import { ChatPanelComponent } from '../chat-panel/chat-panel.component';
import { MemoryPanelComponent } from '../memory-panel/memory-panel.component';
import { DocumentPanelComponent } from '../document-panel/document-panel.component';
import { McpServersPanelComponent } from '../mcp-servers-panel/mcp-servers-panel.component';
import { ChatboxComponent } from '../chatbox/chatbox.component';
import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { interval } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [MatToolbar, MatIconButton, MatIcon, CommonModule, ChatPanelComponent, MemoryPanelComponent, DocumentPanelComponent, McpServersPanelComponent, ChatboxComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'pulseui';

  // Use signals for reactive state management
  private readonly _currentDocumentIds = signal<string[]>([]);
  private readonly _motivationalMessage = signal<string>('');
  private readonly _metrics = signal<PlatformMetrics>({
    conversationId: '',
    chatModel: '',
    embeddingModel: '',
    vectorStoreName: '',
    mcpServers: [],
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

  // Predefined motivational messages
  private readonly motivationalMessages = [
    "🚀 You're absolutely crushing it today! Keep that momentum going!",
    "💪 Every challenge you face is just making you stronger and more awesome!",
    "⭐ Your potential is limitless - you've got this!",
    "🔥 Amazing things happen when you believe in yourself - and we believe in you!",
    "🌟 You're not just building software, you're building the future!"
  ];

  private readonly destroyRef = inject(DestroyRef);
  private readonly httpClient = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

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

  // Method to show random motivational message
  showMotivationalMessage(): void {
    const randomIndex = Math.floor(Math.random() * this.motivationalMessages.length);
    const message = this.motivationalMessages[randomIndex];
    this._motivationalMessage.set(message);
    
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
    let host: string;
    let protocol: string;

    if (this.document.location.hostname === 'localhost') {
      host = 'localhost:8080';
    } else {
      host = this.document.location.host;
    }
    protocol = this.document.location.protocol;

    return { protocol, host };
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

export interface PlatformMetrics {
  conversationId: string;
  chatModel: string;
  embeddingModel: string;
  vectorStoreName: string;
  mcpServers: McpServer[];
  prompts: EnhancedPromptMetrics;
}
