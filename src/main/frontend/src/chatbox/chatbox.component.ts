import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Inject,
  Injector,
  Input,
  NgZone,
  OnDestroy,
  runInInjectionContext,
  ViewChild,
  signal,
  computed,
  effect
} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {HttpParams} from '@angular/common/http';
import {MatIconButton, MatFabButton} from '@angular/material/button';
import {FormsModule} from '@angular/forms';
import {MatFormField} from '@angular/material/form-field';
import {MatInput, MatInputModule} from '@angular/material/input';
import {TextFieldModule} from '@angular/cdk/text-field';
import {MatCard, MatCardContent} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatDialog} from '@angular/material/dialog';
import {MarkdownComponent} from 'ngx-markdown';
import {PlatformMetrics} from '../app/app.component';
import {
  PromptSelectionDialogComponent,
  PromptSelectionResult
} from '../prompt-selection-dialog/prompt-selection-dialog.component';
import {PromptResolutionService} from '../services/prompt-resolution.service';
import {MatTooltip} from '@angular/material/tooltip';
import {MatExpansionModule} from '@angular/material/expansion';
import {ThinkTagParser} from './think-tag-parser';

interface ErrorInfo {
  message: string;
  errorType: string;
  timestamp: string;
  stackTrace?: string;
  context?: Record<string, string>;
}

interface ChatboxMessage {
  text: string;
  persona: 'user' | 'bot';
  typing?: boolean;
  reasoning?: string;
  showReasoning?: boolean;
  error?: ErrorInfo;
  showError?: boolean;
}

