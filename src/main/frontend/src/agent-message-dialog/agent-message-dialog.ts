import { Component, Inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { A2AAgent } from '../app/app.component';

export interface AgentMessageDialogData {
  agent: A2AAgent;
}

export interface AgentMessageDialogResult {
  message: string;
}

@Component({
  selector: 'app-agent-message-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule
  ],
  templateUrl: './agent-message-dialog.html',
  styleUrl: './agent-message-dialog.css'
})
export class AgentMessageDialog {
  messageText = signal('');

  constructor(
    public dialogRef: MatDialogRef<AgentMessageDialog>,
    @Inject(MAT_DIALOG_DATA) public data: AgentMessageDialogData
  ) {}

  canSend(): boolean {
    return this.messageText().trim().length > 0;
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSend(): void {
    if (this.canSend()) {
      const result: AgentMessageDialogResult = {
        message: this.messageText().trim()
      };
      this.dialogRef.close(result);
    }
  }
}
