# Product Insight Engine - Architecture Sketch

## High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Product Manager / API Client                        │
└───────────────────────────────────┬─────────────────────────────────────────────┘
                                    │ HTTP GET /analyze
                                    │ (featureId, keywords)
                                    ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                    AGGREGATOR AGENT (Spring Boot :8080)                            │
│                                                                                    │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                      AggregatorController                                 │   │
│  │                         (REST API Layer)                                  │   │
│  └───────────────────────────────┬──────────────────────────────────────────┘   │
│                                  │                                                │
│  ┌───────────────────────────────▼──────────────────────────────────────────┐   │
│  │                     AggregatorService                                      │   │
│  │                  (Fan-Out/Fan-In Orchestrator)                            │   │
│  │                                                                            │   │
│  │  ┌─────────────────────────────────────────────────────────────┐         │   │
│  │  │           Thread Pool Executor (Async)                      │         │   │
│  │  └────┬─────────────┬─────────────┬─────────────┬──────────────┘         │   │
│  │       │             │             │             │                         │   │
│  │       │ Task 1      │ Task 2      │ Task 3      │ Task 4                 │   │
│  │       │             │             │             │                         │   │
│  └───────┼─────────────┼─────────────┼─────────────┼─────────────────────────┘   │
│          │             │             │             │                             │
│  ┌───────▼──────┐ ┌────▼──────┐ ┌───▼───────┐ ┌──▼──────────────────────────┐  │
│  │ FB Scout    │ │ IG Scout   │ │ Reddit    │ │ RestTemplate                │  │
│  │ LlmAgent    │ │ LlmAgent   │ │ LlmAgent  │ │ (Analytics Client)          │  │
│  │ (Gemini 2.5)│ │ (Gemini 2.5)│ │(Gemini 2.5)│ │                            │  │
│  └──────┬──────┘ └─────┬──────┘ └─────┬─────┘ └──┬──────────────────────────┘  │
│         │              │              │           │                              │
│         │wraps         │wraps         │wraps      │ HTTP POST                    │
│         │              │              │           │ (A2A Protocol)               │
│  ┌──────▼──────┐ ┌─────▼──────┐ ┌────▼──────┐   │                              │
│  │ FB Scout    │ │ IG Scout   │ │ Reddit    │   │                              │
│  │ Tool        │ │ Tool       │ │ Tool      │   │                              │
│  │ +RateLimiter│ │ +RateLimiter│ │+OAuth2   │   │                              │
│  └──────┬──────┘ └─────┬──────┘ └────┬──────┘   │                              │
│         │              │              │           │                              │
└─────────┼──────────────┼──────────────┼───────────┼──────────────────────────────┘
          │              │              │           │
          │ HTTP+OAuth   │ HTTP+OAuth   │ OAuth2    │
          │              │              │           │
     ┌────▼────┐    ┌────▼─────┐  ┌────▼────┐     │
     │Facebook │    │Instagram │  │ Reddit  │     │
     │Graph API│    │Graph API │  │   API   │     │
     │  v18.0  │    │  v18.0   │  │         │     │
     └─────────┘    └──────────┘  └─────────┘     │
                                                   │
                                                   │
          ┌────────────────────────────────────────┘
          │
          │ HTTP (Agent-to-Agent Communication)
          │
          ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                    ANALYTICS AGENT (Spring Boot :8081)                             │
