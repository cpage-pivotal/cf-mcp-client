# Getting Started with Tanzu Platform Chat: A Guide to Cloud Foundry AI/ML Services

## Welcome! 🚀

Welcome to your journey into AI-powered applications on Cloud Foundry! In this hands-on guide, you'll build and deploy a sophisticated chat application that progressively gains new AI capabilities. Starting with a simple interface, you'll add intelligent chat responses, document understanding, external tool usage, and persistent memory—all without managing any infrastructure yourself.

What makes this special? Cloud Foundry's service marketplace transforms complex AI infrastructure into simple, self-service building blocks. Instead of provisioning GPU clusters, configuring vector databases, or managing model deployments, you'll use simple `cf create-service` and `cf bind-service` commands. Cloud Foundry handles all the heavy lifting—SSL certificates, load balancing, scaling, updates—letting you focus on building great applications.

By the end of this guide, you'll have:
- 💬 An AI chatbot powered by enterprise LLMs
- 📚 Document Q&A using Retrieval Augmented Generation (RAG)
- 🔧 Tool-using AI via Model Context Protocol (MCP) agents
- 🧠 Persistent conversation memory across sessions

Let's turn infrastructure complexity into developer simplicity!

## Prerequisites

Before starting this guide, ensure you have:
- Cloud Foundry installed and configured
- GenAI on Tanzu Platform tile installed
- VMware Postgres for Tanzu Application Service tile installed
- CF CLI installed and logged into your Cloud Foundry foundation
- Java 21+ and Maven 3.8+ installed locally
- Git installed for cloning repositories

## Building and Deploying the Application

### Step 1: Clone and Build the Application

First, clone the Tanzu Platform Chat repository and build it:

```bash
# Clone the repository
git clone https://github.com/cpage-pivotal/cf-mcp-client
cd cf-mcp-client

# Build the application
mvn clean package
```

The build process will:
- Compile the Spring Boot backend
- Build the Angular frontend
- Package everything into a single JAR file

### Step 2: Deploy to Cloud Foundry

Now let's deploy your application to Cloud Foundry:

```bash
cf push
```

This command reads the `manifest.yml` file and deploys your application with 1GB of memory and Java 21 runtime. After about a minute, you'll see:

```
Showing health and status for app ai-tool-chat...

requested state: started
routes:          ai-tool-chat.apps.your-cf-domain.com
```

Open the provided URL in your browser to see your chat application.

## Understanding the Basic Chat Interface

### Step 3: Explore the Application Without AI

When you first open the application, you'll see:
- A clean chat interface at the center
- Four control buttons on the right side: Chat 💬, Documents 📄, Agents 🔌, and Memory 🧠

Let's see what happens without any AI services:

1. Type a message like "Hello, how are you?" in the chat box
2. Press Send or hit Enter
3. **Result:** The bot responds with "No chat model available"

Click each button on the right to explore the status panels:
- **Chat 💬**: Shows "Chat Model: Not Available ❌"
- **Documents 📄**: Shows "Vector Store: Not Available ❌" and "Embed Model: Not Available ❌"
- **Agents 🔌**: Shows "Status: Not Available ❌"
- **Memory 🧠**: Shows "Memory: Transient ⚠️" (only lasts during the session)

This demonstrates that your application is running but has no AI capabilities yet. Let's fix that!

## Enabling AI Chat with an LLM

### Step 4: Create and Bind a Chat Model

Large Language Models (LLMs) are AI systems trained on vast amounts of text that can understand and generate human-like responses. Cloud Foundry makes accessing these powerful models as easy as binding a database.

First, check what GenAI plans are available:

```bash
cf marketplace -s genai
```

You'll see various model options like `gpt-4o-mini`, `claude-3-haiku`, or others. Choose one and create a service instance:

```bash
# Create a chat LLM service instance (replace 'gpt-4o-mini' with your chosen plan)
cf create-service genai gpt-4o-mini chat-llm

# Bind the service to your application
cf bind-service ai-tool-chat chat-llm

# Restart the application to pick up the new service
cf restart ai-tool-chat
```

**What just happened?** Cloud Foundry:
- Provisioned access to an enterprise LLM
- Generated secure API credentials
- Injected them into your application's environment
- All without you touching a single API key or endpoint URL!

### Step 5: Test AI-Powered Chat

