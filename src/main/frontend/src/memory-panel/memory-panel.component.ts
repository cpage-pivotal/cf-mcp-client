import {Component, Input, ViewChild, AfterViewInit, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatSidenav, MatSidenavModule} from '@angular/material/sidenav';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {FormsModule} from '@angular/forms';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatTooltipModule} from "@angular/material/tooltip";
import {PlatformMetrics} from '../app/app.component';
import {SidenavService} from '../services/sidenav.service';
import {trigger, state, style, transition, animate} from '@angular/animations';

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
    MatTooltipModule
  ],
  templateUrl: './memory-panel.component.html',
  styleUrl: './memory-panel.component.css',
  animations: [
    trigger('fadeInOut', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(-10px)' }),
        animate('300ms ease-in', style({ opacity: 1, transform: 'translateY(0)' }))
      ]),
      transition(':leave', [
        animate('300ms ease-out', style({ opacity: 0, transform: 'translateY(-10px)' }))
      ])
    ])
  ]
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

  // Motivational message properties
  showMessage: boolean = false;
  currentMessage: string = '';
  private motivationalMessages: string[] = [
    "You're doing great! Keep pushing forward! 💪",
    "Success is built one step at a time. You've got this! 🚀",
    "Every challenge is an opportunity to grow stronger! 🌟",
    "Believe in yourself - you're more capable than you know! ✨",
    "Today is a perfect day to make progress on your goals! 🎯"
  ];

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

  showMotivationalMessage() {
    const randomIndex = Math.floor(Math.random() * this.motivationalMessages.length);
    this.currentMessage = this.motivationalMessages[randomIndex];
    this.showMessage = true;

    setTimeout(() => {
      this.showMessage = false;
    }, 3000);
  }
}
