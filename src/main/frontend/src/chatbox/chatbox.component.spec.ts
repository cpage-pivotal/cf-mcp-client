import { describe, it, expect } from 'vitest';

/**
 * These tests verify the pure business logic used in ChatboxComponent.
 * The computed signal logic and message handling is extracted and tested independently.
 */

interface ChatboxMessage {
  text: string;
  persona: 'user' | 'bot' | 'agent';
  typing?: boolean;
  reasoning?: string;
  showReasoning?: boolean;
  error?: ErrorInfo;
  showError?: boolean;
  agentName?: string;
  statusMessage?: string;
  taskState?: string;
}

interface ErrorInfo {
  message: string;
  errorType: string;
  timestamp: string;
  stackTrace?: string;
  context?: Record<string, string>;
}

// Helper functions that mirror the component's computed signal logic
function canSendMessage(
  chatMessage: string,
  isStreaming: boolean,
  isConnecting: boolean
): boolean {
  return chatMessage.trim().length > 0 && !isStreaming && !isConnecting;
}

function isBusy(isStreaming: boolean, isConnecting: boolean): boolean {
  return isStreaming || isConnecting;
}

function lastBotMessage(messages: ChatboxMessage[]): ChatboxMessage | null {
  for (let i = messages.length - 1; i >= 0; i--) {
    if (messages[i].persona === 'bot') {
      return messages[i];
    }
  }
  return null;
}

function lastBotMessageIndex(messages: ChatboxMessage[]): number {
  for (let i = messages.length - 1; i >= 0; i--) {
    if (messages[i].persona === 'bot') {
      return i;
    }
  }
  return -1;
}

function getSendButtonText(
  isConnecting: boolean,
  isStreaming: boolean
): string {
  if (isConnecting) return 'Connecting...';
  if (isStreaming) return 'Streaming...';
  return 'Send';
}

function getSendButtonTooltip(
  isStreaming: boolean,
  isConnecting: boolean,
  canSend: boolean
): string {
  if (isStreaming || isConnecting) {
    return 'Please wait for current message to complete';
  }
  if (!canSend) {
    return 'Enter a message to send';
  }
  return 'Send message';
}

function messagesWithReasoningFlags(messages: ChatboxMessage[]) {
  return messages.map((message, index) => ({
    ...message,
    index,
    hasReasoning:
      (message.persona === 'bot' || message.persona === 'agent') &&
      !!message.reasoning &&
      message.reasoning.trim().length > 0,
    hasError:
      (message.persona === 'bot' || message.persona === 'agent') &&
      !!message.error,
    reasoningToggleId: `reasoning-toggle-${index}`,
    reasoningContentId: `reasoning-content-${index}`,
    errorToggleId: `error-toggle-${index}`,
    errorContentId: `error-content-${index}`,
  }));
}

function totalReasoningMessages(messages: ChatboxMessage[]): number {
  return messages.filter(
    (msg) =>
      msg.persona === 'bot' && msg.reasoning && msg.reasoning.trim().length > 0
  ).length;
}

function streamingMessageIndex(messages: ChatboxMessage[]): number {
  const lastBotIdx = lastBotMessageIndex(messages);
  return lastBotIdx >= 0 && messages[lastBotIdx]?.typing ? lastBotIdx : -1;
}

