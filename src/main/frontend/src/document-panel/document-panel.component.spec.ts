import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * These tests verify the pure business logic used in DocumentPanelComponent.
 * The formatting and validation logic is extracted and tested independently.
 */

// Helper functions that mirror the component logic
function isValidFileType(file: { type: string; name: string }): boolean {
  return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
}

function getFileTypeIcon(fileName: string): string {
  if (fileName.toLowerCase().endsWith('.pdf')) {
    return 'picture_as_pdf';
  }
  return 'insert_drive_file';
}

function formatDocumentDate(uploadDate: string, now: Date = new Date()): string {
  const date = new Date(uploadDate);
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

describe('DocumentPanel Logic', () => {
  describe('isValidFileType', () => {
    it('should accept PDF by MIME type', () => {
      const file = { type: 'application/pdf', name: 'document.doc' };
      expect(isValidFileType(file)).toBe(true);
    });

    it('should accept PDF by file extension', () => {
      const file = { type: 'application/octet-stream', name: 'document.pdf' };
      expect(isValidFileType(file)).toBe(true);
    });

    it('should accept PDF with uppercase extension', () => {
      const file = { type: '', name: 'DOCUMENT.PDF' };
      expect(isValidFileType(file)).toBe(true);
    });

    it('should accept PDF with mixed case extension', () => {
      const file = { type: '', name: 'Document.Pdf' };
      expect(isValidFileType(file)).toBe(true);
    });

    it('should reject non-PDF files', () => {
      const file = { type: 'text/plain', name: 'document.txt' };
      expect(isValidFileType(file)).toBe(false);
    });

    it('should reject Word documents', () => {
      const file = { type: 'application/msword', name: 'document.doc' };
      expect(isValidFileType(file)).toBe(false);
    });

    it('should reject images', () => {
      const file = { type: 'image/png', name: 'image.png' };
      expect(isValidFileType(file)).toBe(false);
    });

    it('should reject files with pdf in name but wrong extension', () => {
      const file = { type: 'text/plain', name: 'pdf_document.txt' };
      expect(isValidFileType(file)).toBe(false);
    });
  });

  describe('getFileTypeIcon', () => {
    it('should return PDF icon for .pdf files', () => {
      expect(getFileTypeIcon('document.pdf')).toBe('picture_as_pdf');
    });

    it('should return PDF icon for uppercase .PDF files', () => {
      expect(getFileTypeIcon('DOCUMENT.PDF')).toBe('picture_as_pdf');
    });

    it('should return generic icon for non-PDF files', () => {
      expect(getFileTypeIcon('document.txt')).toBe('insert_drive_file');
    });

    it('should return generic icon for files without extension', () => {
      expect(getFileTypeIcon('document')).toBe('insert_drive_file');
    });

    it('should return generic icon for Word documents', () => {
      expect(getFileTypeIcon('document.docx')).toBe('insert_drive_file');
    });
  });

  describe('formatDocumentDate', () => {
    // Use a fixed reference date for consistent testing
    const referenceDate = new Date('2025-12-30T12:00:00.000Z');

    it('should return "Today" for same day uploads', () => {
      const uploadDate = '2025-12-30T08:00:00.000Z';
      expect(formatDocumentDate(uploadDate, referenceDate)).toBe('Today');
    });

    it('should return "Yesterday" for previous day uploads', () => {
      const uploadDate = '2025-12-29T08:00:00.000Z';
      expect(formatDocumentDate(uploadDate, referenceDate)).toBe('Yesterday');
    });

    it('should return "2 days ago" for 2 days old uploads', () => {
      const uploadDate = '2025-12-28T08:00:00.000Z';
      expect(formatDocumentDate(uploadDate, referenceDate)).toBe('2 days ago');
    });

    it('should return "3 days ago" for 3 days old uploads', () => {
      const uploadDate = '2025-12-27T08:00:00.000Z';
      expect(formatDocumentDate(uploadDate, referenceDate)).toBe('3 days ago');
    });

    it('should return "6 days ago" for 6 days old uploads', () => {
      const uploadDate = '2025-12-24T08:00:00.000Z';
      expect(formatDocumentDate(uploadDate, referenceDate)).toBe('6 days ago');
    });

    it('should return formatted date for uploads older than 7 days', () => {
      const uploadDate = '2025-12-20T08:00:00.000Z';
      const result = formatDocumentDate(uploadDate, referenceDate);
      // Should be a locale date string, not "X days ago"
      expect(result).not.toContain('days ago');
      expect(result).not.toBe('Today');
      expect(result).not.toBe('Yesterday');
    });

    it('should return formatted date for very old uploads', () => {
      const uploadDate = '2024-01-15T08:00:00.000Z';
      const result = formatDocumentDate(uploadDate, referenceDate);
      expect(result).not.toContain('days ago');
    });
  });
});
