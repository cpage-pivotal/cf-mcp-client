/**
 * Interface for the result of parsing a chunk of text
 */
export interface ParseResult {
  mainContent: string;
  reasoningContent: string;
  isComplete: boolean;
}

/**
 * Parser for handling <think> tags in streaming AI responses.
 * This parser is designed to work with chunked streaming data where
 * think tags may be split across multiple chunks.
 */
export class ThinkTagParser {
  private static readonly THINK_OPEN = '<think>';
  private static readonly THINK_CLOSE = '</think>';
  
  private buffer: string = '';
  private inThinkTag: boolean = false;
  private thinkContent: string = '';
  private mainContent: string = '';
  private thinkTagDepth: number = 0;
  
  /**
   * Processes a chunk of streaming text and separates main content from reasoning content
   * @param chunk The incoming text chunk
   * @returns ParseResult containing separated content and completion status
   */
  processChunk(chunk: string): ParseResult {
    if (!chunk) {
      return {
        mainContent: '',
        reasoningContent: '',
        isComplete: false
      };
    }
    
    // Add chunk to buffer
    this.buffer += chunk;
    
    let processedMainContent = '';
    let processedReasoningContent = '';
    let remainingBuffer = this.buffer;
    
    // Process the buffer looking for think tags
    while (remainingBuffer.length > 0) {
      if (!this.inThinkTag) {
        // Look for opening think tag
        const openIndex = remainingBuffer.indexOf(ThinkTagParser.THINK_OPEN);
        
        if (openIndex === -1) {
          // No opening tag found, add all remaining content to main content
          processedMainContent += remainingBuffer;
          this.mainContent += remainingBuffer;
          remainingBuffer = '';
          break;
        }
        
        // Found opening tag
        const beforeTag = remainingBuffer.substring(0, openIndex);
        processedMainContent += beforeTag;
        this.mainContent += beforeTag;
        
        // Move past the opening tag
        remainingBuffer = remainingBuffer.substring(openIndex + ThinkTagParser.THINK_OPEN.length);
        this.inThinkTag = true;
        this.thinkTagDepth = 1;
        
      } else {
        // We're inside a think tag, look for closing tag or nested opening tag
        const closeIndex = remainingBuffer.indexOf(ThinkTagParser.THINK_CLOSE);
        const openIndex = remainingBuffer.indexOf(ThinkTagParser.THINK_OPEN);
        
        // Determine which comes first (or if either exists)
        let nextTagIndex = -1;
        let isClosingTag = false;
        
        if (closeIndex === -1 && openIndex === -1) {
          // No tags found, add all to think content
          processedReasoningContent += remainingBuffer;
          this.thinkContent += remainingBuffer;
          remainingBuffer = '';
          break;
        } else if (closeIndex === -1) {
          // Only opening tag found
          nextTagIndex = openIndex;
          isClosingTag = false;
        } else if (openIndex === -1) {
          // Only closing tag found
          nextTagIndex = closeIndex;
          isClosingTag = true;
        } else {
          // Both tags found, use the one that comes first
          if (openIndex < closeIndex) {
            nextTagIndex = openIndex;
            isClosingTag = false;
          } else {
            nextTagIndex = closeIndex;
            isClosingTag = true;
          }
        }
        
        // Add content before the next tag to think content
        const beforeTag = remainingBuffer.substring(0, nextTagIndex);
        processedReasoningContent += beforeTag;
        this.thinkContent += beforeTag;
        
        if (isClosingTag) {
          // Handle closing tag
          this.thinkTagDepth--;
          if (this.thinkTagDepth === 0) {
            this.inThinkTag = false;
          }
          // Move past the closing tag
          remainingBuffer = remainingBuffer.substring(nextTagIndex + ThinkTagParser.THINK_CLOSE.length);
        } else {
          // Handle nested opening tag
          this.thinkTagDepth++;
          // Move past the opening tag
          remainingBuffer = remainingBuffer.substring(nextTagIndex + ThinkTagParser.THINK_OPEN.length);
        }
      }
    }
    
    // Update buffer with remaining content
    this.buffer = remainingBuffer;
    
    return {
      mainContent: processedMainContent,
      reasoningContent: processedReasoningContent,
      isComplete: !this.inThinkTag && this.buffer.length === 0
    };
  }
  
  /**
   * Gets the accumulated main content
   */
  getMainContent(): string {
    return this.mainContent;
  }
  
  /**
   * Gets the accumulated reasoning content
   */
  getReasoningContent(): string {
    return this.thinkContent;
  }
  
  /**
   * Checks if the parser is currently inside a think tag
   */
  isInThinkTag(): boolean {
    return this.inThinkTag;
  }
  
  /**
   * Checks if there's any accumulated reasoning content
   */
  hasReasoning(): boolean {
    return this.thinkContent.trim().length > 0;
  }
  
  /**
   * Resets the parser state for a new message
   */
  reset(): void {
    this.buffer = '';
    this.inThinkTag = false;
    this.thinkContent = '';
    this.mainContent = '';
    this.thinkTagDepth = 0;
  }
  
  /**
   * Handles malformed tags by treating them as regular content
   * This is a fallback method for when parsing fails
   */
  handleMalformedTags(content: string): ParseResult {
    // Simple fallback: treat everything as main content
    this.mainContent += content;
    return {
      mainContent: content,
      reasoningContent: '',
      isComplete: true
    };
  }
  
  /**
   * Fast check to see if content contains think tags without full parsing
   * This is used to optimize performance for non-reasoning content
   */
  static containsThinkTags(content: string): boolean {
    return content.includes('<think>') || content.includes('</think>');
  }
  
  /**
   * Checks if the parser has ever encountered think tags in the current session
   */
  hasEncounteredThinkTags(): boolean {
    return this.inThinkTag || this.thinkContent.length > 0 || this.buffer.includes('<think>');
  }
  
  /**
   * Gets the current state of the parser for debugging
   */
  getDebugState(): {
    buffer: string;
    inThinkTag: boolean;
    thinkTagDepth: number;
    mainContentLength: number;
    reasoningContentLength: number;
  } {
    return {
      buffer: this.buffer,
      inThinkTag: this.inThinkTag,
      thinkTagDepth: this.thinkTagDepth,
      mainContentLength: this.mainContent.length,
      reasoningContentLength: this.thinkContent.length
    };
  }
}