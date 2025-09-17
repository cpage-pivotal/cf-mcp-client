import { Component, EventEmitter, Inject, Input, Output, ViewChild, AfterViewInit, signal } from '@angular/core';
import { HttpClient, HttpEventType } from '@angular/common/http';
import { CommonModule, DOCUMENT } from '@angular/common';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { FileSizePipe } from '../pipes/file-size.pipe';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { PlatformMetrics } from '../app/app.component';
import { SidenavService } from '../services/sidenav.service';
import { HammerGestureConfig, HAMMER_GESTURE_CONFIG } from '@angular/platform-browser';
import { Injectable } from '@angular/core';

@Injectable()
export class DocumentHammerConfig extends HammerGestureConfig {
  override overrides = {
    swipe: { direction: 6 }, // Horizontal swipe only
    pan: { direction: 6 }
  };
}

@Component({
  selector: 'app-document-panel',
  standalone: true,
  imports: [
    CommonModule, MatSidenavModule, MatButtonModule, MatIconModule,
    MatListModule, MatSnackBarModule, FileSizePipe, MatProgressBarModule, MatTooltipModule, MatChipsModule
  ],
  providers: [
    { provide: HAMMER_GESTURE_CONFIG, useClass: DocumentHammerConfig }
  ],
  templateUrl: './document-panel.component.html',
  styleUrl: './document-panel.component.css'
})
export class DocumentPanelComponent implements AfterViewInit {
  documents: DocumentInfo[] = [];

  @Input() metrics!: PlatformMetrics;

  // Add Output event emitter for document IDs changes
  @Output() documentIdsChanged = new EventEmitter<string[]>();

  // Upload progress properties
  uploadProgress = 0;
  isUploading = false;
  currentFileName = '';

  // Drag and drop signals (modern Angular pattern)
  isDragOver = signal(false);
  dragCounter = signal(0);

  // Document selection and interaction state
  selectedDocumentId = signal<string | null>(null);
  swipingDocumentId = signal<string | null>(null);
  swipeDistance = signal(0);

  @ViewChild('sidenav') sidenav!: MatSidenav;

  constructor(
    private httpClient: HttpClient,
    @Inject(DOCUMENT) private htmlDocument: Document,
    private snackBar: MatSnackBar,
    private sidenavService: SidenavService
  ) {
    this.fetchDocuments();
  }

  ngAfterViewInit(): void {
    this.sidenavService.registerSidenav('document', this.sidenav);
  }

  toggleSidenav() {
    this.sidenavService.toggle('document');
  }

  onSidenavOpenedChange(opened: boolean) {
    if (!opened) {
      // Sidenav was closed (e.g., by backdrop click) - update service state
      this.sidenavService.notifyPanelClosed('document');
    }
  }