│                                                                                    │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │              AgentToAgentController                                       │   │
│  │                 (A2A Protocol Handler)                                    │   │
│  └───────────────────────────────┬──────────────────────────────────────────┘   │
│                                  │                                                │
│  ┌───────────────────────────────▼──────────────────────────────────────────┐   │
│  │                    AnalyticsLlmAgent                                      │   │
│  │                    (Google ADK Agent)                                     │   │
│  │                    Gemini 2.5 Flash                                       │   │
│  └───────────────────────────────┬──────────────────────────────────────────┘   │
│                                  │ wraps                                          │
│  ┌───────────────────────────────▼──────────────────────────────────────────┐   │
│  │                        VerticaTool                                        │   │
│  │                     (Function Tool)                                       │   │
│  └───────────────────────────────┬──────────────────────────────────────────┘   │
│                                  │                                                │
│  ┌───────────────────────────────▼──────────────────────────────────────────┐   │
│  │                     VerticaMcpClient                                      │   │
│  │                   (JSON-RPC 2.0 Manager)                                 │   │
│  │                    - Process management                                   │   │
│  │                    - Retry logic                                          │   │
│  │                    - Health checks                                        │   │
│  │                    - Auto-reconnect                                       │   │
│  └───────────────────────────────┬──────────────────────────────────────────┘   │
│                                  │                                                │
└──────────────────────────────────┼────────────────────────────────────────────────┘
                                   │ stdin/stdout
                                   │ JSON-RPC 2.0
                                   ▼
                          ┌─────────────────────┐
                          │  MCP Server Process │
                          │  (nolleh/mcp-vertica)│
                          │  Node.js Process    │
                          └──────────┬──────────┘
                                     │ SQL
                                     ▼
                          ┌─────────────────────┐
                          │  Vertica Analytics  │
                          │      Database       │
                          └─────────────────────┘
```

## Key Architectural Patterns

### 1. Fan-Out/Fan-In Pattern
```
Request comes in
    ↓
Orchestrator fans out to 4 agents (parallel)
    ├─→ Facebook Scout Agent  ──→ Facebook API
    ├─→ Instagram Scout Agent ──→ Instagram API
    ├─→ Reddit Scout Agent    ──→ Reddit API
    └─→ Analytics Agent (A2A) ──→ Vertica DB
         ↓ (all complete)
    Fan-In: Aggregate results
         ↓
    Calculate Health Score (0.0-1.0)
         ↓
    Generate Recommendations
         ↓
    Return FeatureInsight
```

### 2. Tool-Agent Separation
```
┌───────────────┐         ┌───────────────────┐
│  Scout Tool   │  wraps  │   LLM Agent       │
│               │ ◄───────┤   (Intelligence)  │
│ - Fetch data  │         │                   │
│ - Auth/OAuth  │         │ - Sentiment       │
│ - Rate limit  │         │ - Themes          │
│ - Pagination  │         │ - Highlights      │
│ - NO ANALYSIS │         │ - Analysis        │
└───────┬───────┘         └───────────────────┘
        │
        ▼
   External API
```

### 3. Agent-to-Agent (A2A) Communication
```
Aggregator Agent             Analytics Agent
    (Service 1)                 (Service 2)
        │                           │
        │  HTTP POST                │
        │  /agent-to-agent          │
        ├──────────────────────────>│
        │                           │
        │  {                        │
        │    "action": "GET_METRICS"│
        │    "featureId": "cart-v2" │
        │  }                        │
        │                           │
        │                     Process
        │                           │
        │  JSON Response            │
        │<──────────────────────────┤
        │                           │
        │  {                        │
        │    "metrics": {...}       │
        │  }                        │
```

### 4. Model Context Protocol (MCP)
```
┌──────────────┐    JSON-RPC     ┌──────────────┐    SQL      ┌──────────┐
│  MCP Client  │ ──────────────> │  MCP Server  │ ─────────> │ Database │
│  (Java)      │  stdin/stdout   │  (Node.js)   │            │(Vertica) │
└──────────────┘                 └──────────────┘            └──────────┘

Benefits:
- No JDBC drivers needed
- Language agnostic
- Process isolation
- Retry/reconnection logic
- Fallback to mock data
```

## Data Flow Example

```
1. User Request:
   GET /analyze?featureId=shopping-cart&keyword=checkout

2. Orchestrator Fans Out (Parallel):
   ┌─────────────────────────────────────────┐
   │ Thread Pool (4 parallel CompletableFutures) │
   └─────────────────────────────────────────┘
              ↓         ↓         ↓         ↓
         [FB Scout] [IG Scout] [Reddit] [Analytics]
              ↓         ↓         ↓         ↓
    (Gemini analyzes Facebook comments)
    (Gemini analyzes Instagram comments)
    (Gemini analyzes Reddit thread)
    (Gemini analyzes Vertica metrics)

