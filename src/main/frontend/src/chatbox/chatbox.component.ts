import {
  afterNextRender,
  Component,
  ElementRef,
  Inject,
  Injector,
  Input,
  NgZone,
  runInInjectionContext,
  ViewChild
} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {HttpClient, HttpParams} from '@angular/common/http';
import {MatButton} from '@angular/material/button';
import {FormsModule} from '@angular/forms';
import {MatFormField} from '@angular/material/form-field';
import {MatInput, MatInputModule} from '@angular/material/input';
import {MatCard, MatCardContent} from '@angular/material/card';
import {MatSlideToggle} from '@angular/material/slide-toggle';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MarkdownComponent} from 'ngx-markdown';
import {PlatformMetrics} from '../app/app.component';
import {MatIcon} from '@angular/material/icon';

@Component({
  selector: 'app-chatbox',
  standalone: true,
  imports: [MatButton, FormsModule, MatFormField, MatInput, MatCard, MatCardContent, MatSlideToggle, MatTooltipModule, MarkdownComponent, MatInputModule, MatIcon],
  templateUrl: './chatbox.component.html',
  styleUrl: './chatbox.component.css'
})
export class ChatboxComponent {
  @Input() documentId: string = '';
  @Input() metrics!: PlatformMetrics;

  messages: ChatboxMessage[] = [];
  chatMessage = '';
  zantuMode = false;
  host = '';
  protocol = '';

  @ViewChild("chatboxMessages") private chatboxMessages?: ElementRef<HTMLDivElement>;

  constructor(private httpClient: HttpClient,
              private injector: Injector,
              @Inject(DOCUMENT) private document: Document,
              private ngZone: NgZone) {
    if (this.document.location.hostname == 'localhost') {
      this.host = 'localhost:8080';
    } else this.host = this.document.location.host;
    this.protocol = this.document.location.protocol;
  }

  /**
   * Check if Zantu is available (requires chat model)
   */
  isZantuAvailable(): boolean {
    return this.metrics && this.metrics.chatModel !== '';
  }

  async sendChatMessage() {
    if (!this.chatMessage.trim()) return;

    // Add user message
    this.messages.push({text: this.chatMessage, persona: 'user'});

    // Add bot/zantu message with typing indicator
    let botMessage: ChatboxMessage = {
      text: '',
      persona: this.zantuMode ? 'zantu' : 'bot',
      typing: true
    };
    this.messages.push(botMessage);
    this.scrollChatToBottom();

    const userMessage = this.chatMessage;
    this.chatMessage = '';

    try {
      if (this.metrics.chatModel == '') {
        this.ngZone.run(() => {
          botMessage.typing = false;
          botMessage.text = 'No chat model available';
          this.scrollChatToBottom();
        });
        return;
      }

      if (this.zantuMode) {
        await this.streamZantuResponse(userMessage, botMessage);
      } else {
        // Create HTTP params for regular chat
        let params: HttpParams = new HttpParams().set('chat', userMessage);
        if (this.documentId.length > 0) {
          params = params.set('documentId', this.documentId);
        }
        await this.streamChatResponse(params, botMessage);
      }

    } catch (error) {
      console.error('Chat request error:', error);
      this.ngZone.run(() => {
        if (botMessage.text === '') {
          botMessage.typing = false;
          botMessage.text = "Sorry, I encountered an error processing your request.";
        }
        this.scrollChatToBottom();
      });
    }
  }