1. Refresh your browser
2. Click the Chat button (💬) to verify the model is connected
   - You should see: Chat Model: [your-model-name] ✅
3. Send a message like "What is Cloud Foundry?"

**What you'll see:** The AI will now provide intelligent responses about Cloud Foundry, demonstrating that the LLM is working correctly.

## Implementing RAG (Retrieval Augmented Generation)

RAG is a powerful technique that lets AI answer questions about YOUR documents. Instead of relying only on its training data, the AI can access and reference specific information you provide. Think of it as giving the AI a personal library card to your document collection.

### Step 6: Set Up Vector Database and Embeddings

To implement RAG, we need two key components:
- **Embeddings Model**: Converts text into numerical representations (vectors) that capture meaning
- **Vector Database**: Stores and searches these vectors to find relevant information quickly

Let's set them up:

```bash
# Check available embedding models
cf marketplace -s genai

# Create an embeddings model service (e.g., text-embedding-3-small)
cf create-service genai text-embedding-3-small embeddings-llm

# Create a PostgreSQL database with vector support
cf create-service postgres on-demand-postgres-db vector-db
```
**Note:** The database provisioning might take 2-3 minutes. You can check its status with:
```bash
cf service vector-db
```

```bash
# Bind both services to your application
cf bind-service ai-tool-chat embeddings-llm
cf bind-service ai-tool-chat vector-db

# Restart the application
cf restart ai-tool-chat
````


### Step 7: Upload and Query Documents

Now let's give your AI some documents to work with:

1. Click the **Documents** button (📄) on the right side
2. Verify both services are connected:
   - Vector Store: PgVectorStore ✅
   - Embed Model: text-embedding-3-small ✅
3. Click "Upload File" and select a PDF document
   - **Suggestion**: Use a technical document, user manual, or documentation PDF
   - The file will be processed and indexed automatically
   - You'll see a progress bar during upload

**Behind the scenes:**
1. Your PDF is split into digestible chunks
2. Each chunk is converted to a vector (a list of numbers representing its meaning)
3. These vectors are stored in PostgreSQL using the pgvector extension
4. When you ask a question, it's converted to a vector too
5. The database finds the most similar document chunks
6. These relevant chunks are given to the LLM as context

This all happens in milliseconds, thanks to Cloud Foundry's optimized infrastructure!

### Step 8: Test Document-Based Q&A

Send a question about your uploaded document:
- "What does this document say about [topic]?"
- "Can you summarize the main points?"

The AI will now answer based on the document content, providing accurate information even if the LLM wasn't originally trained on your specific document.

## Extending Capabilities with MCP (Model Context Protocol)

While LLMs are great at generating text, they can't naturally interact with external systems or access real-time data. MCP solves this by giving AI models the ability to use tools, invoking software functions in real time. This opens up possibilities like checking current data, querying databases, or calling APIs.

### Step 9: See the Limitation, Then Fix It

First, let's see what happens without MCP:

1. Click "Clear Files" in the Documents panel to remove distractions
2. Ask the chat: "What time is it?"
3. The AI will explain it cannot access real-time information

Now let's give it the ability to tell time!

### Step 10: Deploy a Time MCP Server

MCP servers are lightweight applications that expose specific tools to AI models. Let's deploy one and connect it using the SSE protocol:

```bash
# Clone the Time MCP Server
git clone https://github.com/cpage-pivotal/time-mcp-server
cd time-mcp-server

# Build the application
./mvnw clean package

# Deploy to Cloud Foundry (in the same space as your chat app)
cf push

# Note the URL it deploys to (e.g., https://time-mcp-server.apps.your-cf-domain.com)

# Create a user-provided service pointing to the MCP server using SSE protocol
cf cups mcp-time-server -p '{"mcpSseURL":"https://time-mcp-server.apps.your-cf-domain.com"}'

# Return to the chat app directory and bind the service
cd ../cf-mcp-client
cf bind-service ai-tool-chat mcp-time-server

