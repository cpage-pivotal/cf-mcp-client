import {Component, DestroyRef, Inject, inject, ViewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpClient} from '@angular/common/http';
import {CommonModule, DOCUMENT} from '@angular/common';
import {MatSidenav, MatSidenavModule} from '@angular/material/sidenav';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {interval} from 'rxjs';

@Component({
  selector: 'app-memory-panel',
  imports: [
    CommonModule, MatSidenavModule, MatButtonModule, MatIconModule, FormsModule, MatFormFieldModule, MatInputModule
  ],
  templateUrl: './memory-panel.component.html',
  styleUrl: './memory-panel.component.css'
})
export class MemoryPanelComponent {
  metrics: MemoryMetrics = {
    contextSize: 0,
    humanBlockValue: '',
    personaBlockValue: ''
  };
  disabled = false;
  conversationId: string = 'default';
  private destroyRef = inject(DestroyRef);

  @ViewChild('sidenav') sidenav!: MatSidenav;

  constructor(private httpClient: HttpClient, @Inject(DOCUMENT) private document: Document) {
    this.initMetricsPolling();
  }

  private initMetricsPolling(): void {
    // Set up interval to fetch metrics every 5 seconds
    // Using takeUntilDestroyed operator for automatic cleanup
    interval(5000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.fetchMemoryMetrics();
      });
  }

  toggleSidenav() {
    if (!this.disabled) {
      this.sidenav.toggle();
    }
  }

  fetchMemoryMetrics() {
    let host: string;
    let protocol: string;

    if (this.document.location.hostname == 'localhost') {
      host = 'localhost:8080';
    } else {
      host = this.document.location.host;
    }
    protocol = this.document.location.protocol;

    this.httpClient.get<MemoryMetrics>(`${protocol}//${host}/memory/metrics/${this.conversationId}`)
      .subscribe({
        next: (data) => {
          this.metrics = data;
          this.disabled = false;
        },
        error: (error) => {
          console.error('Error fetching memory metrics:', error);
          if (error.status === 501) {
            this.disabled = true;
          }
        }
      });
  }
}

interface MemoryMetrics {
  contextSize: number;
  humanBlockValue: string;
  personaBlockValue: string;
}