@Component({
  selector: 'app-chatbox',
  standalone: true,
  imports: [FormsModule, MatFormField, MatInput, MatCard, MatCardContent, MarkdownComponent, MatInputModule, MatIconModule, MatIconButton, MatFabButton, TextFieldModule, MatTooltip, MatExpansionModule],
  templateUrl: './chatbox.component.html',
  styleUrl: './chatbox.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChatboxComponent implements OnDestroy {
  @Input() documentIds: string[] = [];

  @Input() set metrics(value: PlatformMetrics) {
    this._metricsInput.set(value);
  }
  get metrics(): PlatformMetrics {
    return this._metricsInput();
  }

  private readonly _metricsInput = signal<PlatformMetrics>({
    conversationId: '',
    chatModel: '',
    embeddingModel: '',
    vectorStoreName: '',
    mcpServers: [],
    prompts: {
      totalPrompts: 0,
      serversWithPrompts: 0,
      available: false,
      promptsByServer: {}
    }
  });

  // State signals
  private readonly _messages = signal<ChatboxMessage[]>([]);
  private readonly _chatMessage = signal<string>('');
  private readonly _isStreaming = signal<boolean>(false);
  private readonly _isConnecting = signal<boolean>(false);

  // Public readonly signals
  readonly messages = this._messages.asReadonly();
  readonly chatMessage = this._chatMessage.asReadonly();

  // Computed signals for derived state
  readonly canSendMessage = computed(() =>
      this._chatMessage().trim().length > 0 &&
      !this._isStreaming() &&
      !this._isConnecting()
  );

  readonly isBusy = computed(() =>
    this._isStreaming() || this._isConnecting()
  );

  readonly lastBotMessage = computed(() => {
    const msgs = this._messages();
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].persona === 'bot') {
        return msgs[i];
      }
    }
    return null;
  });

  readonly hasAvailablePrompts = computed(() => {
    const metrics = this._metricsInput();
    return metrics &&
      metrics.prompts &&
      metrics.prompts.available &&
      metrics.prompts.totalPrompts > 0;
  });

  readonly sendButtonText = computed(() => {
    if (this._isConnecting()) return 'Connecting...';
    if (this._isStreaming()) return 'Streaming...';
    return 'Send';
  });

  readonly sendButtonTooltip = computed(() => {
    if (this._isStreaming() || this._isConnecting()) {
      return 'Please wait for current message to complete';
    }
    if (!this.canSendMessage()) {
      return 'Enter a message to send';
    }
    return 'Send message';
  });

  // Computed signals for optimized rendering
  readonly messagesWithReasoningFlags = computed(() => {
    return this._messages().map((message, index) => ({
      ...message,
      index,
      hasReasoning: message.persona === 'bot' && 
                   !!message.reasoning && 
                   message.reasoning.trim().length > 0,
      hasError: message.persona === 'bot' && !!message.error,
      reasoningToggleId: `reasoning-toggle-${index}`,
      reasoningContentId: `reasoning-content-${index}`,
      errorToggleId: `error-toggle-${index}`,
      errorContentId: `error-content-${index}`
    }));
  });

  readonly lastBotMessageIndex = computed(() => {
    const msgs = this._messages();
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].persona === 'bot') {
        return i;
      }
    }
    return -1;
  });

  readonly streamingMessageIndex = computed(() => {
    const lastBotIndex = this.lastBotMessageIndex();
    const msgs = this._messages();
    return lastBotIndex >= 0 && msgs[lastBotIndex]?.typing ? lastBotIndex : -1;
  });

  // Optimized computed signals for UI state
  readonly shouldShowScrollToBottom = computed(() => {
    const msgs = this._messages();
    return msgs.length > 3; // Only show scroll to bottom if more than 3 messages
  });

  readonly totalReasoningMessages = computed(() => {
    return this._messages().filter(msg => 
      msg.persona === 'bot' && msg.reasoning && msg.reasoning.trim().length > 0
    ).length;
  });

  readonly visibleReasoningCount = computed(() => {
    return this._messages().filter(msg => 
      msg.persona === 'bot' && msg.showReasoning === true
    ).length;
  });


  private host = '';
  private protocol = '';
  private thinkTagParser = new ThinkTagParser();
  private updateBatchTimeout?: number;
  private pendingUpdate: {
    mainContent: string;
    reasoningContent: string;
    typing: boolean;
  } | null = null;

  @ViewChild("chatboxMessages") private chatboxMessages?: ElementRef<HTMLDivElement>;

  constructor(
    private injector: Injector,
    @Inject(DOCUMENT) private document: Document,
    private ngZone: NgZone,
    private dialog: MatDialog,
    private promptResolutionService: PromptResolutionService
  ) {
    // Set up host and protocol
    if (this.document.location.hostname === 'localhost') {
      this.host = 'localhost:8080';
    } else {
      this.host = this.document.location.host;
    }
    this.protocol = this.document.location.protocol;

    // Effects for side effects
    this.setupEffects();
  }
  
  ngOnDestroy(): void {
    if (this.updateBatchTimeout) {
      clearTimeout(this.updateBatchTimeout);
    }
    
    // Flush any pending update before destroying
    if (this.pendingUpdate) {
      this.immediateUpdateMessage(
        this.pendingUpdate.mainContent,
        this.pendingUpdate.reasoningContent,
        this.pendingUpdate.typing
      );
    }
  }

  private setupEffects(): void {
    // Optimized auto-scroll - only trigger on message count changes, not content changes
    effect(() => {
      const messageCount = this._messages().length;
      const lastBotIndex = this.lastBotMessageIndex();
      
      if (messageCount > 0) {
        // Only scroll if we have a new message or the last bot message finished typing
        const lastBot = lastBotIndex >= 0 ? this._messages()[lastBotIndex] : null;
        if (!lastBot?.typing) {
          // Use requestAnimationFrame for better performance
          requestAnimationFrame(() => this.scrollChatToBottom());
        }
      }
    });

    // Optimized state logging - only log on actual state changes
    effect(() => {
      const streaming = this._isStreaming();
      const connecting = this._isConnecting();
      
      // Only log when state actually changes to true
      if (streaming || connecting) {
        console.log('Chat state changed:', { streaming, connecting });
      }
    });

    // Optimized metrics logging - only log meaningful changes
    effect(() => {
      const hasAvailablePrompts = this.hasAvailablePrompts();
      const chatModel = this._metricsInput().chatModel;
      
      console.log('Chat capabilities:', {
        hasModel: !!chatModel,
        hasPrompts: hasAvailablePrompts,
        modelName: chatModel
      });
    });

    // Model validation - only warn when user tries to send without model
    effect(() => {
      const canSend = this.canSendMessage();
      const hasModel = this._metricsInput().chatModel !== '';
      const hasMessage = this._chatMessage().trim().length > 0;
      
      if (hasMessage && !hasModel && !canSend) {
        console.warn('Cannot send: Chat model not available');
      }
    });
  }

  updateChatMessage(message: string): void {
    this._chatMessage.set(message);
  }

  onEnterKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendChatMessage();
    }
    // Allow Shift+Enter for new lines
  }

  toggleReasoning(messageIndex: number): void {
    this._messages.update(msgs => {
      if (messageIndex < 0 || messageIndex >= msgs.length) return msgs;
      
      const message = msgs[messageIndex];
      if (message.persona !== 'bot' || !message.reasoning || message.reasoning.trim() === '') {
        return msgs;
      }
      
      const updatedMessage = {
        ...message,
        showReasoning: !message.showReasoning
      };
      
      return [
        ...msgs.slice(0, messageIndex),
        updatedMessage,
        ...msgs.slice(messageIndex + 1)
      ];
    });
  }

  toggleError(messageIndex: number): void {
    this._messages.update(msgs => {
      if (messageIndex < 0 || messageIndex >= msgs.length) return msgs;
      
      const message = msgs[messageIndex];
      if (message.persona !== 'bot' || !message.error) {
        return msgs;
      }
      
      const updatedMessage = {
        ...message,
        showError: !message.showError
      };
      
      return [
        ...msgs.slice(0, messageIndex),
        updatedMessage,
        ...msgs.slice(messageIndex + 1)
      ];
    });
  }


  async sendChatMessage(): Promise<void> {
    if (!this.canSendMessage()) return;

    const messageText = this._chatMessage();

    this.addUserMessage(messageText);
    this.addBotMessagePlaceholder();
    this._chatMessage.set('');
    this._isConnecting.set(true);
    
    // Reset the parser for the new message
    this.thinkTagParser.reset();

    // Create HTTP params
    let params: HttpParams = new HttpParams().set('chat', messageText);
    if (this.documentIds.length > 0) {
      // Send multiple document IDs as comma-separated string
      params = params.set('documentIds', this.documentIds.join(','));
    }

    try {
      // Check if chat model is available
      const metrics = this._metricsInput();
      if (!metrics.chatModel) {
        this.handleChatError('No chat model is available');
        return;
      }

      // Stream the chat response
      await this.streamChatResponse(params);

    } catch (error) {
      console.error('Chat request error:', error);
      this.handleChatError('Sorry, I encountered an error processing your request.');
    }
  }

  openPromptSelection(): void {
    if (!this.hasAvailablePrompts()) {
      return;
    }

    const dialogRef = this.dialog.open(PromptSelectionDialogComponent, {
      data: { metrics: this._metricsInput() },
      width: '90vw',
      maxWidth: '800px',
      maxHeight: '80vh',
      panelClass: 'prompt-selection-dialog-container'
    });

    dialogRef.afterClosed().subscribe((result: PromptSelectionResult) => {
      if (result) {
        this.handlePromptSelection(result);
      }
    });
  }

  private addUserMessage(text: string): void {
    this._messages.update(msgs => [
      ...msgs,
      { text, persona: 'user' }
    ]);
  }

  private addBotMessagePlaceholder(): ChatboxMessage {
    const botMessage: ChatboxMessage = { 
      text: '', 
      persona: 'bot', 
      typing: true,
      reasoning: '',
      showReasoning: false
    };
    this._messages.update(msgs => [...msgs, botMessage]);
    return botMessage;
  }

  private updateBotMessage(content: string, typing: boolean = false): void {
    if (!content && !typing) return;
    
    // Fast path: if no think tags detected and none encountered before, skip parsing
    const hasThinkTags = content ? ThinkTagParser.containsThinkTags(content) : false;
    const hasEncounteredTags = this.thinkTagParser.hasEncounteredThinkTags();
    
    if (!hasThinkTags && !hasEncounteredTags && content) {
      // Pure text content, no parsing needed - use debounced updates for smoother rendering
      this.updateMessageContent(content, '', typing);
      return;
    }
    
    try {
      // Parse the chunk to separate main content from reasoning
      const parseResult = this.thinkTagParser.processChunk(content || '');
      
      // Only update if there's actually new content to avoid unnecessary re-renders
      if (parseResult.mainContent || parseResult.reasoningContent || typing !== undefined) {
        this.updateMessageContent(parseResult.mainContent, parseResult.reasoningContent, typing);
      }
    } catch (error) {
      console.warn('Error parsing think tags, falling back to plain text:', error);
      // Fallback: treat all content as main content
      this.updateMessageContent(content || '', '', typing);
    }
  }

  private updateMessageContent(mainContent: string, reasoningContent: string, typing: boolean): void {
    // For streaming content, use immediate updates to maintain responsiveness
    if (this._isStreaming() && (mainContent || reasoningContent)) {
      this.debouncedUpdateMessage(mainContent, reasoningContent, typing);
    } else {
      // For non-streaming updates (like typing state), update immediately
      this.immediateUpdateMessage(mainContent, reasoningContent, typing);
    }
  }

  private debouncedUpdateMessage(mainContent: string, reasoningContent: string, typing: boolean): void {
    // Accumulate the content
    if (this.pendingUpdate) {
      this.pendingUpdate.mainContent += mainContent;
      this.pendingUpdate.reasoningContent += reasoningContent;
      this.pendingUpdate.typing = typing;
    } else {
      this.pendingUpdate = { mainContent, reasoningContent, typing };
    }

    // Clear existing timeout
    if (this.updateBatchTimeout) {
      clearTimeout(this.updateBatchTimeout);
    }

    // Set new timeout for batched update
    this.updateBatchTimeout = window.setTimeout(() => {
      if (this.pendingUpdate) {
        this.immediateUpdateMessage(
          this.pendingUpdate.mainContent,
          this.pendingUpdate.reasoningContent,
          this.pendingUpdate.typing
        );
        this.pendingUpdate = null;
      }
      this.updateBatchTimeout = undefined;
    }, 16); // ~60fps update rate
  }

  private immediateUpdateMessage(mainContent: string, reasoningContent: string, typing: boolean): void {
    this._messages.update(msgs => {
      const lastIndex = msgs.length - 1;
      if (lastIndex >= 0 && msgs[lastIndex].persona === 'bot') {
        const currentMessage = msgs[lastIndex];
        
        // Check if we actually need to update to avoid unnecessary re-renders
        const needsUpdate = 
          mainContent || 
          reasoningContent || 
          currentMessage.typing !== typing;
          
        if (!needsUpdate) {
          return msgs;
        }
        
        const updatedMessage = {
          ...currentMessage,
          text: currentMessage.text + mainContent,
          reasoning: (currentMessage.reasoning || '') + reasoningContent,
          typing
        };
        
        return [
          ...msgs.slice(0, lastIndex),
          updatedMessage
        ];
      }
      return msgs;
    });
  }

  private setBotMessageTyping(typing: boolean): void {
    this._messages.update(msgs => {
      const lastIndex = msgs.length - 1;
      if (lastIndex >= 0 && msgs[lastIndex].persona === 'bot') {
        const updatedMessage = {
          ...msgs[lastIndex],
          typing
        };
        return [
          ...msgs.slice(0, lastIndex),
          updatedMessage
        ];
      }
      return msgs;
    });
  }

  private handleChatError(errorMessage: string): void {
    this.ngZone.run(() => {
      this.setBotMessageTyping(false);
      if (this.lastBotMessage()?.text === '') {
        this._messages.update(msgs => {
          const lastIndex = msgs.length - 1;
          if (lastIndex >= 0 && msgs[lastIndex].persona === 'bot') {
            return [
              ...msgs.slice(0, lastIndex),
              { ...msgs[lastIndex], text: errorMessage, typing: false }
            ];
          }
          return msgs;
        });
      }
      this._isStreaming.set(false);
      this._isConnecting.set(false);
    });
  }

  private handleServerError(errorDetails: ErrorInfo): void {
    this.ngZone.run(() => {
      this.setBotMessageTyping(false);
      this._messages.update(msgs => {
        const lastIndex = msgs.length - 1;
        if (lastIndex >= 0 && msgs[lastIndex].persona === 'bot') {
          return [
            ...msgs.slice(0, lastIndex),
            { 
              ...msgs[lastIndex], 
              text: errorDetails.message, 
              typing: false,
              error: errorDetails,
              showError: false
            }
          ];
        }
        return msgs;
      });
      this._isStreaming.set(false);
      this._isConnecting.set(false);
    });
  }

  private handlePromptSelection(result: PromptSelectionResult): void {
    const promptId = `${result.prompt.serverId}:${result.prompt.name}`;

    // If prompt has no arguments, use it directly
    if (!result.prompt.arguments || result.prompt.arguments.length === 0) {
      this.insertPromptIntoChat(result.prompt.name, result.prompt.description);
      return;
    }

    // Resolve prompt with arguments
    this.promptResolutionService.resolvePrompt({
      promptId: promptId,
      arguments: result.arguments
    }).subscribe({
      next: (resolvedPrompt) => {
        this.insertResolvedPromptIntoChat(resolvedPrompt);
      },
      error: (error) => {
        console.error('Error resolving prompt:', error);
        // Fallback: insert prompt name
        this.insertPromptIntoChat(result.prompt.name, 'Failed to resolve prompt with arguments');
      }
    });
  }

  private insertPromptIntoChat(promptName: string, description?: string): void {
    const content = description || promptName;
    this._chatMessage.set(content);
    this.sendChatMessage();
  }

  private insertResolvedPromptIntoChat(resolvedPrompt: any): void {
    let content: string;

    if (resolvedPrompt.messages && resolvedPrompt.messages.length > 0) {
      // Use structured messages
      content = resolvedPrompt.messages
        .map((msg: any) => msg.content)
        .join('\n\n');
    } else if (resolvedPrompt.content) {
      // Use direct content
      content = resolvedPrompt.content;
    } else {
      content = 'Resolved prompt content';
    }

    this._chatMessage.set(content);
    this.sendChatMessage();
  }

  private streamChatResponse(params: HttpParams): Promise<void> {
    return new Promise((resolve, reject) => {
      const url = `${this.protocol}//${this.host}/chat?${params.toString()}`;

      const eventSource = new EventSource(url, {
        withCredentials: true
      });

      let isFirstChunk = true;

      eventSource.onopen = () => {
        console.log('EventSource connection opened');
        this.ngZone.run(() => {
          this._isConnecting.set(false);
          this._isStreaming.set(true);
        });
      };

      eventSource.onmessage = (event) => {
        this.ngZone.run(() => {
          if (isFirstChunk) {
            this.setBotMessageTyping(false);
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
            this.updateBotMessage(chunk);
          }
        });
      };

      eventSource.onerror = (error) => {
        console.error('EventSource error:', error);
        eventSource.close();
        this.handleChatError('Sorry, I encountered an error processing your request.');
        reject(error);
      };

      // Listen for error events
      eventSource.addEventListener('error', (event: MessageEvent) => {
        this.ngZone.run(() => {
          try {
            const errorDetails: ErrorInfo = JSON.parse(event.data);
            this.handleServerError(errorDetails);
          } catch (e) {
            console.error('Failed to parse error details:', e);
            this.handleChatError('Sorry, I encountered an error processing your request.');
          }
        });
        eventSource.close();
        resolve();
      });

      // Listen for successful completion
      eventSource.addEventListener('close', () => {
        eventSource.close();
        this.ngZone.run(() => {
          this._isStreaming.set(false);
          this._isConnecting.set(false);
        });
        resolve();
      });
    });
  }

  private scrollChatToBottom(): void {
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
      });
    });
  }
}
