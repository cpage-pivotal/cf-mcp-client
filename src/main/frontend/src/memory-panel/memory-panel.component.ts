import {Component, Input, ViewChild, AfterViewInit, OnChanges, SimpleChanges, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatSidenav, MatSidenavModule} from '@angular/material/sidenav';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatTooltipModule} from "@angular/material/tooltip";
import {MatChipsModule} from '@angular/material/chips';
import {HttpClient} from '@angular/common/http';
import {DOCUMENT} from '@angular/common';
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

  // Track if a memory toggle operation is in progress
  isTogglingMemory: boolean = false;

  @ViewChild('sidenav') sidenav!: MatSidenav;

  private readonly httpClient = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

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

  /**
   * Check if the memory chip is clickable (vector store and embedding model are available)
   */
  isMemoryClickable(): boolean {
    return this.metrics.vectorStoreName !== '' && this.metrics.embeddingModel !== '';
  }

  /**
   * Get the current memory type display text
   */
  getMemoryTypeText(): string {
    return this.metrics.memoryType === 'PERSISTENT' ? 'Persistent' : 'Transient';
  }

  /**
   * Get the current memory icon
   */
  getMemoryIcon(): string {
    return this.metrics.memoryType === 'PERSISTENT' ? 'storage' : 'memory';
  }

  /**
   * Toggle between Transient and Persistent memory
   */
  toggleMemoryType(): void {
    // Don't toggle if requirements aren't met or if already toggling
    if (!this.isMemoryClickable() || this.isTogglingMemory) {
      return;
    }

    this.isTogglingMemory = true;

    // Determine the new memory type
    const newMemoryType = this.metrics.memoryType === 'TRANSIENT' ? 'PERSISTENT' : 'TRANSIENT';

    const { protocol, host } = this.getApiBaseUrl();

    // Call the backend API to update the preference
    this.httpClient.post<{conversationId: string, memoryType: string}>(
      `${protocol}//${host}/api/memory/preference`,
      {
        conversationId: this.conversationId,
        memoryType: newMemoryType
      }
    ).subscribe({
      next: (response) => {
        console.log('Memory type updated successfully:', response);
        // The metrics will be updated on the next polling cycle
        this.isTogglingMemory = false;
      },
      error: (error) => {
        console.error('Error updating memory type:', error);
        this.isTogglingMemory = false;
      }
    });
  }

  /**
   * Get the tooltip text for the memory chip
   */
  getMemoryTooltip(): string {
    if (!this.isMemoryClickable()) {
      return 'Vector store and embedding model required for persistent memory';
    }
    return `Click to switch to ${this.metrics.memoryType === 'TRANSIENT' ? 'Persistent' : 'Transient'} memory`;
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