# Restart the chat application
cf restart ai-tool-chat
```

**Alternative: Streamable HTTP Protocol**

If your MCP server supports Streamable HTTP Protocol instead of SSE, use:

```bash
# Create service with Streamable HTTP protocol
cf cups mcp-time-server -p '{"mcpStreamableURL":"https://time-mcp-server.apps.your-cf-domain.com"}'
```

### Step 11: Test MCP Tool Usage

With the MCP server connected, your AI now has new abilities:

1. Click the **Agents** button (🔌) to see available tools
   - You should see: mcp-time-server listed under "Available Agents" ✅
2. Ask again: "What time is it?"
   - The AI will now provide the actual current time!

**Watch the magic happen:**
- The LLM recognizes it needs current time information
- It discovers the `get_current_time` tool from the MCP server
- It calls the tool and receives real-time data
- It incorporates this information into a natural response

Try these questions to see the AI using tools:
- "What time is it in Tokyo?"
- "How many hours until midnight?"
- "Is it morning or afternoon?"

The AI seamlessly combines its language understanding with real-world data access!

## Persistent Memory with Vector Storage

When both a vector database and embeddings model are available, your chat application gains a superpower: it remembers conversations even after restarts! This transforms a stateless application into one with long-term memory.

### Step 12: Experience Persistent Memory

1. Click the **Memory** button (🧠) to check your setup
   - Memory: Persistent ✅ (thanks to vector store + embeddings)
   - Note your Conversation ID (this links your chat history)

2. Have a personal conversation with the AI:
   - "My name is Alex and I work on Cloud Foundry automation"
   - "I'm particularly interested in AI/ML integration"
   - "My favorite programming language is Python"

3. Ask a question that requires context:
   - "Based on what you know about me, what AI projects might I enjoy?"

4. Restart the application:
   ```bash
   cf restart ai-tool-chat
   ```

5. Once restarted, open the app and ask:
   - "What do you remember about me?"
   - "What's my name and what do I work on?"

**How it works:**
- Each message is converted to a vector and stored with your conversation ID
- On restart, the app retrieves relevant past conversations from the vector database
- The LLM uses this retrieved context to maintain continuity
- Your conversation history persists as long as you use the same browser session

This is all handled automatically by Cloud Foundry's managed services—no session management code needed!

## 🎉 Congratulations!

You've built a sophisticated AI application using Cloud Foundry's platform services! Let's recap your achievements:

- ✅ **Deployed** an AI chat application with zero infrastructure setup
- ✅ **Connected** enterprise LLMs with simple service bindings
- ✅ **Implemented** RAG for intelligent document Q&A
- ✅ **Extended** your AI with tool-using capabilities via MCP
- ✅ **Enabled** persistent memory that survives restarts

### Your Application Architecture

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Angular UI     │────▶│ Spring Boot  │────▶│  Chat LLM       │
│                 │     │  Backend     │     │  (GPT/Claude)   │
└─────────────────┘     └──────┬───────┘     └─────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
              ┌─────▼──────┐       ┌─────▼──────┐
              │ Vector DB  │       │ MCP Server │
              │ + Embeddings│       │  (Tools)   │
              └────────────┘       └────────────┘
```

All managed by Cloud Foundry—you just focus on your application logic!

### Troubleshooting Tips

**Chat not responding?**
- Check the Chat panel (💬) - ensure a model shows as available
- View logs: `cf logs ai-tool-chat --recent`
- Verify the service binding: `cf env ai-tool-chat`

**Documents not uploading?**
- Confirm both Vector Store and Embed Model show as available (📄)
- Check that your PDF is valid and under 10MB
- Ensure the database is fully provisioned: `cf service vector-db`

**MCP tools not working?**
- Verify the MCP server is running: `cf app time-mcp-server`
- Check the Agents panel (🔌) shows your service
- Confirm the mcpSseURL or mcpStreamableURL matches the deployed app URL
- Ensure you're using the correct protocol key for your MCP server type

**Memory not persisting?**
- Both vector store and embeddings must be bound
- Memory panel (🧠) should show "Persistent" status
- Use the same browser/session (cookies maintain your conversation ID)

### The Cloud Foundry Advantage

Notice what you DIDN'T have to do:
- ❌ Manage API keys or credentials
- ❌ Configure network security or SSL certificates
- ❌ Set up database schemas or vector indexes
- ❌ Handle service discovery or load balancing
- ❌ Write retry logic or connection pooling
- ❌ Monitor infrastructure or apply updates

Cloud Foundry turned complex AI infrastructure into simple, composable services. You spent your time building features, not fighting infrastructure. That's the power of a true cloud platform!

Happy building! 🚀