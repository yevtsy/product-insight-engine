# Agent CLI Guide

## Overview

The Aggregator Agent provides an interactive command-line interface (CLI) for analyzing product features using multiple LLM agents. The CLI is powered by Spring Shell and provides a rich, interactive experience.

## Starting the CLI

### Option 1: With Web Server (Default)
Run the application normally. The CLI will be available alongside the REST API:

```bash
cd aggregator-agent
../gradlew bootRun
```

Then connect to the shell from another terminal:
```bash
ssh -p 8080 localhost
```

### Option 2: CLI Only Mode
Run with CLI profile to disable the web server:

```bash
cd aggregator-agent
../gradlew bootRun --args='--spring.profiles.active=cli --spring.main.web-application-type=none'
```

## Available Commands

### 1. Full Feature Analysis

Analyze a feature using all agents (Facebook, Instagram, Reddit, Analytics):

```bash
agent-cli> analyze --feature-id "feat-123" --feature-name "Shopping Cart" --keywords "cart,checkout,buy"
```

**Output:**
- Social media analysis from Facebook, Instagram, and Reddit
- Internal metrics (purchases, logins, session duration)
- Overall health score (0.0 - 1.0)
- Actionable recommendations

### 2. Facebook Analysis

Analyze Facebook comments only:

```bash
agent-cli> facebook --feature "Shopping Cart" --keywords "cart,buy" --max-comments 50
```

**Options:**
- `--feature` (required): Feature name to search for
- `--keywords` (optional): Comma-separated keywords for filtering
- `--max-comments` (optional): Maximum comments to fetch (default: 100)

### 3. Instagram Analysis

Analyze Instagram comments only:

```bash
agent-cli> instagram --feature "Shopping Cart" --keywords "cart" --max-comments 50
```

**Options:**
- `--feature` (required): Feature name to search for
- `--keywords` (optional): Comma-separated keywords for filtering
- `--max-comments` (optional): Maximum comments to fetch (default: 100)

### 4. Reddit Analysis

Analyze Reddit thread comments:

```bash
agent-cli> reddit --feature "Shopping Cart" --thread-url "https://reddit.com/r/test/comments/abc123" --keywords "cart" --max-comments 100
```

**Options:**
- `--feature` (required): Feature name to search for
- `--thread-url` (required): Reddit thread URL to analyze
- `--keywords` (optional): Comma-separated keywords for filtering
- `--max-comments` (optional): Maximum comments to fetch (default: 100)

### 5. Get Internal Metrics

Retrieve internal analytics metrics:

```bash
agent-cli> metrics --feature-id "feat-123"
```

**Output:**
- Purchase count (last 30 days)
- Login count (last 30 days)
- Average session duration (minutes)

### 6. Help Commands

Display agent-specific help:
```bash
agent-cli> agent-help
```

Display Spring Shell built-in help:
```bash
agent-cli> help
```

List all available commands:
```bash
agent-cli> help commands
```

## Command Examples

### Example 1: Quick Feature Check
```bash
agent-cli> metrics --feature-id "cart-v2"
```

### Example 2: Social Media Sentiment
```bash
agent-cli> facebook --feature "Cart" --keywords "bug,error,broken"
agent-cli> instagram --feature "Cart" --keywords "love,great,amazing"
```

### Example 3: Comprehensive Analysis
```bash
agent-cli> analyze --feature-id "cart-v2" --feature-name "Shopping Cart v2" --keywords "cart,checkout,payment"
```

### Example 4: Reddit Discussion Analysis
```bash
agent-cli> reddit --feature "New Checkout" --thread-url "https://reddit.com/r/webdev/comments/xyz789/new_checkout_flow" --keywords "UX,payment,mobile"
```

## CLI Features

### Auto-completion
Press `TAB` to auto-complete commands and options:
```bash
agent-cli> ana<TAB>  # Completes to "analyze"
agent-cli> analyze --fea<TAB>  # Shows --feature-id and --feature-name
```

### Command History
Use arrow keys to navigate through command history:
- `↑` (Up Arrow): Previous command
- `↓` (Down Arrow): Next command

### Multi-line Input
Use backslash `\` for multi-line commands:
```bash
agent-cli> analyze \
           --feature-id "feat-123" \
           --feature-name "Shopping Cart" \
           --keywords "cart,checkout"
```

### Clear Screen
```bash
agent-cli> clear
```

### Exit CLI
```bash
agent-cli> exit
# or
agent-cli> quit
```

## Output Formatting

All commands provide formatted output with:
- Clear section headers with box drawing characters
- Structured information layout
- Easy-to-read metrics and analysis

Example output:
```
╔═══════════════════════════════════════════════════════════════════════╗
║                      FEATURE ANALYSIS RESULTS                         ║
╚═══════════════════════════════════════════════════════════════════════╝

