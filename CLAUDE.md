# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 4.0 application (Java 21) that acts as an AI-powered email agent. It receives emails via Mailgun webhooks, processes them using Goose CLI, and sends responses back via email. Emails are processed asynchronously and responses are formatted from markdown to HTML.

## Architecture

### Package Structure (Package-by-Feature)
- `org.tanzu.goosemail.agent` - Core business logic for email processing
- `org.tanzu.goosemail.mailgun` - Mailgun integration (webhooks and sending)
- `org.tanzu.goosemail.goose` - Goose CLI integration

### Key Components

**MailgunWebhookController** (`mailgun/MailgunWebhookController.java`)
- Receives POST requests at `/webhook/mailgun`
- Validates webhook signatures using HMAC-SHA256
- Extracts sender, subject, and body-plain from form parameters
- Delegates to MailAgentService for async processing

**MailAgentService** (`agent/MailAgentService.java`)
- Processes emails asynchronously using `@Async`
- Invokes Goose CLI via GooseService
- Sends responses via EmailService
- Handles errors by sending error emails back to sender

**GooseService** (`goose/GooseService.java`)
- Wraps the `goose-cf-wrapper` library (v1.1.0)
- Creates temporary workspaces for each Goose execution
- Cleans up workspaces after execution
- Email body becomes the prompt for Goose

**EmailService** (`mailgun/EmailService.java`)
- Sends emails via Mailgun REST API using WebClient
- Converts markdown responses to HTML using MarkdownService
- Sends both plain text (markdown) and HTML versions

**MarkdownService** (`mailgun/MarkdownService.java`)
- Converts markdown to styled HTML using CommonMark library
- Applies GitHub-style CSS formatting to emails

### Configuration

The application requires these environment variables:
- `MAILGUN_API_KEY` - Mailgun API key for sending emails
- `MAILGUN_SIGNING_KEY` - For verifying webhook signatures
- `ANTHROPIC_API_KEY` - Anthropic API key for Goose CLI (or other provider keys)

Application properties in `src/main/resources/application.properties`:
- `mailgun.domain` - The Mailgun domain
- `mailgun.from` - From address for outgoing emails

Goose CLI settings in `src/main/resources/.goose-config.yml`:
- Configures the Goose CLI instance
- Current settings: enabled, latest version, anthropic provider, claude-sonnet-4 model

## Development Commands

### Build
```bash
./mvnw clean package
```

### Run locally
```bash
./mvnw spring-boot:run
```

### Run tests
```bash
./mvnw test
```

### Deploy to Cloud Foundry
```bash
cf push --vars-file vars.yaml
```

Note: The application uses the Goose buildpack (`goose-buildpack`) in addition to the Java buildpack. Unlike the Claude Code version, no Node.js buildpack is required since Goose is a native Rust binary.

## Dependencies

Key dependencies:
- Spring Boot 4.0.2 (Web, WebFlux)
- `goose-cf-wrapper` 1.1.0 (from GCP Artifact Registry)
- CommonMark 0.24.0 (markdown parsing)

The custom Maven repository is configured in pom.xml:
```
https://us-central1-maven.pkg.dev/cf-mcp/maven-public
```

## Comparison with Claude Code Version

| Aspect | mail-agent (Claude) | goose-mail-agent (Goose) |
|--------|---------------------|--------------------------|
| Wrapper | `claude-code-cf-wrapper` | `goose-cf-wrapper` |
| Service | `ClaudeCodeService` | `GooseService` |
| Buildpack | `nodejs_buildpack` + `claude-code-buildpack` | `goose-buildpack` only |
| Auth Env Var | `CLAUDE_CODE_OAUTH_TOKEN` | `ANTHROPIC_API_KEY` (or other provider) |
| Runtime Dependency | Node.js | None (native binary) |
