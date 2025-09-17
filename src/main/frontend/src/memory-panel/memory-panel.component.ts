import {Component, Input, ViewChild, AfterViewInit, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatSidenav, MatSidenavModule} from '@angular/material/sidenav';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatChipsModule} from '@angular/material/chips';
import {PlatformMetrics} from '../app/app.component';
import {SidenavService} from '../services/sidenav.service';

@Component({
  selector: 'app-memory-panel',
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
    MatChipsModule
  ],
  templateUrl: './memory-panel.component.html',
  styleUrl: './memory-panel.component.css'
})
export class MemoryPanelComponent implements AfterViewInit, OnChanges {
  // Input properties from the parent (app) component
  @Input() metrics!: PlatformMetrics;

  // Local conversationId property (now read-only from metrics)
  private _conversationId: string = '';
  get conversationId(): string {
    return this._conversationId;
  }

  @ViewChild('sidenav') sidenav!: MatSidenav;

  constructor(private sidenavService: SidenavService) {}

  ngAfterViewInit(): void {
    this.sidenavService.registerSidenav('memory', this.sidenav);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['metrics'] && this.metrics) {
      // Update conversationId when metrics change
      this._conversationId = this.metrics.conversationId;
    }
  }

  toggleSidenav() {
    this.sidenavService.toggle('memory');
  }

  onSidenavOpenedChange(opened: boolean) {
    if (!opened) {
      // Sidenav was closed (e.g., by backdrop click) - update service state
      this.sidenavService.notifyPanelClosed('memory');
    }
  }
}
