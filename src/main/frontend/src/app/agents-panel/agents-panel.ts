import { AfterViewInit, Component, input, output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSidenavModule, MatSidenav } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { SidenavService } from '../../services/sidenav.service';
import { PlatformMetrics, A2AAgent } from '../app.component';
import { AgentMessageDialog, AgentMessageDialogData, AgentMessageDialogResult } from '../agent-message-dialog/agent-message-dialog';

export interface AgentMessageEvent {
  agent: A2AAgent;
  message: string;
}

@Component({
  selector: 'app-agents-panel',
  standalone: true,
  imports: [
    CommonModule,
    MatSidenavModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatCardModule,
    MatChipsModule,
    MatTooltipModule,
    MatDialogModule
  ],
  templateUrl: './agents-panel.html',
  styleUrl: './agents-panel.css'
})
export class AgentsPanel implements AfterViewInit {
  metrics = input.required<PlatformMetrics>();
  messageSent = output<AgentMessageEvent>();

  @ViewChild('sidenav') sidenav!: MatSidenav;

  constructor(
    private sidenavService: SidenavService,
    private dialog: MatDialog
  ) {}

  ngAfterViewInit(): void {
    // Register this sidenav with the service
    this.sidenavService.registerSidenav('agents', this.sidenav);
  }

  toggleSidenav(): void {
    this.sidenavService.toggle('agents');
  }

  onSidenavOpenedChange(opened: boolean): void {
    if (!opened) {
      this.sidenavService.close('agents');
    }
  }

  get sortedA2AAgents(): A2AAgent[] {
    const agents = this.metrics().a2aAgents || [];

    // Sort: healthy agents first, then by name
    return [...agents].sort((a, b) => {
      // Healthy agents come first
      if (a.healthy !== b.healthy) {
        return a.healthy ? -1 : 1;
      }
      // Then sort by name
      return a.agentName.localeCompare(b.agentName);
    });
  }

  openAgentMessageDialog(agent: A2AAgent): void {
    const dialogData: AgentMessageDialogData = { agent };

    const dialogRef = this.dialog.open(AgentMessageDialog, {
      width: '500px',
      data: dialogData
    });

    dialogRef.afterClosed().subscribe((result: AgentMessageDialogResult | undefined) => {
      if (result) {
        // Close the agents panel
        this.sidenavService.close('agents');

        // Emit event to send message (handled by parent component)
        this.messageSent.emit({
          agent: agent,
          message: result.message
        });
      }
    });
  }

  getOverallStatusClass(): string {
    const agents = this.metrics().a2aAgents || [];

    if (agents.length === 0) {
      return 'status-red';
    }

    const healthyCount = agents.filter(a => a.healthy).length;

    if (healthyCount === agents.length) {
      return 'status-green';
    } else if (healthyCount > 0) {
      return 'status-orange';
    } else {
      return 'status-red';
    }
  }

  getOverallStatusIcon(): string {
    const statusClass = this.getOverallStatusClass();

    switch (statusClass) {
      case 'status-green':
        return 'check_circle';
      case 'status-orange':
        return 'warning';
      case 'status-red':
        return 'error';
      default:
        return 'error';
    }
  }

  getOverallStatusText(): string {
    const agents = this.metrics().a2aAgents || [];

    if (agents.length === 0) {
      return 'No Agents';
    }

    const healthyCount = agents.filter(a => a.healthy).length;
    return `${healthyCount}/${agents.length} Healthy`;
  }
}
