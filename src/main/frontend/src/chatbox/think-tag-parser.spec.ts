import { ThinkTagParser, ParseResult } from './think-tag-parser';

describe('ThinkTagParser', () => {
  let parser: ThinkTagParser;

  beforeEach(() => {
    parser = new ThinkTagParser();
  });

  describe('Basic functionality', () => {
    it('should create parser instance', () => {
      expect(parser).toBeTruthy();
    });

    it('should handle empty chunk', () => {
      const result = parser.processChunk('');
      expect(result.mainContent).toBe('');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(false);
    });

    it('should handle null/undefined chunk', () => {
      const result1 = parser.processChunk(null as any);
      expect(result1.mainContent).toBe('');
      expect(result1.reasoningContent).toBe('');

      const result2 = parser.processChunk(undefined as any);
      expect(result2.mainContent).toBe('');
      expect(result2.reasoningContent).toBe('');
    });

    it('should handle plain text without think tags', () => {
      const result = parser.processChunk('Hello world');
      expect(result.mainContent).toBe('Hello world');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(true);
    });
  });

  describe('Single complete think tag', () => {
    it('should parse complete think tag in single chunk', () => {
      const result = parser.processChunk('Before <think>reasoning content</think> After');
      expect(result.mainContent).toBe('Before  After');
      expect(result.reasoningContent).toBe('reasoning content');
      expect(result.isComplete).toBe(true);
    });

    it('should handle think tag at beginning', () => {
      const result = parser.processChunk('<think>reasoning</think>Main content');
      expect(result.mainContent).toBe('Main content');
      expect(result.reasoningContent).toBe('reasoning');
      expect(result.isComplete).toBe(true);
    });

    it('should handle think tag at end', () => {
      const result = parser.processChunk('Main content<think>reasoning</think>');
      expect(result.mainContent).toBe('Main content');
      expect(result.reasoningContent).toBe('reasoning');
      expect(result.isComplete).toBe(true);
    });

    it('should handle only think tag', () => {
      const result = parser.processChunk('<think>only reasoning</think>');
      expect(result.mainContent).toBe('');
      expect(result.reasoningContent).toBe('only reasoning');
      expect(result.isComplete).toBe(true);
    });
  });

  describe('Streaming scenarios - split tags', () => {
    it('should handle opening tag split across chunks', () => {
      let result = parser.processChunk('Before <th');
      expect(result.mainContent).toBe('Before <th');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(false);

      result = parser.processChunk('ink>reasoning');
      expect(result.mainContent).toBe('');
      expect(result.reasoningContent).toBe('reasoning');
      expect(result.isComplete).toBe(false);

      result = parser.processChunk('</think> After');
      expect(result.mainContent).toBe(' After');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(true);
    });

    it('should handle closing tag split across chunks', () => {
      let result = parser.processChunk('<think>reasoning</th');
      expect(result.mainContent).toBe('');
      expect(result.reasoningContent).toBe('reasoning</th');
      expect(result.isComplete).toBe(false);

      result = parser.processChunk('ink> After');
      expect(result.mainContent).toBe(' After');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(true);
    });

    it('should handle content split across chunks', () => {
      let result = parser.processChunk('Before <think>reason');
      expect(result.mainContent).toBe('Before ');
      expect(result.reasoningContent).toBe('reason');
      expect(result.isComplete).toBe(false);

      result = parser.processChunk('ing content');
      expect(result.mainContent).toBe('');
      expect(result.reasoningContent).toBe('ing content');
      expect(result.isComplete).toBe(false);

      result = parser.processChunk('</think> After');
      expect(result.mainContent).toBe(' After');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(true);
    });
  });

  describe('Multiple think tags', () => {
    it('should handle multiple think tags in single chunk', () => {
      const result = parser.processChunk('Start <think>first</think> middle <think>second</think> end');
      expect(result.mainContent).toBe('Start  middle  end');
      expect(result.reasoningContent).toBe('firstsecond');
      expect(result.isComplete).toBe(true);
    });

    it('should handle multiple think tags across chunks', () => {
      let result = parser.processChunk('Start <think>first</think> mid');
      expect(result.mainContent).toBe('Start  mid');
      expect(result.reasoningContent).toBe('first');

      result = parser.processChunk('dle <think>second</think> end');
      expect(result.mainContent).toBe('dle  end');
      expect(result.reasoningContent).toBe('second');
      expect(result.isComplete).toBe(true);
    });
  });

  describe('Nested think tags', () => {
    it('should handle nested think tags', () => {
      const result = parser.processChunk('Before <think>outer <think>inner</think> outer</think> After');
      expect(result.mainContent).toBe('Before  After');
      expect(result.reasoningContent).toBe('outer inner outer');
      expect(result.isComplete).toBe(true);
    });

    it('should handle deeply nested think tags', () => {
      const result = parser.processChunk('<think>level1 <think>level2 <think>level3</think> level2</think> level1</think>');
      expect(result.mainContent).toBe('');
      expect(result.reasoningContent).toBe('level1 level2 level3 level2 level1');
      expect(result.isComplete).toBe(true);
    });

    it('should handle nested tags across chunks', () => {
      let result = parser.processChunk('<think>outer <think>in');
      expect(result.mainContent).toBe('');
      expect(result.reasoningContent).toBe('outer in');
      expect(result.isComplete).toBe(false);

      result = parser.processChunk('ner</think> outer</think> main');
      expect(result.mainContent).toBe(' main');
      expect(result.reasoningContent).toBe('ner outer');
      expect(result.isComplete).toBe(true);
    });
  });

  describe('Edge cases', () => {
    it('should handle incomplete opening tag', () => {
      const result = parser.processChunk('Before <think incomplete');
      expect(result.mainContent).toBe('Before <think incomplete');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(false);
    });

    it('should handle incomplete closing tag', () => {
      let result = parser.processChunk('<think>reasoning</think incomplete');
      expect(result.mainContent).toBe('');
      expect(result.reasoningContent).toBe('reasoning</think incomplete');
      expect(result.isComplete).toBe(false);
    });

    it('should handle malformed tags', () => {
      const result = parser.processChunk('Before <think>reasoning</thonk> After');
      expect(result.mainContent).toBe('Before ');
      expect(result.reasoningContent).toBe('reasoning</thonk> After');
      expect(result.isComplete).toBe(false);
    });

    it('should handle unmatched opening tag', () => {
      const result = parser.processChunk('Before <think>reasoning without close');
      expect(result.mainContent).toBe('Before ');
      expect(result.reasoningContent).toBe('reasoning without close');
      expect(result.isComplete).toBe(false);
    });

    it('should handle unmatched closing tag', () => {
      const result = parser.processChunk('Before </think> After');
      expect(result.mainContent).toBe('Before </think> After');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(true);
    });

    it('should handle empty think tag', () => {
      const result = parser.processChunk('Before <think></think> After');
      expect(result.mainContent).toBe('Before  After');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(true);
    });

    it('should handle think tag with only whitespace', () => {
      const result = parser.processChunk('Before <think>   </think> After');
      expect(result.mainContent).toBe('Before  After');
      expect(result.reasoningContent).toBe('   ');
      expect(result.isComplete).toBe(true);
    });
  });

  describe('State management', () => {
    it('should accumulate content correctly', () => {
      parser.processChunk('First chunk ');
      parser.processChunk('<think>reasoning</think>');
      parser.processChunk(' last chunk');

      expect(parser.getMainContent()).toBe('First chunk  last chunk');
      expect(parser.getReasoningContent()).toBe('reasoning');
      expect(parser.hasReasoning()).toBe(true);
    });

    it('should reset state correctly', () => {
      parser.processChunk('Content <think>reasoning</think>');
      expect(parser.hasReasoning()).toBe(true);
      expect(parser.getMainContent()).toBe('Content ');

      parser.reset();
      expect(parser.hasReasoning()).toBe(false);
      expect(parser.getMainContent()).toBe('');
      expect(parser.getReasoningContent()).toBe('');
      expect(parser.isInThinkTag()).toBe(false);
    });

    it('should track think tag state correctly', () => {
      expect(parser.isInThinkTag()).toBe(false);

      parser.processChunk('<think>reasoning');
      expect(parser.isInThinkTag()).toBe(true);

      parser.processChunk('</think>');
      expect(parser.isInThinkTag()).toBe(false);
    });
  });

  describe('Malformed tag handling', () => {
    it('should provide fallback for malformed content', () => {
      const result = parser.handleMalformedTags('malformed content');
      expect(result.mainContent).toBe('malformed content');
      expect(result.reasoningContent).toBe('');
      expect(result.isComplete).toBe(true);
    });
  });

  describe('Debug functionality', () => {
    it('should provide debug state information', () => {
      parser.processChunk('Before <think>reasoning');
      const debugState = parser.getDebugState();

      expect(debugState.buffer).toBe('reasoning');
      expect(debugState.inThinkTag).toBe(true);
      expect(debugState.thinkTagDepth).toBe(1);
      expect(debugState.mainContentLength).toBe(7); // 'Before '
      expect(debugState.reasoningContentLength).toBe(9); // 'reasoning'
    });
  });

  describe('Real-world scenarios', () => {
    it('should handle typical AI response with reasoning', () => {
      const chunks = [
        'Let me think about this problem.',
        '<think>',
        'The user is asking about parsing HTML-like tags.',
        'I need to consider edge cases like nested tags and streaming.',
        'The approach should be to use a state machine.',
        '</think>',
        'Here is my response: I recommend using a state machine approach.'
      ];

      let fullMainContent = '';
      let fullReasoningContent = '';

      chunks.forEach(chunk => {
        const result = parser.processChunk(chunk);
        fullMainContent += result.mainContent;
        fullReasoningContent += result.reasoningContent;
      });

      expect(fullMainContent).toBe('Let me think about this problem.Here is my response: I recommend using a state machine approach.');
      expect(fullReasoningContent).toBe('The user is asking about parsing HTML-like tags.I need to consider edge cases like nested tags and streaming.The approach should be to use a state machine.');
      expect(parser.hasReasoning()).toBe(true);
    });

    it('should handle mixed content with multiple reasoning blocks', () => {
      const input = 'First part <think>first reasoning</think> middle part <think>second reasoning</think> final part';
      const result = parser.processChunk(input);

      expect(result.mainContent).toBe('First part  middle part  final part');
      expect(result.reasoningContent).toBe('first reasoningsecond reasoning');
      expect(result.isComplete).toBe(true);
    });

    it('should handle streaming with partial tags and content', () => {
      const chunks = [
        'Starting content <th',
        'ink>This is reasoning content that spans ',
        'multiple chunks and includes some complex ',
        'analysis of the problem</th',
        'ink> and this is the final content.'
      ];

      let accumulatedMain = '';
      let accumulatedReasoning = '';

      chunks.forEach(chunk => {
        const result = parser.processChunk(chunk);
        accumulatedMain += result.mainContent;
        accumulatedReasoning += result.reasoningContent;
      });

      expect(accumulatedMain).toBe('Starting content  and this is the final content.');
      expect(accumulatedReasoning).toBe('This is reasoning content that spans multiple chunks and includes some complex analysis of the problem');
      expect(parser.isInThinkTag()).toBe(false);
    });
  });
});