  // Drag and drop event handlers
  onDragEnter(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragCounter.set(this.dragCounter() + 1);
    if (this.dragCounter() === 1) {
      this.isDragOver.set(true);
    }
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragCounter.set(this.dragCounter() - 1);
    if (this.dragCounter() === 0) {
      this.isDragOver.set(false);
    }
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();

    this.isDragOver.set(false);
    this.dragCounter.set(0);

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      const file = files[0];

      // Validate file type
      if (this.isValidFileType(file)) {
        this.uploadFile(file);
      } else {
        this.snackBar.open('Please select a PDF file', 'Close', {
          duration: 3000
        });
      }
    }
  }

  private isValidFileType(file: File): boolean {
    return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  }

  getFileTypeIcon(fileName: string): string {
    if (fileName.toLowerCase().endsWith('.pdf')) {
      return 'picture_as_pdf';
    }
    return 'insert_drive_file';
  }

  onFileSelected(event: Event) {
    const fileInput = event.target as HTMLInputElement;
    if (fileInput.files && fileInput.files.length > 0) {
      const file = fileInput.files[0];
      this.uploadFile(file);
      fileInput.value = ''; // Reset the input
    }
  }

  uploadFile(file: File) {
    const formData = new FormData();
    formData.append('file', file);

    // Reset and initialize progress tracking
    this.uploadProgress = 0;
    this.isUploading = true;
    this.currentFileName = file.name;

    let host: string;
    let protocol: string;

    if (this.htmlDocument.location.hostname == 'localhost') {
      host = 'localhost:8080';
    } else {
      host = this.htmlDocument.location.host;
    }
    protocol = this.htmlDocument.location.protocol;

    this.httpClient.post<UploadResponse>(`${protocol}//${host}/upload`, formData, {
      reportProgress: true,
      observe: 'events'
    }).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress) {
          // Calculate and update progress percentage
          if (event.total) {
            this.uploadProgress = Math.round(100 * event.loaded / event.total);
          }
        } else if (event.type === HttpEventType.Response) {
          // Upload complete - use the response which includes all documents
          this.isUploading = false;
          this.snackBar.open('File uploaded successfully', 'Close', {
            duration: 3000
          });
          if (event.body?.allDocuments) {
            this.documents = event.body.allDocuments;
            const documentIds = this.documents.map(doc => doc.id);
            this.documentIdsChanged.emit(documentIds);
          } else {
            this.fetchDocuments(); // Fallback to refetch
          }
        }
      },
      error: (error) => {
        // Reset progress state on error
        this.isUploading = false;
        console.error('Error uploading file:', error);
        this.snackBar.open('Error uploading file', 'Close', {
          duration: 3000
        });
      }
    });
  }

  fetchDocuments() {
    let host: string;
    let protocol: string;

    if (this.htmlDocument.location.hostname == 'localhost') {
      host = 'localhost:8080';
    } else {
      host = this.htmlDocument.location.host;
    }
    protocol = this.htmlDocument.location.protocol;

    this.httpClient.get<DocumentInfo[]>(`${protocol}//${host}/documents`)
      .subscribe({
        next: (data) => {
          this.documents = data;
          // Emit all document IDs whenever the document list changes
          const documentIds = this.documents.map(doc => doc.id);
          this.documentIdsChanged.emit(documentIds);
        },
        error: (error) => {
          console.error('Error fetching documents:', error);
        }
      });
  }

  deleteAllDocuments() {
    let host: string;
    let protocol: string;

    if (this.htmlDocument.location.hostname == 'localhost') {
      host = 'localhost:8080';
    } else {
      host = this.htmlDocument.location.host;
    }
    protocol = this.htmlDocument.location.protocol;

    this.httpClient.delete(`${protocol}//${host}/documents`)
      .subscribe({
        next: () => {
          this.snackBar.open('All documents deleted', 'Close', {
            duration: 3000
          });
          this.documents = [];
          this.documentIdsChanged.emit([]);
        },
        error: (error) => {
          console.error('Error deleting all documents:', error);
          this.snackBar.open('Error deleting all documents', 'Close', {
            duration: 3000
          });
        }
      });
  }

  deleteDocument(documentId: string) {
    let host: string;
    let protocol: string;

    if (this.htmlDocument.location.hostname == 'localhost') {
      host = 'localhost:8080';
    } else {
      host = this.htmlDocument.location.host;
    }
    protocol = this.htmlDocument.location.protocol;

    this.httpClient.delete<DeleteResponse>(`${protocol}//${host}/documents/${documentId}`)
      .subscribe({
        next: (response) => {
          this.snackBar.open('Document deleted', 'Close', {
            duration: 3000
          });
          if (response?.remainingDocuments) {
            this.documents = response.remainingDocuments;
            const documentIds = this.documents.map(doc => doc.id);
            this.documentIdsChanged.emit(documentIds);
          } else {
            this.fetchDocuments(); // Fallback to refetch
          }
          // Clear selection if deleted document was selected
          if (this.selectedDocumentId() === documentId) {
            this.selectedDocumentId.set(null);
          }
        },
        error: (error) => {
          console.error('Error deleting document:', error);
          this.snackBar.open('Error deleting document', 'Close', {
            duration: 3000
          });
        }
      });
  }

  // Document selection methods
  selectDocument(documentId: string) {
    if (this.selectedDocumentId() === documentId) {
      this.selectedDocumentId.set(null); // Deselect if already selected
    } else {
      this.selectedDocumentId.set(documentId);
    }
  }

  isDocumentSelected(documentId: string): boolean {
    return this.selectedDocumentId() === documentId;
  }

  // Mobile swipe gesture handlers
  onSwipeStart(documentId: string, event: any) {
    this.swipingDocumentId.set(documentId);
    this.swipeDistance.set(0);
  }

  onSwipeMove(event: any) {
    if (this.swipingDocumentId()) {
      const deltaX = event.deltaX;
      // Limit swipe distance to prevent over-swiping
      const maxSwipe = -80;
      this.swipeDistance.set(Math.max(deltaX, maxSwipe));
    }
  }

  onSwipeEnd(event: any) {
    const swipeThreshold = -60; // Pixels to trigger delete
    const currentDistance = this.swipeDistance();

    if (currentDistance <= swipeThreshold && this.swipingDocumentId()) {
      // Trigger delete action
      this.deleteDocument(this.swipingDocumentId()!);
    }

    // Reset swipe state
    this.swipingDocumentId.set(null);
    this.swipeDistance.set(0);
  }

  getDocumentItemTransform(documentId: string): string {
    if (this.swipingDocumentId() === documentId) {
      return `translateX(${this.swipeDistance()}px)`;
    }
    return 'translateX(0px)';
  }

  formatDocumentDate(uploadDate: string): string {
    const date = new Date(uploadDate);
    const now = new Date();
    const diffTime = Math.abs(now.getTime() - date.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays === 1) {
      return 'Today';
    } else if (diffDays === 2) {
      return 'Yesterday';
    } else if (diffDays <= 7) {
      return `${diffDays - 1} days ago`;
    } else {
      return date.toLocaleDateString();
    }
  }
}

interface DocumentInfo {
  id: string;
  name: string;
  size: number;
  uploadDate: string;
}

interface UploadResponse {
  uploadedDocument: DocumentInfo;
  allDocuments: DocumentInfo[];
}

interface DeleteResponse {
  message: string;
  remainingDocuments: DocumentInfo[];
}
