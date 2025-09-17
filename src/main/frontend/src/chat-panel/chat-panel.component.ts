import { Component, Input, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { PlatformMetrics } from '../app/app.component';
import { SidenavService } from '../services/sidenav.service';

@Component({
  selector: 'app-chat-panel',
  standalone: true,
  imports: [
    CommonModule,
    MatSidenavModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatChipsModule
  ],
  templateUrl: './chat-panel.component.html',
  styleUrl: './chat-panel.component.css'
})
export class ChatPanelComponent implements AfterViewInit {
  @Input() metrics!: PlatformMetrics;

  @ViewChild('sidenav') sidenav!: MatSidenav;

  constructor(private sidenavService: SidenavService) {}

  ngAfterViewInit(): void {
    this.sidenavService.registerSidenav('chat', this.sidenav);
  }

  toggleSidenav() {
    this.sidenavService.toggle('chat');
  }

  onSidenavOpenedChange(opened: boolean) {
    if (!opened) {
      // Sidenav was closed (e.g., by backdrop click) - update service state
      this.sidenavService.notifyPanelClosed('chat');
    }
  }
}