Feature ID: feat-123
Health Score: 0.75 / 1.0

───────────────────────────────────────────────────────────────────────
SOCIAL MEDIA ANALYSIS
───────────────────────────────────────────────────────────────────────

FACEBOOK:
SUMMARY: Users show positive sentiment with some concerns...
KEY_THEMES:
- Easy to use
- Mobile experience
...
```

## Agent Architecture

The CLI interacts with the following LLM agents:

1. **FacebookScoutLlmAgent**: Analyzes Facebook comments using Gemini 2.5 Flash
2. **InstagramScoutLlmAgent**: Analyzes Instagram comments using Gemini 2.5 Flash
3. **RedditScoutLlmAgent**: Analyzes Reddit comments using Gemini 2.5 Flash
4. **AnalyticsLlmAgent**: Analyzes internal metrics via A2A communication

Each agent:
- Uses Google ADK for LLM integration
- Wraps dedicated tools (FacebookScoutTool, etc.)
- Provides structured analysis output
- Runs independently with its own context

## Configuration

### Environment Variables

Set these before running the CLI:

```bash
# Social Media APIs
export FACEBOOK_ACCESS_TOKEN="your-token"
export INSTAGRAM_ACCESS_TOKEN="your-token"
export REDDIT_CLIENT_ID="your-client-id"
export REDDIT_CLIENT_SECRET="your-secret"

# Analytics Agent
export ANALYTICS_AGENT_URL="http://localhost:8081/agent-to-agent"

# Optional: Reddit Thread URL (default for reddit command)
export REDDIT_THREAD_URL="https://reddit.com/r/default/comments/123"
```

### Spring Profiles

Use different profiles for different environments:

```bash
# Development with CLI
../gradlew bootRun --args='--spring.profiles.active=dev,cli'

# Production with CLI
../gradlew bootRun --args='--spring.profiles.active=prod,cli'

# CLI only (no web server)
../gradlew bootRun --args='--spring.profiles.active=cli --spring.main.web-application-type=none'
```

## Troubleshooting

### Command Not Found
If a command is not recognized:
1. Check spelling with `help commands`
2. Ensure Spring Shell is properly initialized
3. Check logs for bean initialization errors

### API Errors
If you get API errors:
1. Verify API credentials are set
2. Check rate limits haven't been exceeded
3. Verify network connectivity
4. Check logs for detailed error messages

### No Response from Agent
If an agent doesn't respond:
1. Check agent initialization in logs
2. Verify Gemini API access
3. Check thread pool configuration
4. Increase timeout if needed

### Analytics Agent Connection Failed
If metrics command fails:
1. Ensure analytics-agent is running
2. Verify ANALYTICS_AGENT_URL is correct
3. Check analytics-agent health: `curl http://localhost:8081/actuator/health`
4. Review A2A communication logs

## Advanced Usage

### Batch Processing

Create a script file with commands:

```bash
# commands.txt
analyze --feature-id "feat-1" --feature-name "Feature 1" --keywords "new,feature"
analyze --feature-id "feat-2" --feature-name "Feature 2" --keywords "update,fix"
metrics --feature-id "feat-1"
metrics --feature-id "feat-2"
```

Run with:
```bash
cat commands.txt | java -jar aggregator-agent.jar --spring.profiles.active=cli
```

### Scripting

Use the CLI in shell scripts:

```bash
#!/bin/bash
# analyze-features.sh

FEATURES=("feat-1" "feat-2" "feat-3")

for feature in "${FEATURES[@]}"; do
  echo "Analyzing $feature..."
  echo "analyze --feature-id \"$feature\" --feature-name \"Feature $feature\"" | \
    java -jar aggregator-agent.jar --spring.profiles.active=cli
done
```

### JSON Output (Future Enhancement)

Add `--format json` option for machine-readable output:
```bash
agent-cli> analyze --feature-id "feat-123" --format json > result.json
```

## Best Practices

1. **Use meaningful keywords**: Better filtering improves analysis quality
2. **Start with individual agents**: Test each platform before running full analysis
3. **Check metrics first**: Understand quantitative data before qualitative analysis
4. **Use appropriate comment limits**: Balance between completeness and performance
5. **Monitor rate limits**: Respect API quotas for social media platforms
6. **Review logs**: Enable DEBUG logging for troubleshooting

## Integration with REST API

The CLI and REST API can run simultaneously. Use CLI for:
- Interactive exploration
- Ad-hoc analysis
- Testing and development
- Manual investigation

Use REST API for:
- Automated workflows
- Integration with other systems
- Scheduled analysis jobs
- Production deployments

## See Also

- [Architecture Summary](../../architecture_summary.md) - System architecture
- [Spring Shell Documentation](https://docs.spring.io/spring-shell/docs/current/reference/html/)
- [Google ADK Documentation](https://github.com/google/adk-toolkit)