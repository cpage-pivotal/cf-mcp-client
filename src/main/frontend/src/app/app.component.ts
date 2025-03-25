import {
  afterNextRender,
  Component,
  ElementRef,
  Inject,
  Injector,
  NgZone,
  runInInjectionContext,
  ViewChild
} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {HttpClient, HttpParams} from '@angular/common/http';
import {lastValueFrom} from 'rxjs';
import {MatButton} from '@angular/material/button';
import {FormsModule} from '@angular/forms';
import {MatFormField} from '@angular/material/form-field';
import {MatInput, MatInputModule} from '@angular/material/input';
import {MatCard, MatCardContent} from '@angular/material/card';
import {MatToolbar} from '@angular/material/toolbar';
import {MarkdownComponent} from 'ngx-markdown';
import {MemoryPanelComponent} from '../memory-panel/memory-panel.component';

@Component({
  selector: 'app-root',
  imports: [MatButton, FormsModule, MatFormField, MatInput, MatCard, MatCardContent, MatToolbar, MarkdownComponent, MatInputModule, MemoryPanelComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  constructor(private httpClient: HttpClient,
              private injector: Injector,
              @Inject(DOCUMENT) private document: Document,
              private ngZone: NgZone) {
    if (this.document.location.hostname == 'localhost') {
      this.host = 'localhost:8080';
    } else this.host = this.document.location.host;
    this.protocol = this.document.location.protocol;
  }

  title = 'pulseui';
  host = '';
  protocol = '';
  messages: ChatboxMessage[] = [];
  chatMessage = '';

  async sendChatMessage() {
    this.messages.push({text: this.chatMessage, persona: 'user'});
    let botMessage: ChatboxMessage = {text: '', persona: 'bot', typing: true};
    this.messages.push(botMessage);
    this.scrollChatToBottom();
    let params: HttpParams = new HttpParams().set('chat', this.chatMessage);

    this.chatMessage = '';
    let response: ChatResponse = await lastValueFrom(this.httpClient.get<ChatResponse>(`${this.protocol}//${this.host}/chat`, {params}));

    // Use ngZone.run to ensure Angular detects the change and updates the view
    this.ngZone.run(() => {
      botMessage.typing = false;
      botMessage.text = response.message;

      // Use setTimeout to push this to the next macrotask queue
      // after Angular has had time to render the changes
      setTimeout(() => {
        this.scrollChatToBottom();
      }, 0);
    });
  }

  @ViewChild("chatboxMessages") private chatboxMessages?: ElementRef<HTMLDivElement>;

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
  persona: 'user' | 'bot';
  typing?: boolean;
}

interface ChatResponse {
  message: string
}
