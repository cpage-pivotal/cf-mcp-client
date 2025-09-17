import { Component, Input, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatListModule } from '@angular/material/list';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
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
    MatChipsModule,
    MatCardModule
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

  toggleSidenav(): void {
    this.sidenavService.toggle('mcp-servers');
  }

  onSidenavOpenedChange(opened: boolean): void {
    if (!opened) {
      // Sidenav was closed (e.g., by backdrop click) - update service state
      this.sidenavService.notifyPanelClosed('mcp-servers');
    }
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
      data: { mcpServer },
      width: '90vw',
      maxWidth: '600px',
      maxHeight: '80vh',
      panelClass: 'custom-dialog-container'
    });
  }

  getOverallStatusClass(): string {
    if (!this.metrics?.mcpServers || this.metrics.mcpServers.length === 0) {
      return 'status-red';
    }

    const hasUnhealthy = this.metrics.mcpServers.some(server => !server.healthy);
    const hasHealthy = this.metrics.mcpServers.some(server => server.healthy);

    if (hasUnhealthy && hasHealthy) {
      return 'status-orange';
    } else if (hasHealthy) {
      return 'status-green';
    } else {
      return 'status-red';
    }
  }

  getOverallStatusIcon(): string {
    if (!this.metrics?.mcpServers || this.metrics.mcpServers.length === 0) {
      return 'error';
    }

    const hasUnhealthy = this.metrics.mcpServers.some(server => !server.healthy);
    const hasHealthy = this.metrics.mcpServers.some(server => server.healthy);

    if (hasUnhealthy && hasHealthy) {
      return 'warning';
    } else if (hasHealthy) {
      return 'check_circle';
    } else {
      return 'error';
    }
  }

  getOverallStatusText(): string {
    if (!this.metrics?.mcpServers || this.metrics.mcpServers.length === 0) {
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

  // Fixed to handle the actual protocol structure from backend
  getProtocolDisplayName(protocol?: any): string {
    if (!protocol) return 'SSE';
    // Handle both function and string cases
    if (typeof protocol.displayName === 'function') {
      return protocol.displayName();
    }
    return protocol.displayName || 'SSE';
  }

  isSSEProtocol(protocol?: any): boolean {
    if (!protocol) return true; // Default to SSE
    const displayName = typeof protocol.displayName === 'function'
      ? protocol.displayName()
      : protocol.displayName;
    return displayName === 'SSE';
  }

  isStreamableHttpProtocol(protocol?: any): boolean {
    if (!protocol) return false;
    const displayName = typeof protocol.displayName === 'function'
      ? protocol.displayName()
      : protocol.displayName;
    return displayName === 'Streamable HTTP';
  }

  // Track function for ngFor optimization
  trackByServerName(index: number, server: McpServer): string {
    return server.name || server.serverName || index.toString();
  }
}
