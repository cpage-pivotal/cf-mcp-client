import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatCardModule } from '@angular/material/card';
import { McpServer } from '../app/app.component';

@Component({
  selector: 'app-tools-modal',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatCardModule
  ],
  templateUrl: './tools-modal.component.html',
  styleUrl: './tools-modal.component.css'
})
export class ToolsModalComponent {
  constructor(
    public dialogRef: MatDialogRef<ToolsModalComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { mcpServer: McpServer }
  ) {}

  onClose(): void {
    this.dialogRef.close();
  }
}
