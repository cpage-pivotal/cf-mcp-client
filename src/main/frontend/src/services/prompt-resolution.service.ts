import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface PromptResolutionRequest {
  promptId: string;
  arguments: { [key: string]: any };
}

export interface PromptMessage {
  role: string;
  content: string;
}

export interface ResolvedPrompt {
  content: string;
  messages?: PromptMessage[];
  metadata?: { [key: string]: any };
}

@Injectable({
  providedIn: 'root'
})
export class PromptResolutionService {

  constructor(
    private httpClient: HttpClient,
    private apiService: ApiService
  ) {}

  /**
   * Resolve a prompt with the provided arguments
   */
  resolvePrompt(request: PromptResolutionRequest): Observable<ResolvedPrompt> {
    const url = this.apiService.getApiUrl('/prompts/resolve');

    return this.httpClient.post<ResolvedPrompt>(url, request);
  }

}