describe('Chatbox Logic', () => {
  describe('canSendMessage', () => {
    it('should return true when message has content and not busy', () => {
      expect(canSendMessage('Hello', false, false)).toBe(true);
    });

    it('should return false when message is empty', () => {
      expect(canSendMessage('', false, false)).toBe(false);
    });

    it('should return false when message is only whitespace', () => {
      expect(canSendMessage('   ', false, false)).toBe(false);
    });

    it('should return false when streaming', () => {
      expect(canSendMessage('Hello', true, false)).toBe(false);
    });

    it('should return false when connecting', () => {
      expect(canSendMessage('Hello', false, true)).toBe(false);
    });

    it('should return false when both streaming and connecting', () => {
      expect(canSendMessage('Hello', true, true)).toBe(false);
    });

    it('should return true for message with leading/trailing whitespace', () => {
      expect(canSendMessage('  Hello  ', false, false)).toBe(true);
    });
  });

  describe('isBusy', () => {
    it('should return false when idle', () => {
      expect(isBusy(false, false)).toBe(false);
    });

    it('should return true when streaming', () => {
      expect(isBusy(true, false)).toBe(true);
    });

    it('should return true when connecting', () => {
      expect(isBusy(false, true)).toBe(true);
    });

    it('should return true when both streaming and connecting', () => {
      expect(isBusy(true, true)).toBe(true);
    });
  });

  describe('lastBotMessage', () => {
    it('should return null for empty messages', () => {
      expect(lastBotMessage([])).toBeNull();
    });

    it('should return null when no bot messages exist', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Hello', persona: 'user' },
        { text: 'Hi there', persona: 'user' },
      ];
      expect(lastBotMessage(messages)).toBeNull();
    });

    it('should return the last bot message', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Hello', persona: 'user' },
        { text: 'First response', persona: 'bot' },
        { text: 'Another question', persona: 'user' },
        { text: 'Second response', persona: 'bot' },
      ];
      expect(lastBotMessage(messages)?.text).toBe('Second response');
    });

    it('should return bot message even if agent message comes after', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Bot response', persona: 'bot' },
        { text: 'Agent response', persona: 'agent', agentName: 'Agent1' },
      ];
      expect(lastBotMessage(messages)?.text).toBe('Bot response');
    });
  });

  describe('lastBotMessageIndex', () => {
    it('should return -1 for empty messages', () => {
      expect(lastBotMessageIndex([])).toBe(-1);
    });

    it('should return -1 when no bot messages exist', () => {
      const messages: ChatboxMessage[] = [{ text: 'Hello', persona: 'user' }];
      expect(lastBotMessageIndex(messages)).toBe(-1);
    });

    it('should return correct index of last bot message', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Hello', persona: 'user' },
        { text: 'Response', persona: 'bot' },
        { text: 'Follow up', persona: 'user' },
      ];
      expect(lastBotMessageIndex(messages)).toBe(1);
    });
  });

  describe('getSendButtonText', () => {
    it('should return "Send" when idle', () => {
      expect(getSendButtonText(false, false)).toBe('Send');
    });

    it('should return "Connecting..." when connecting', () => {
      expect(getSendButtonText(true, false)).toBe('Connecting...');
    });

    it('should return "Streaming..." when streaming', () => {
      expect(getSendButtonText(false, true)).toBe('Streaming...');
    });

    it('should prioritize "Connecting..." over "Streaming..."', () => {
      expect(getSendButtonText(true, true)).toBe('Connecting...');
    });
  });

  describe('getSendButtonTooltip', () => {
    it('should show wait message when streaming', () => {
      expect(getSendButtonTooltip(true, false, false)).toBe(
        'Please wait for current message to complete'
      );
    });

    it('should show wait message when connecting', () => {
      expect(getSendButtonTooltip(false, true, false)).toBe(
        'Please wait for current message to complete'
      );
    });

    it('should prompt to enter message when cannot send', () => {
      expect(getSendButtonTooltip(false, false, false)).toBe(
        'Enter a message to send'
      );
    });

    it('should show send message when ready', () => {
      expect(getSendButtonTooltip(false, false, true)).toBe('Send message');
    });
  });

  describe('messagesWithReasoningFlags', () => {
    it('should add hasReasoning flag for bot messages with reasoning', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Response', persona: 'bot', reasoning: 'My reasoning' },
      ];

      const result = messagesWithReasoningFlags(messages);

      expect(result[0].hasReasoning).toBe(true);
    });

    it('should not set hasReasoning for empty reasoning', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Response', persona: 'bot', reasoning: '' },
      ];

      const result = messagesWithReasoningFlags(messages);

      expect(result[0].hasReasoning).toBe(false);
    });

    it('should not set hasReasoning for whitespace-only reasoning', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Response', persona: 'bot', reasoning: '   ' },
      ];

      const result = messagesWithReasoningFlags(messages);

      expect(result[0].hasReasoning).toBe(false);
    });

    it('should not set hasReasoning for user messages', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Hello', persona: 'user', reasoning: 'Some reasoning' } as any,
      ];

      const result = messagesWithReasoningFlags(messages);

      expect(result[0].hasReasoning).toBe(false);
    });

    it('should set hasReasoning for agent messages with reasoning', () => {
      const messages: ChatboxMessage[] = [
        {
          text: 'Agent response',
          persona: 'agent',
          reasoning: 'Agent reasoning',
          agentName: 'Agent1',
        },
      ];

      const result = messagesWithReasoningFlags(messages);

      expect(result[0].hasReasoning).toBe(true);
    });

    it('should set hasError for bot messages with errors', () => {
      const messages: ChatboxMessage[] = [
        {
          text: 'Error occurred',
          persona: 'bot',
          error: {
            message: 'Something went wrong',
            errorType: 'RuntimeError',
            timestamp: '2025-12-30T12:00:00Z',
          },
        },
      ];

      const result = messagesWithReasoningFlags(messages);

      expect(result[0].hasError).toBe(true);
    });

    it('should generate correct toggle IDs', () => {
      const messages: ChatboxMessage[] = [
        { text: 'First', persona: 'user' },
        { text: 'Second', persona: 'bot' },
        { text: 'Third', persona: 'user' },
      ];

      const result = messagesWithReasoningFlags(messages);

      expect(result[0].reasoningToggleId).toBe('reasoning-toggle-0');
      expect(result[1].reasoningToggleId).toBe('reasoning-toggle-1');
      expect(result[2].errorContentId).toBe('error-content-2');
    });
  });

  describe('totalReasoningMessages', () => {
    it('should return 0 for empty messages', () => {
      expect(totalReasoningMessages([])).toBe(0);
    });

    it('should count only bot messages with reasoning', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Hello', persona: 'user' },
        { text: 'Response 1', persona: 'bot', reasoning: 'Reasoning 1' },
        { text: 'Follow up', persona: 'user' },
        { text: 'Response 2', persona: 'bot', reasoning: 'Reasoning 2' },
        { text: 'Response 3', persona: 'bot' }, // No reasoning
      ];

      expect(totalReasoningMessages(messages)).toBe(2);
    });

    it('should not count agent messages', () => {
      const messages: ChatboxMessage[] = [
        {
          text: 'Agent response',
          persona: 'agent',
          reasoning: 'Agent reasoning',
          agentName: 'Agent1',
        },
      ];

      expect(totalReasoningMessages(messages)).toBe(0);
    });
  });

  describe('streamingMessageIndex', () => {
    it('should return -1 for empty messages', () => {
      expect(streamingMessageIndex([])).toBe(-1);
    });

    it('should return -1 when last bot message is not typing', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Response', persona: 'bot', typing: false },
      ];

      expect(streamingMessageIndex(messages)).toBe(-1);
    });

    it('should return index when last bot message is typing', () => {
      const messages: ChatboxMessage[] = [
        { text: 'Hello', persona: 'user' },
        { text: 'Typing...', persona: 'bot', typing: true },
      ];

      expect(streamingMessageIndex(messages)).toBe(1);
    });

    it('should return -1 when no bot messages exist', () => {
      const messages: ChatboxMessage[] = [{ text: 'Hello', persona: 'user' }];

      expect(streamingMessageIndex(messages)).toBe(-1);
    });
  });
});