  private streamChatResponse(params: HttpParams, botMessage: ChatboxMessage): Promise<void> {
    return new Promise((resolve, reject) => {
      const url = `${this.protocol}//${this.host}/chat?${params.toString()}`;

      const eventSource = new EventSource(url, {
        withCredentials: true
      });

      let isFirstChunk = true;

      eventSource.onmessage = (event) => {
        this.ngZone.run(() => {
          if (isFirstChunk) {
            botMessage.typing = false;
            isFirstChunk = false;
          }

          // Handle JSON chunks
          let chunk: string;
          try {
            const parsed = JSON.parse(event.data);
            chunk = parsed.content || event.data;
          } catch (e) {
            chunk = event.data;
          }

          if (chunk && chunk.length > 0) {
            botMessage.text += chunk;
          }

          // Scroll to bottom after each update
          setTimeout(() => {
            this.scrollChatToBottom();
          }, 0);
        });
      };

      eventSource.onerror = (error) => {
        console.error('EventSource error:', error);
        eventSource.close();
        this.ngZone.run(() => {
          if (botMessage.text === '') {
            botMessage.typing = false;
            botMessage.text = "Sorry, I encountered an error processing your request.";
          }
          this.scrollChatToBottom();
        });
        reject(error);
      };

      eventSource.onopen = () => {
        console.log('EventSource connection opened');
      };

      // Listen for successful completion
      eventSource.addEventListener('close', () => {
        eventSource.close();
        resolve();
      });
    });
  }

  private streamZantuResponse(userMessage: string, zantuMessage: ChatboxMessage): Promise<void> {
    return new Promise((resolve, reject) => {
      const params = new HttpParams().set('chat', userMessage);
      const url = `${this.protocol}//${this.host}/zantu/chat?${params.toString()}`;

      const eventSource = new EventSource(url, {
        withCredentials: true
      });

      let isFirstChunk = true;

      eventSource.onmessage = (event) => {
        this.ngZone.run(() => {
          if (isFirstChunk) {
            zantuMessage.typing = false;
            isFirstChunk = false;
          }

          // Handle JSON chunks
          let chunk: string;
          try {
            const parsed = JSON.parse(event.data);
            chunk = parsed.content || event.data;
          } catch (e) {
            chunk = event.data;
          }

          if (chunk && chunk.length > 0) {
            zantuMessage.text += chunk;
          }

          // Scroll to bottom after each update
          setTimeout(() => {
            this.scrollChatToBottom();
          }, 0);
        });
      };

      // Handle acknowledgment messages (when message is sent to Zantu)
      eventSource.addEventListener('acknowledgment', (event) => {
        this.ngZone.run(() => {
          if (isFirstChunk) {
            zantuMessage.typing = false;
            zantuMessage.text = 'Thinking...';
            isFirstChunk = false;
            this.scrollChatToBottom();
          }
        });
      });

      // Handle actual Zantu responses
      eventSource.addEventListener('zantu_message', (event) => {
        this.ngZone.run(() => {
          try {
            const parsed = JSON.parse(event.data);
            const content = parsed.content || event.data;

            // Replace the "Thinking..." message with actual content
            zantuMessage.text = content;
            this.scrollChatToBottom();
          } catch (e) {
            zantuMessage.text = event.data;
            this.scrollChatToBottom();
          }
        });
      });

      eventSource.onerror = (error) => {
        console.error('Zantu EventSource error:', error);
        eventSource.close();
        this.ngZone.run(() => {
          if (zantuMessage.text === '' || zantuMessage.text === 'Thinking...') {
            zantuMessage.typing = false;
            zantuMessage.text = "Sorry, I encountered an error communicating with Zantu.";
          }
          this.scrollChatToBottom();
        });
        reject(error);
      };

      eventSource.onopen = () => {
        console.log('Zantu EventSource connection opened');
      };

      // Listen for successful completion
      eventSource.addEventListener('close', () => {
        eventSource.close();
        resolve();
      });
    });
  }

  scrollChatToBottom() {
    runInInjectionContext(this.injector, () => {
      afterNextRender({
        read: () => {
          if (this.chatboxMessages) {
            this.chatboxMessages.nativeElement.lastElementChild?.scrollIntoView({
              behavior: "smooth",
              block: "start"
            });
          }
        }
      })
    })
  }
}

interface ChatboxMessage {
  text: string;
  persona: 'user' | 'bot' | 'zantu';
  typing?: boolean;
}
