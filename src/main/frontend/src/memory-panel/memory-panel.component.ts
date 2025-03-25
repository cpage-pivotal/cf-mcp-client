import {Component, OnInit, Inject, ViewChild} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {CommonModule, DOCUMENT} from '@angular/common';
import {MatSidenavModule, MatSidenav} from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-memory-panel',
  imports: [
    CommonModule, MatSidenavModule, MatButtonModule, MatIconModule
  ],
  templateUrl: './memory-panel.component.html',
  styleUrl: './memory-panel.component.css'
})
export class MemoryPanelComponent implements OnInit {
  metrics: MemoryMetrics = {
    contextSize: 0,
    humanBlockValue: '',
    personaBlockValue: ''
  };
  disabled = false;

  @ViewChild('sidenav') sidenav!: MatSidenav;

  constructor(
    private httpClient: HttpClient,
    @Inject(DOCUMENT) private document: Document
  ) {}

  toggleSidenav() {
    if (!this.disabled) {
      this.sidenav.toggle();
    }
  }
  ngOnInit() {
    this.fetchMemoryMetrics();

    // this.metrics = {contextSize: 300, humanBlockValue: "Core Human Memory", personaBlockValue: "Core Persona Memory"}
  }

  fetchMemoryMetrics() {
    let host = '';
    let protocol = '';

    if (this.document.location.hostname == 'localhost') {
      host = 'localhost:8080';
    } else {
      host = this.document.location.host;
    }
    protocol = this.document.location.protocol;

    this.httpClient.get<MemoryMetrics>(`${protocol}//${host}/memory/metrics`)
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