3. All Complete (Fan-In):
   Facebook Analysis: "Users love the new UI but report slow loading"
   Instagram Analysis: "Positive feedback on mobile experience"
   Reddit Analysis: "Some confusion about payment options"
   Analytics Metrics: 1200 purchases, 5000 logins, 8.5 min avg session

4. Health Score Calculation:
   Metrics Score: 0.65 (normalized from counts)
   Sentiment Score: 0.15 (mostly positive)
   Total: 0.80 (EXCELLENT)

5. Recommendation Generation:
   - Address loading performance issues (from FB)
   - Improve payment flow documentation (from Reddit)
   - Increase mobile marketing spend (from IG positive sentiment)

6. Response:
   {
     "featureId": "shopping-cart",
     "healthScore": 0.80,
     "socialSummary": "...",
     "metricsSummary": "...",
     "recommendations": [...]
   }
```

## Component Responsibilities

### Aggregator Agent Components:
| Component | Responsibility |
|-----------|---------------|
| AggregatorController | REST API endpoint handling |
| AggregatorService | Fan-out/fan-in orchestration |
| Thread Pool Executor | Parallel agent execution |
| Scout LLM Agents | Sentiment & theme analysis (Gemini) |
| Scout Tools | API data fetching, rate limiting |
| Health Calculator | Score computation (0.0-1.0) |
| Recommendation Engine | Pattern-based guidance |
| RestTemplate | HTTP client for A2A calls |

### Analytics Agent Components:
| Component | Responsibility |
|-----------|---------------|
| AgentToAgentController | A2A protocol handler |
| AnalyticsLlmAgent | Metrics analysis (Gemini) |
| VerticaTool | Function tool wrapper |
| VerticaMcpClient | MCP process management |
| MCP Server (external) | Database query execution |

## Technology Stack

### Core Framework:
- **Spring Boot 3.x** - Microservice framework
- **Java 21** - Programming language
- **CompletableFuture** - Async parallel execution

### LLM Integration:
- **Google Agent Development Kit (ADK)** - Agent framework
- **Gemini 2.5 Flash** - LLM model
- **InMemoryRunner** - Agent session management

### External Communication:
- **RestTemplate / WebClient** - HTTP clients
- **OAuth2** - Social media authentication
- **JSON-RPC 2.0** - MCP protocol

### Infrastructure:
- **Bucket4j** - Token bucket rate limiting
- **Model Context Protocol (MCP)** - Database abstraction
- **Thread Pool** - Concurrent execution management

## Deployment Architecture

```
┌─────────────────────────────────────────────┐
│         Development (Single Machine)         │
│                                              │
│  ┌──────────────────┐  ┌─────────────────┐ │
│  │ Aggregator :8080 │  │ Analytics :8081 │ │
│  └──────────┬───────┘  └────────┬────────┘ │
│             │                   │           │
│             └──────HTTP─────────┘           │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│      Production (Kubernetes/Cloud)           │
│                                              │
│  ┌──────────────────┐  ┌─────────────────┐ │
│  │ Aggregator Pod   │  │ Analytics Pod   │ │
│  │ (Auto-scaling)   │  │ (Auto-scaling)  │ │
│  └──────────┬───────┘  └────────┬────────┘ │
│             │                   │           │
│             └──────HTTP─────────┘           │
│                  (Service Mesh)              │
└─────────────────────────────────────────────┘
```

## Performance Characteristics

### Parallel Execution:
- **4 agents run simultaneously**
- **Total time = slowest agent** (not sum)
- **Typical analysis: < 30 seconds**

### Rate Limiting:
- Facebook: 200 req/min (configurable)
- Instagram: 200 req/min (configurable)
- Reddit: 60 req/min (configurable)

### Scalability:
- **Stateless services** - horizontal scaling
- **Thread pool tuning** - configurable cores
- **MCP isolation** - database connection pooling