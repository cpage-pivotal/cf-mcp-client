import { Injectable, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

/**
 * AuthService handles authentication-related operations.
 * Centralizes logout functionality to avoid code duplication across components.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly document = inject(DOCUMENT);

  /**
   * Logs out the current user by submitting a POST request to /logout.
   * Uses form submission to ensure proper CSRF handling and session cleanup.
   */
  logout(): void {
    const form = this.document.createElement('form');
    form.method = 'POST';
    form.action = '/logout';
    this.document.body.appendChild(form);
    form.submit();
  }
}
