import { Injectable, Inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

/**
 * Base service for API communications that handles host and protocol configuration.
 * Centralizes the logic for determining the correct backend URL based on environment.
 */
@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private host: string;
  private protocol: string;

  constructor(@Inject(DOCUMENT) private document: Document) {
    // Set up host and protocol - centralized logic used across all components
    if (this.document.location.hostname === 'localhost') {
      this.host = 'localhost:8080';
    } else {
      this.host = this.document.location.host;
    }
    this.protocol = this.document.location.protocol;
  }

  /**
   * Gets the base URL for API calls.
   * @returns The base URL (e.g., "http://localhost:8080" or "https://example.com")
   */
  getBaseUrl(): string {
    return `${this.protocol}//${this.host}`;
  }

  /**
   * Constructs a full URL for an API endpoint.
   * @param path The API path (e.g., "/api/agents/status")
   * @returns The complete URL
   */
  getApiUrl(path: string): string {
    // Ensure path starts with /
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    return `${this.getBaseUrl()}${normalizedPath}`;
  }

  /**
   * Gets the host portion (for backwards compatibility).
   * @returns The host string
   */
  getHost(): string {
    return this.host;
  }

  /**
   * Gets the protocol portion (for backwards compatibility).
   * @returns The protocol string
   */
  getProtocol(): string {
    return this.protocol;
  }
}