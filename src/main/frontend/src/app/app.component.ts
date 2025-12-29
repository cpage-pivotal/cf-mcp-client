import { Component, inject, signal, effect, ViewChild } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { ChatPanelComponent } from '../chat-panel/chat-panel.component';
import { MemoryPanelComponent } from '../memory-panel/memory-panel.component';
import { DocumentPanelComponent } from '../document-panel/document-panel.component';
import { McpServersPanelComponent } from '../mcp-servers-panel/mcp-servers-panel.component';
import { AgentsPanel } from '../agents-panel/agents-panel';
import { ChatboxComponent } from '../chatbox/chatbox.component';
import { NavigationRailComponent } from './navigation-rail/navigation-rail.component';
import { BottomNavigationComponent } from './bottom-navigation/bottom-navigation';
import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { interval } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { startWith, switchMap, retry, shareReplay, catchError, of } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [MatToolbar, ChatPanelComponent, MemoryPanelComponent, DocumentPanelComponent, McpServersPanelComponent, AgentsPanel, ChatboxComponent, NavigationRailComponent, BottomNavigationComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'pulseui';

  // ViewChild references for components
  @ViewChild(ChatboxComponent) chatbox?: ChatboxComponent;

  // Use signals for reactive state management
  private readonly _currentDocumentIds = signal<string[]>([]);

  // Public readonly signal for document IDs
  readonly currentDocumentIds = this._currentDocumentIds.asReadonly();

  private readonly httpClient = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  // Reactive metrics polling using RxJS interop (zoneless-friendly pattern)
  private readonly metricsPolling$ = interval(5000).pipe(
    startWith(0), // Fetch immediately on startup
    switchMap(() => {
      const { protocol, host } = this.getApiBaseUrl();
      return this.httpClient.get<PlatformMetrics>(`${protocol}//${host}/metrics`).pipe(
        retry({ count: 3, delay: 1000 }),
        catchError((error) => {
          console.error('Error fetching metrics:', error);
          // Return fallback value on error
          return of({
            conversationId: '',
            chatModel: '',
            embeddingModel: '',
            vectorStoreName: '',
            mcpServers: [],
            a2aAgents: [],
            memoryType: 'TRANSIENT' as const
          });
        })
      );
    }),
    shareReplay(1)
  );

  // Convert observable to signal - automatically manages subscription lifecycle
  readonly metrics = toSignal(this.metricsPolling$, {
    initialValue: {
      conversationId: '',
      chatModel: '',
      embeddingModel: '',
      vectorStoreName: '',
      mcpServers: [],
      a2aAgents: [],
      memoryType: 'TRANSIENT' as const
    }
  });

  constructor() {
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

  // Method to handle agent message sending
  onAgentMessageSent(agent: A2AAgent, message: string): void {
    this.chatbox?.sendMessageToAgent(agent, message);
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
  protocol?: {
    type: string;
    displayName: string;
    bindingKey: string;
  }; // New optional field for protocol object
}

export interface AgentCapabilities {
  streaming: boolean;
  pushNotifications: boolean;
  stateTransitionHistory: boolean;
}

export interface A2AAgent {
  serviceName: string;
  agentName: string;
  description: string;
  version: string;
  agentCardUri: string;
  healthy: boolean;
  errorMessage?: string;
  capabilities: AgentCapabilities;
}

export interface PlatformMetrics {
  conversationId: string;
  chatModel: string;
  embeddingModel: string;
  vectorStoreName: string;
  mcpServers: McpServer[];
  a2aAgents: A2AAgent[];
  memoryType: string;
}
