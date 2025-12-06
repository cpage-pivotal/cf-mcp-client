import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { SidenavService } from '../../services/sidenav.service';
import { PlatformMetrics } from '../app.component';

@Component({
  selector: 'app-bottom-navigation',
  standalone: true,
  imports: [NgClass, MatIcon],
  templateUrl: './bottom-navigation.html',
  styleUrl: './bottom-navigation.css'
})
export class BottomNavigationComponent {
  @Input() metrics: PlatformMetrics = {
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
    },
    a2aAgents: [],
    memoryType: 'TRANSIENT'
  };

  constructor(private sidenavService: SidenavService) {}

  // Bottom navigation items (same as navigation rail for consistency)
  navigationItems = [
    {
      id: 'chat',
      icon: 'chat',
      label: 'Chat',
      tooltip: 'Chat Model Status'
    },
    {
      id: 'document',
      icon: 'description',
      label: 'Docs',
      tooltip: 'Document Management'
    },
    {
      id: 'mcp-servers',
      icon: 'hub',
      label: 'MCP',
      tooltip: 'MCP Server Connections'
    },
    {
      id: 'agents',
      icon: 'smart_toy',
      label: 'Agents',
      tooltip: 'A2A Agent Connections'
    },
    {
      id: 'memory',
      icon: 'psychology',
      label: 'Memory',
      tooltip: 'Conversation Memory'
    }
  ];

  onNavItemClick(itemId: string): void {
    this.sidenavService.toggle(itemId);
  }

  // Helper method to get status indicator for each nav item
  getStatusIndicator(itemId: string): { show: boolean; color: string; icon: string } {
    switch (itemId) {
      case 'chat':
        return {
          show: true,
          color: this.metrics.chatModel ? 'status-green' : 'status-red',
          icon: this.metrics.chatModel ? 'check_circle' : 'error'
        };
      case 'document':
        return {
          show: true,
          color: this.metrics.embeddingModel ? 'status-green' : 'status-red',
          icon: this.metrics.embeddingModel ? 'check_circle' : 'error'
        };
      case 'agents':
        const healthyAgents = this.metrics.a2aAgents.filter(agent => agent.healthy).length;
        return {
          show: this.metrics.a2aAgents.length > 0,
          color: this.metrics.a2aAgents.length === 0 ? 'status-red' :
                 healthyAgents === this.metrics.a2aAgents.length ? 'status-green' :
                 healthyAgents > 0 ? 'status-orange' : 'status-red',
          icon: this.metrics.a2aAgents.length === 0 ? 'error' :
                healthyAgents === this.metrics.a2aAgents.length ? 'check_circle' :
                healthyAgents > 0 ? 'warning' : 'error'
        };
      case 'mcp-servers':
        const healthyServers = this.metrics.mcpServers.filter(server => server.healthy).length;
        return {
          show: this.metrics.mcpServers.length > 0,
          color: healthyServers === this.metrics.mcpServers.length ? 'status-green' :
                 healthyServers > 0 ? 'status-orange' : 'status-red',
          icon: healthyServers === this.metrics.mcpServers.length ? 'check_circle' :
                healthyServers > 0 ? 'warning' : 'error'
        };
      case 'memory':
        return {
          show: true,
          color: this.metrics.conversationId ? 'status-green' : 'status-red',
          icon: this.metrics.conversationId ? 'check_circle' : 'error'
        };
      default:
        return { show: false, color: '', icon: '' };
    }
  }
}
