import { Injectable } from '@angular/core';

export interface ChatboxMessage {
  id: string;
  text: string;
  persona: 'user' | 'bot' | 'agent';
  agentType?: string;
  timestamp: Date;
  // New fields for message behavior discrimination
  messageType: 'single-response' | 'multi-response';
  parentMessageId?: string; // For linking agent responses to user message
  responseIndex?: number; // For ordering multiple agent responses
  isComplete?: boolean; // Indicates if message sequence is complete
  typing?: boolean;
  reasoning?: string;
  showReasoning?: boolean;
  error?: any;
  showError?: boolean;
  agentInfo?: any;
}

@Injectable({ providedIn: 'root' })
export class MessageFactoryService {

  constructor() {}

  createUserMessage(text: string): ChatboxMessage {
    return {
      id: this.generateId(),
      text,
      persona: 'user',
      timestamp: new Date(),
      messageType: 'single-response',
      typing: false,
      isComplete: true
    };
  }

  createBotMessagePlaceholder(userMessageId: string): ChatboxMessage {
    return {
      id: this.generateId(),
      text: '',
      persona: 'bot',
      messageType: 'single-response',
      parentMessageId: userMessageId,
      timestamp: new Date(),
      typing: true,
      isComplete: false,
      reasoning: '',
      showReasoning: false
    };
  }

  createAgentMessage(text: string, userMessageId: string, index: number): ChatboxMessage {
    return {
      id: this.generateId(),
      text,
      persona: 'agent',
      messageType: 'multi-response',
      parentMessageId: userMessageId,
      responseIndex: index,
      timestamp: new Date(),
      typing: false,
      isComplete: false
    };
  }

  updateBotMessage(message: ChatboxMessage, content: string, typing: boolean = false): ChatboxMessage {
    return {
      ...message,
      text: message.text + content,
      typing,
      isComplete: !typing
    };
  }

  updateBotMessageWithReasoning(message: ChatboxMessage, mainContent: string, reasoningContent: string, typing: boolean = false): ChatboxMessage {
    return {
      ...message,
      text: message.text + mainContent,
      reasoning: (message.reasoning || '') + reasoningContent,
      typing,
      isComplete: !typing
    };
  }

  updateAgentMessage(message: ChatboxMessage, content: string, typing: boolean = false, agentInfo?: any): ChatboxMessage {
    return {
      ...message,
      text: typing ? message.text + content : content,
      typing,
      isComplete: !typing,
      agentInfo
    };
  }

  markMessageComplete(message: ChatboxMessage): ChatboxMessage {
    return {
      ...message,
      typing: false,
      isComplete: true
    };
  }

  addErrorToMessage(message: ChatboxMessage, error: any): ChatboxMessage {
    return {
      ...message,
      text: error.message || 'An error occurred',
      typing: false,
      isComplete: true,
      error,
      showError: false
    };
  }

  generateId(): string {
    return `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}