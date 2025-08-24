import { Component, Input, OnDestroy, computed, signal, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatRadioModule } from '@angular/material/radio';

import { PlatformMetrics, AgentStatus } from '../app/app.component';
import { SidenavService } from '../services/sidenav.service';
import { AgentSelectionService, AgentInfo } from '../services/agent-selection.service';

@Component({
  selector: 'app-agents-panel',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatButtonModule, MatIconModule,
    MatTooltipModule, MatSidenavModule, MatListModule, MatRadioModule
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


  // Computed signals for reactive UI updates
  readonly agentStatus = computed(() => this._metrics().agentStatus);
  readonly isConnected = computed(() => this.agentStatus().available);
  readonly availableAgents = computed(() => this.agentSelectionService.availableAgents());
  readonly selectedAgent = computed(() => this.agentSelectionService.selectedAgent());


  constructor(
    private readonly sidenavService: SidenavService,
    public readonly agentSelectionService: AgentSelectionService
  ) {}

  ngAfterViewInit(): void {
    this.sidenavService.registerSidenav('agents', this.sidenav);
  }

  ngOnDestroy(): void {}

  toggleSidenav() {
    this.sidenavService.toggle('agents');
  }

  /**
   * Selects an agent for conversation.
   */
  selectAgent(agent: AgentInfo): void {
    if (this.selectedAgent()?.id === agent.id) {
      // Deselect if clicking the same agent
      this.agentSelectionService.deselectAgent();
    } else {
      // Select the clicked agent
      this.agentSelectionService.selectAgent(agent);
      // Close the agents panel
      this.sidenavService.close('agents');
    }
  }

  /**
   * Checks if an agent is currently selected.
   */
  isAgentSelected(agent: AgentInfo): boolean {
    return this.selectedAgent()?.id === agent.id;
  }

  /**
   * TrackBy function for agent list performance optimization.
   */
  trackByAgent(index: number, agent: AgentInfo): string {
    return agent.id;
  }
}

