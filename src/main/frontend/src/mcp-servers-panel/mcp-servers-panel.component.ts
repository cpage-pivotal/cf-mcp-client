import { Component, Input, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatListModule } from '@angular/material/list';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { PlatformMetrics, McpServer } from '../app/app.component';
import { SidenavService } from '../services/sidenav.service';
import { ToolsModalComponent } from '../tools-modal/tools-modal.component';

@Component({
  selector: 'app-mcp-servers-panel',
  standalone: true,
  imports: [
    CommonModule,
    MatSidenavModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatListModule,
    MatDialogModule,
    MatChipsModule
  ],
  templateUrl: './mcp-servers-panel.component.html',
  styleUrl: './mcp-servers-panel.component.css'
})
export class McpServersPanelComponent implements AfterViewInit {
  @Input() metrics!: PlatformMetrics;

  @ViewChild('sidenav') sidenav!: MatSidenav;

  constructor(
    private sidenavService: SidenavService,
    private dialog: MatDialog
  ) {}

  ngAfterViewInit(): void {
    this.sidenavService.registerSidenav('mcp-servers', this.sidenav);
  }

  toggleSidenav() {
    this.sidenavService.toggle('mcp-servers');
  }

  get sortedMcpServers(): McpServer[] {
    if (!this.metrics || !this.metrics.mcpServers) {
      return [];
    }

    const healthyServers = this.metrics.mcpServers
      .filter(server => server.healthy)
      .sort((a, b) => a.name.localeCompare(b.name));

    const unhealthyServers = this.metrics.mcpServers
      .filter(server => !server.healthy)
      .sort((a, b) => a.name.localeCompare(b.name));

    return [...healthyServers, ...unhealthyServers];
  }

  showMcpServerTools(mcpServer: McpServer): void {
    if (!mcpServer.healthy) {
      return;
    }

    this.dialog.open(ToolsModalComponent, {
      data: { mcpServer: mcpServer },
      width: '90vw', // Responsive width
      maxWidth: '600px', // Maximum width constraint
      maxHeight: '80vh',
      panelClass: 'custom-dialog-container'
    });
  }
  getOverallStatusClass(): string {
    if (this.metrics.mcpServers.length === 0) {
      return 'status-red';
    }

    const hasUnhealthy = this.metrics.mcpServers.some(server => !server.healthy);
    const hasHealthy = this.metrics.mcpServers.some(server => server.healthy);

    if (hasUnhealthy && hasHealthy) {
      return 'status-orange'; // Mixed health status
    } else if (hasHealthy) {
      return 'status-green'; // All healthy
    } else {
      return 'status-red'; // All unhealthy
    }
  }

  getOverallStatusIcon(): string {
    if (this.metrics.mcpServers.length === 0) {
      return 'error';
    }

    const hasUnhealthy = this.metrics.mcpServers.some(server => !server.healthy);
    const hasHealthy = this.metrics.mcpServers.some(server => server.healthy);

    if (hasUnhealthy && hasHealthy) {
      return 'warning'; // Mixed health status
    } else if (hasHealthy) {
      return 'check_circle'; // All healthy
    } else {
      return 'error'; // All unhealthy
    }
  }

  getOverallStatusText(): string {
    if (this.metrics.mcpServers.length === 0) {
      return 'Not Available';
    }

    const healthyCount = this.metrics.mcpServers.filter(server => server.healthy).length;
    const totalCount = this.metrics.mcpServers.length;

    if (healthyCount === totalCount) {
      return 'All Healthy';
    } else if (healthyCount === 0) {
      return 'All Unhealthy';
    } else {
      return `${healthyCount}/${totalCount} Healthy`;
    }
  }

  getProtocolDisplayName(protocol?: { type: string; displayName: string; bindingKey: string } | string): string {
    if (!protocol) {
      return 'SSE'; // Default for backward compatibility
    }
    
    // Handle new object format
    if (typeof protocol === 'object') {
      return protocol.displayName;
    }
    
    // Handle legacy string format for backward compatibility
    switch (protocol) {
      case 'SSE': return 'SSE';
      case 'STREAMABLE_HTTP': return 'Streamable HTTP';
      default: return 'SSE';
    }
  }

  isSSEProtocol(protocol?: { type: string; displayName: string; bindingKey: string } | string): boolean {
    if (!protocol) {
      return true; // Default to SSE for backward compatibility
    }
    
    if (typeof protocol === 'object') {
      return protocol.type === 'SSE' || protocol.type === 'Legacy';
    }
    
    return protocol === 'SSE';
  }

  isStreamableHttpProtocol(protocol?: { type: string; displayName: string; bindingKey: string } | string): boolean {
    if (!protocol) {
      return false;
    }
    
    if (typeof protocol === 'object') {
      return protocol.type === 'StreamableHttp';
    }
    
    return protocol === 'STREAMABLE_HTTP';
  }
}