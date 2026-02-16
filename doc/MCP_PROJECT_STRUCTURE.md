# MCP Platform - Python Project Structure Proposal

> **Version**: 1.0 Draft  
> **Created**: February 2026  
> **Status**: Under Review

---

## 1. Executive Summary

This document proposes a **polyrepo architecture** with **separate repositories per MCP server**, using **FastMCP** as the framework. Shared libraries are published to a **private PyPI registry** (Artifactory/GitLab), enabling:

- **Independent deployments** — Each server upgrades shared libs at its own pace
- **Team velocity** — One developer can work on multiple servers without blocking others
- **Selective releases** — Low-traffic servers don't need redeployment when common code changes

---

## 2. Polyrepo Architecture Overview

### Repository Layout

```
GitHub Organization: your-org/
│
├── mcp-common               # Shared library → Published to private PyPI
├── mcp-testing              # Test utilities → Published to private PyPI
│
├── mcp-auth-extension       # VS Code extension → Published to Artifactory (.vsix)
│
├── topic-management-mcp     # Kafka topic management
├── database-management-mcp  # Database operations (PostgreSQL, etc.)
├── queue-management-mcp     # Queue operations (RabbitMQ, SQS, etc.)
└── ...                      # More servers as needed
```

### Dependency Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                 Private PyPI (Artifactory/Nexus)                    │
│  mcp-common==1.0.0, 1.1.0, 1.2.0                                   │
│  mcp-testing==1.0.0, 1.1.0                                         │
└─────────────────────────────────────────────────────────────────────┘
                                  ▲
              pip install mcp-common==X.Y.Z
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
  ┌───────────────┐      ┌───────────────┐      ┌───────────────┐
  │topic-mgmt-mcp │      │database-mgmt  │      │queue-mgmt-mcp │
  │==1.2.0        │      │-mcp==1.1.0    │      │==1.0.0        │
  │(latest)       │      │               │      │(stable)       │
  └───────────────┘      └───────────────┘      └───────────────┘
          ▲                      ▲                      ▲
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    HTTP + Authorization: Bearer JWT
                                 │
                    ┌────────────┴────────────┐
                    │  mcp-auth-extension     │
                    │  (VS Code Extension)    │
                    │  OAuth 2.0 PKCE auth    │
                    └─────────────────────────┘
```

---

## 2.1 Shared Library Repository: mcp-common

```
mcp-common/                           # Repo: your-org/mcp-common
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                    # Lint, test on PR
│   │   └── publish.yml               # Publish to private PyPI on tag
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   ├── feature_request.md
│   │   └── config.yml
│   └── PULL_REQUEST_TEMPLATE.md
│
├── .copilot/                         # ★ AI Agent Context (Copilot reads this first)
│   ├── instructions.md               # Project rules, constraints, standards
│   ├── patterns.md                   # Code patterns to follow/avoid
│   └── glossary.md                   # Domain terminology definitions
│
├── docs/                             # ★ Documentation Hub
│   ├── README.md                     # Documentation index/navigation
│   │
│   ├── adr/                          # Architecture Decision Records
│   │   ├── README.md                 # ADR index + template
│   │   ├── ADR-001-use-pydantic-settings.md
│   │   ├── ADR-002-structured-json-logging.md
│   │   └── ADR-003-timescaledb-for-audit.md
│   │
│   ├── design/                       # Technical Design Documents
│   │   ├── README.md                 # Design index
│   │   ├── context-extraction.md     # How Kong headers are processed
│   │   ├── audit-pipeline.md         # Audit event flow diagram
│   │   └── diagrams/                 # Draw.io, Mermaid, PlantUML sources
│   │       └── audit-flow.mmd
│   │
│   ├── requirements/                 # Requirements Specification
│   │   ├── README.md                 # Requirements index
│   │   ├── REQ-001-user-context.md   # Requirement: Extract user from headers
│   │   └── REQ-002-audit-logging.md  # Requirement: Log all tool invocations
│   │
│   └── api/                          # API Documentation
│       ├── README.md
│       └── mcp-common-api.md         # Public API reference
│
├── src/
│   └── mcp_common/
│       ├── __init__.py
│       ├── context/
│       │   ├── __init__.py
│       │   ├── user.py               # Extract user from Kong headers (X-User-Email, X-User-Teams)
│       │   └── middleware.py         # Context injection middleware
│       ├── config/
│       │   ├── __init__.py
│       │   ├── settings.py           # Pydantic settings base
│       │   └── secrets.py            # AWS Secrets Manager integration
│       ├── logging/
│       │   ├── __init__.py
│       │   ├── structured.py         # JSON structured logging
│       │   └── correlation.py        # Request correlation IDs
│       ├── errors/
│       │   ├── __init__.py
│       │   └── handlers.py           # Standardized error responses
│       ├── health/
│       │   ├── __init__.py
│       │   └── probes.py             # K8s readiness/liveness
│       ├── audit/
│       │   ├── __init__.py
│       │   ├── models.py             # ToolAuditEvent schema
│       │   ├── middleware.py         # @audit_tool decorator
│       │   └── writer.py             # TimescaleDB writer
│       └── metrics/
│           ├── __init__.py
│           └── prometheus.py         # Metrics exposition
│
├── tests/
│   ├── __init__.py
│   ├── test_context.py
│   ├── test_logging.py
│   └── test_audit.py
│
├── pyproject.toml                    # Package config with semver
├── CHANGELOG.md                      # Version history
├── CONTRIBUTING.md                   # How to contribute (coding standards, PR process)
├── .pre-commit-config.yaml
├── .secrets.baseline                 # detect-secrets baseline
└── README.md
```

### Publishing Workflow

```yaml
# mcp-common/.github/workflows/publish.yml
name: Publish to Private PyPI

on:
  push:
    tags:
      - 'v*'  # Trigger on version tags: v1.0.0, v1.1.0

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.12'
      
      - name: Install uv
        run: pip install uv
      
      - name: Build package
        run: uv build
      
      - name: Publish to Artifactory
        run: |
          uv publish \
            --repository https://artifactory.company.com/api/pypi/pypi-local \
            --username ${{ secrets.ARTIFACTORY_USER }} \
            --password ${{ secrets.ARTIFACTORY_TOKEN }}
```

---

## 2.2 MCP Server Repository: topic-management-mcp (Example)

```
topic-management-mcp/                 # Repo: your-org/topic-management-mcp
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                    # Lint, test on PR
│   │   ├── build.yml                 # Build & push Docker image
│   │   └── deploy.yml                # Deploy to K8s
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   ├── feature_request.md
│   │   └── config.yml
│   └── PULL_REQUEST_TEMPLATE.md
│
├── .copilot/                         # ★ AI Agent Context
│   ├── instructions.md               # Server-specific rules, Kafka patterns
│   ├── patterns.md                   # Tool implementation patterns
│   └── glossary.md                   # Kafka terminology (topic, partition, ACL, etc.)
│
├── docs/                             # ★ Documentation Hub
│   ├── README.md                     # Documentation index
│   │
│   ├── adr/                          # Architecture Decision Records
│   │   ├── README.md                 # ADR index + template
│   │   ├── ADR-001-confluent-kafka-client.md
│   │   ├── ADR-002-tool-naming-convention.md
│   │   └── ADR-003-partition-count-defaults.md
│   │
│   ├── design/                       # Technical Design
│   │   ├── README.md
│   │   ├── tool-design.md            # Tool interface design (inputs, outputs)
│   │   ├── error-handling.md         # Error codes, retry logic
│   │   ├── security-model.md         # ACL enforcement, user context usage
│   │   └── diagrams/
│   │       ├── tool-flow.mmd         # Mermaid: request → tool → Kafka
│   │       └── acl-model.mmd
│   │
│   ├── requirements/                 # Requirements Specification
│   │   ├── README.md                 # Requirements traceability matrix
│   │   ├── REQ-001-list-topics.md    # Requirement: List topics with filters
│   │   ├── REQ-002-create-topic.md   # Requirement: Create topic with validation
│   │   └── REQ-003-manage-acls.md    # Requirement: ACL assignment/revocation
│   │
│   ├── api/                          # API Documentation
│   │   ├── README.md
│   │   └── tools-reference.md        # All tools with examples
│   │
│   └── runbooks/                     # Operational Procedures
│       ├── README.md
│       ├── deployment.md             # How to deploy to each environment
│       ├── troubleshooting.md        # Common issues and solutions
│       └── incident-response.md      # What to do when things go wrong
│
├── src/
│   └── topic_management_mcp/
│       ├── __init__.py
│       ├── server.py                 # FastMCP server instance
│       ├── config.py                 # Server-specific settings
│       ├── tools/
│       │   ├── __init__.py
│       │   ├── topics.py             # create_topic, list_topics, etc.
│       │   ├── acls.py               # assign_acl, revoke_acl
│       │   └── clusters.py           # cluster_overview
│       ├── resources/
│       │   ├── __init__.py
│       │   └── topic_info.py         # topic://{name} resource
│       └── clients/
│           ├── __init__.py
│           └── admin.py              # Kafka AdminClient wrapper
│
├── tests/
│   ├── __init__.py
│   ├── conftest.py
│   ├── test_topics.py
│   └── test_acls.py
│
├── deploy/
│   └── helm/
│       ├── Chart.yaml
│       ├── values.yaml
│       ├── values-dev.yaml
│       ├── values-sit.yaml
│       ├── values-uat.yaml
│       └── values-prod.yaml
│
├── Dockerfile
├── pyproject.toml                    # Pins mcp-common version
├── uv.lock
├── CHANGELOG.md                      # Version history
├── CONTRIBUTING.md                   # How to contribute
├── .pre-commit-config.yaml
├── .secrets.baseline                 # detect-secrets baseline
├── .python-version                   # 3.12
└── README.md
```

### pyproject.toml with Pinned Dependency

```toml
# topic-management-mcp/pyproject.toml
[project]
name = "topic-management-mcp"
version = "2.1.0"
requires-python = ">=3.12"

dependencies = [
    "fastmcp>=2.0.0",
    "confluent-kafka>=2.3.0",
    "mcp-common==1.2.0",        # ← Pinned version from private PyPI
    "mcp-testing==1.0.0",       # ← Test utilities (dev dependency)
]

[tool.uv]
extra-index-url = "https://nexus.company.com/repository/pypi-internal/simple"
```

### Upgrading Shared Library

```bash
# When new mcp-common version is available:

# 1. Check changelog
git clone your-org/mcp-common && cat CHANGELOG.md

# 2. Update dependency in topic-management-mcp
cd topic-management-mcp
sed -i 's/mcp-common==1.1.0/mcp-common==1.2.0/' pyproject.toml

# 3. Run tests to verify compatibility
uv sync && uv run pytest

# 4. Commit and deploy
git commit -am "chore: upgrade mcp-common to 1.2.0"
git push
```

---

## 2.3 VS Code Auth Extension Repository: mcp-auth-extension

> **Purpose:** Provides centralized OAuth 2.0 PKCE authentication for all MCP servers.  
> **Technology:** TypeScript, VS Code Extension API  
> **Size:** ~350 lines of code total (includes auto-update checker)

```
mcp-auth-extension/                   # Repo: your-org/mcp-auth-extension
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                    # Lint, test on PR
│   │   ├── build.yml                 # Package .vsix
│   │   └── publish.yml               # Publish .vsix to Artifactory/Nexus
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   └── PULL_REQUEST_TEMPLATE.md
│
├── .copilot/                         # ★ AI Agent Context
│   ├── instructions.md               # Extension-specific rules, VS Code API patterns
│   ├── patterns.md                   # Auth patterns, token handling patterns
│   └── glossary.md                   # OAuth terms (PKCE, JWT, refresh token, etc.)
│
├── docs/                             # ★ Documentation Hub
│   ├── README.md                     # Documentation index
│   │
│   ├── adr/                          # Architecture Decision Records
│   │   ├── README.md
│   │   ├── ADR-001-oauth2-pkce.md    # Why PKCE over implicit flow
│   │   ├── ADR-002-token-in-memory.md # Why access token in memory, refresh in keychain
│   │   └── ADR-003-auto-update-pattern.md
│   │
│   ├── design/                       # Technical Design
│   │   ├── README.md
│   │   ├── auth-flow.md              # OAuth 2.0 PKCE flow diagram
│   │   ├── token-lifecycle.md        # Token storage, refresh, expiry
│   │   ├── extension-activation.md   # VS Code extension lifecycle
│   │   └── diagrams/
│   │       ├── oauth-flow.mmd
│   │       └── token-states.mmd
│   │
│   ├── requirements/                 # Requirements Specification
│   │   ├── README.md
│   │   ├── REQ-001-sso-signin.md     # Requirement: SSO sign-in via PingFederate
│   │   ├── REQ-002-silent-refresh.md # Requirement: Silent token refresh
│   │   └── REQ-003-auto-update.md    # Requirement: Auto-update notification
│   │
│   └── user-guide/                   # End-User Documentation
│       ├── README.md
│       ├── installation.md           # How to install from Artifactory
│       ├── troubleshooting.md        # Common issues (sign-in fails, etc.)
│       └── faq.md                    # Frequently asked questions
│
├── src/
│   ├── extension.ts                  # Entry point: activate(), deactivate()
│   │
│   ├── auth/
│   │   ├── provider.ts               # AuthenticationProvider (~150 lines)
│   │   │                             # - Implements vscode.AuthenticationProvider
│   │   │                             # - OAuth 2.0 PKCE flow with PingFederate
│   │   │                             # - Handles sign-in, sign-out, session management
│   │   │
│   │   ├── session.ts                # AuthSession interface
│   │   │                             # - accessToken, account, scopes
│   │   │
│   │   └── constants.ts              # Auth configuration constants
│   │                                 # - PROVIDER_ID, PROVIDER_LABEL
│   │                                 # - SCOPES, AUDIENCE
│   │
│   ├── token/
│   │   ├── manager.ts                # TokenManager (~80 lines)
│   │   │                             # - Memory cache for access token (fast reads)
│   │   │                             # - Keychain storage for refresh token (persistence)
│   │   │                             # - Token refresh logic (50 min before expiry)
│   │   │
│   │   └── storage.ts                # TokenStorage interface
│   │                                 # - getToken(), setToken(), clearToken()
│   │
│   ├── config/
│   │   ├── manager.ts                # McpConfigManager (~50 lines)
│   │   │                             # - Generates/updates ~/.vscode/mcp.json
│   │   │                             # - Static URLs only (no tokens in config)
│   │   │
│   │   └── types.ts                  # McpConfig interface
│   │                                 # - Server URLs, environments
│   │
│   ├── ui/
│   │   ├── statusBar.ts              # StatusBarItem (~30 lines)
│   │   │                             # - Shows auth status: signed in/out
│   │   │                             # - Click to sign in/out
│   │   │
│   │   └── commands.ts               # VS Code command handlers
│   │                                 # - mcp-auth.signIn, mcp-auth.signOut
│   │                                 # - mcp-auth.showStatus
│   │
│   └── update/
│       └── checker.ts                # Auto-update checker (~40 lines)
│                                     # - Checks Artifactory for new version
│                                     # - Shows notification if update available
│
├── test/
│   ├── suite/
│   │   ├── extension.test.ts
│   │   ├── auth.test.ts
│   │   └── token.test.ts
│   └── runTest.ts
│
├── .vscode/
│   ├── launch.json                   # Extension debugging config
│   └── tasks.json                    # Build tasks
│
├── package.json                      # Extension manifest
├── tsconfig.json
├── .vscodeignore
├── CHANGELOG.md
├── CONTRIBUTING.md                   # How to contribute
├── LICENSE
└── README.md
```

### package.json — Extension Manifest

```json
{
  "name": "mcp-auth-extension",
  "displayName": "MCP Authentication",
  "description": "Centralized authentication for MCP servers",
  "version": "1.0.0",
  "publisher": "your-org",
  "engines": {
    "vscode": "^1.85.0"
  },
  "categories": ["Other"],
  "activationEvents": [
    "onStartupFinished"
  ],
  "main": "./out/extension.js",
  "contributes": {
    "authentication": [
      {
        "id": "mcp-auth",
        "label": "MCP Platform"
      }
    ],
    "commands": [
      {
        "command": "mcp-auth.signIn",
        "title": "MCP: Sign In"
      },
      {
        "command": "mcp-auth.signOut",
        "title": "MCP: Sign Out"
      }
    ]
  },
  "scripts": {
    "vscode:prepublish": "npm run compile",
    "compile": "tsc -p ./",
    "watch": "tsc -watch -p ./",
    "lint": "eslint src --ext ts",
    "test": "vscode-test"
  },
  "devDependencies": {
    "@types/vscode": "^1.85.0",
    "@types/node": "^20.0.0",
    "typescript": "^5.3.0",
    "eslint": "^8.56.0",
    "@vscode/test-electron": "^2.3.0"
  }
}
```

### Core Implementation Pattern

```typescript
// src/auth/provider.ts (~150 lines)
import * as vscode from 'vscode';
import { TokenManager } from '../token/manager';

const PROVIDER_ID = 'mcp-auth';
const PROVIDER_LABEL = 'MCP Platform';

// PingFederate OAuth 2.0 configuration
const AUTH_CONFIG = {
  authorizationEndpoint: 'https://sso.company.com/as/authorization.oauth2',
  tokenEndpoint: 'https://sso.company.com/as/token.oauth2',
  clientId: 'vscode-mcp-extension',
  scopes: ['openid', 'profile', 'email', 'mcp:access'],
  audience: 'mcp.company.com'
};

export class McpAuthProvider implements vscode.AuthenticationProvider {
  private _sessionChangeEmitter = new vscode.EventEmitter<vscode.AuthenticationProviderAuthenticationSessionsChangeEvent>();
  private _tokenManager: TokenManager;
  private _session: vscode.AuthenticationSession | undefined;

  constructor(private readonly context: vscode.ExtensionContext) {
    this._tokenManager = new TokenManager(context);
  }

  get onDidChangeSessions() {
    return this._sessionChangeEmitter.event;
  }

  async getSessions(): Promise<readonly vscode.AuthenticationSession[]> {
    // Try memory first, then keychain
    const session = await this._tokenManager.getSession();
    return session ? [session] : [];
  }

  async createSession(scopes: readonly string[]): Promise<vscode.AuthenticationSession> {
    // OAuth 2.0 PKCE flow
    const verifier = this.generateCodeVerifier();
    const challenge = await this.generateCodeChallenge(verifier);
    
    // Open browser for sign-in
    const callbackUri = await vscode.env.asExternalUri(
      vscode.Uri.parse(`${vscode.env.uriScheme}://your-org.mcp-auth-extension/callback`)
    );
    
    const authUrl = this.buildAuthUrl(challenge, callbackUri.toString());
    await vscode.env.openExternal(vscode.Uri.parse(authUrl));
    
    // Handle callback (simplified)
    const code = await this.waitForCallback();
    const tokens = await this.exchangeCodeForTokens(code, verifier);
    
    // Store tokens
    await this._tokenManager.setTokens(tokens);
    
    // Create session
    this._session = {
      id: crypto.randomUUID(),
      accessToken: tokens.accessToken,
      account: { id: tokens.email, label: tokens.email },
      scopes: [...scopes]
    };
    
    this._sessionChangeEmitter.fire({ added: [this._session], removed: [], changed: [] });
    return this._session;
  }

  async removeSession(sessionId: string): Promise<void> {
    await this._tokenManager.clearTokens();
    const removed = this._session;
    this._session = undefined;
    if (removed) {
      this._sessionChangeEmitter.fire({ added: [], removed: [removed], changed: [] });
    }
  }

  // Helper methods for PKCE...
  private generateCodeVerifier(): string { /* ... */ }
  private async generateCodeChallenge(verifier: string): Promise<string> { /* ... */ }
  private buildAuthUrl(challenge: string, redirectUri: string): string { /* ... */ }
  private async waitForCallback(): Promise<string> { /* ... */ }
  private async exchangeCodeForTokens(code: string, verifier: string): Promise<Tokens> { /* ... */ }
}
```

```typescript
// src/token/manager.ts (~80 lines)
import * as vscode from 'vscode';

const ACCESS_TOKEN_KEY = 'mcp-auth.accessToken';
const REFRESH_TOKEN_KEY = 'mcp-auth.refreshToken';
const REFRESH_THRESHOLD_MS = 50 * 60 * 1000; // Refresh 50 min before expiry

export class TokenManager {
  // Memory cache for fast access (VS Code session only)
  private _accessToken: string | undefined;
  private _tokenExpiry: number | undefined;

  constructor(private readonly context: vscode.ExtensionContext) {
    // Restore from keychain on startup
    this.initialize();
  }

  private async initialize(): Promise<void> {
    const refreshToken = await this.context.secrets.get(REFRESH_TOKEN_KEY);
    if (refreshToken) {
      await this.refreshAccessToken(refreshToken);
    }
  }

  async getAccessToken(): Promise<string | undefined> {
    // Fast path: return from memory
    if (this._accessToken && this._tokenExpiry && Date.now() < this._tokenExpiry - REFRESH_THRESHOLD_MS) {
      return this._accessToken;
    }
    
    // Refresh if needed
    const refreshToken = await this.context.secrets.get(REFRESH_TOKEN_KEY);
    if (refreshToken) {
      return this.refreshAccessToken(refreshToken);
    }
    
    return undefined;
  }

  async setTokens(tokens: { accessToken: string; refreshToken: string; expiresIn: number }): Promise<void> {
    // Access token in memory (fast)
    this._accessToken = tokens.accessToken;
    this._tokenExpiry = Date.now() + tokens.expiresIn * 1000;
    
    // Refresh token in keychain (persistent)
    await this.context.secrets.store(REFRESH_TOKEN_KEY, tokens.refreshToken);
  }

  async clearTokens(): Promise<void> {
    this._accessToken = undefined;
    this._tokenExpiry = undefined;
    await this.context.secrets.delete(REFRESH_TOKEN_KEY);
  }

  private async refreshAccessToken(refreshToken: string): Promise<string | undefined> {
    // Call token endpoint with refresh_token grant
    // Update memory cache and return new access token
  }
}
```

### CI/CD Workflow

```yaml
# .github/workflows/ci.yml
name: CI

on:
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Lint
        run: npm run lint
      
      - name: Compile
        run: npm run compile
      
      - name: Run tests
        run: npm test
```

```yaml
# .github/workflows/publish.yml
name: Publish Extension

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Package extension
        run: npx vsce package
      
      - name: Upload to Artifactory
        run: |
          curl -u ${{ secrets.ARTIFACTORY_USER }}:${{ secrets.ARTIFACTORY_TOKEN }} \
            -T mcp-auth-extension-*.vsix \
            "https://artifactory.company.com/vscode-extensions/mcp-auth-extension-${{ github.ref_name }}.vsix"
      
      - name: Update latest manifest
        run: |
          VERSION=${GITHUB_REF_NAME#v}
          echo '{
            "version": "'$VERSION'",
            "vsixUrl": "https://artifactory.company.com/vscode-extensions/mcp-auth-extension-'${{ github.ref_name }}'.vsix",
            "releaseNotes": "https://github.com/your-org/mcp-auth-extension/releases/tag/'${{ github.ref_name }}'"
          }' > latest.json
          curl -u ${{ secrets.ARTIFACTORY_USER }}:${{ secrets.ARTIFACTORY_TOKEN }} \
            -T latest.json \
            "https://artifactory.company.com/vscode-extensions/mcp-auth-extension-latest.json"
```

### Extension Installation (Internal Distribution)

> **Note:** The extension is distributed internally via Artifactory, NOT the public VS Code Marketplace.

**First-time installation:**
```bash
# Option 1: Command line (recommended for onboarding scripts)
code --install-extension "https://artifactory.company.com/vscode-extensions/mcp-auth-extension-v1.0.0.vsix"

# Option 2: Download and install manually
curl -O https://artifactory.company.com/vscode-extensions/mcp-auth-extension-v1.0.0.vsix
code --install-extension mcp-auth-extension-v1.0.0.vsix

# Option 3: Via VS Code UI
# Extensions → ... → Install from VSIX... → paste URL or select downloaded file
```

**Auto-update notification (built into extension):**
```typescript
// src/update/checker.ts
const LATEST_MANIFEST_URL = 'https://artifactory.company.com/vscode-extensions/mcp-auth-extension-latest.json';

export async function checkForUpdates(context: vscode.ExtensionContext): Promise<void> {
  const currentVersion = context.extension.packageJSON.version;
  
  try {
    const response = await fetch(LATEST_MANIFEST_URL);
    const latest = await response.json();
    
    if (semver.gt(latest.version, currentVersion)) {
      const choice = await vscode.window.showInformationMessage(
        `MCP Auth Extension ${latest.version} is available (current: ${currentVersion})`,
        'Install Now',
        'View Release Notes'
      );
      
      if (choice === 'Install Now') {
        await vscode.commands.executeCommand(
          'workbench.extensions.installExtension',
          vscode.Uri.parse(latest.vsixUrl)
        );
      } else if (choice === 'View Release Notes') {
        await vscode.env.openExternal(vscode.Uri.parse(latest.releaseNotes));
      }
    }
  } catch (error) {
    // Silent fail - don't block user if update check fails
    console.warn('Failed to check for updates:', error);
  }
}
```

---

## 2.4 Documentation Architecture: AI-Ready, Traceable, Maintainable

> **Philosophy:** Documentation lives with code. AI agents (Copilot) read `.copilot/` first, then `docs/`.
> Every requirement traces to a story, every design traces to an ADR, every ADR traces to code.

### Documentation Folder Structure (All Repos)

```
your-repo/
├── .copilot/                         # ★ AI Agent Context (read FIRST by Copilot)
│   ├── instructions.md               # Project rules, constraints, DO/DON'T
│   ├── patterns.md                   # Code patterns to follow
│   └── glossary.md                   # Domain terminology
│
├── docs/                             # ★ Human + AI readable documentation
│   ├── README.md                     # Documentation index with links
│   │
│   ├── adr/                          # Architecture Decision Records
│   │   ├── README.md                 # ADR index + template
│   │   └── ADR-NNN-title.md          # Individual decisions
│   │
│   ├── design/                       # Technical Design Documents
│   │   ├── README.md
│   │   ├── *.md                      # Design documents
│   │   └── diagrams/                 # Mermaid, PlantUML, Draw.io
│   │
│   ├── requirements/                 # Requirements Specification
│   │   ├── README.md                 # Requirements traceability matrix
│   │   └── REQ-NNN-title.md          # Individual requirements
│   │
│   ├── api/                          # API Documentation
│   │   └── *.md
│   │
│   └── runbooks/                     # Operational Procedures (servers only)
│       └── *.md
│
├── CHANGELOG.md                      # Version history (Keep-a-Changelog format)
├── CONTRIBUTING.md                   # How to contribute
└── README.md                         # Project overview
```

### Traceability Matrix Pattern

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TRACEABILITY FLOW                                    │
│                                                                             │
│  REQ-001 ──────────► design/feature.md ──────────► ADR-001                 │
│      │                      │                         │                     │
│      │                      │                         │                     │
│      ▼                      ▼                         ▼                     │
│  Functional            Technical                 Decision                   │
│  Requirement           Design                    Rationale                  │
│      │                      │                         │                     │
│      │                      │                         │                     │
│      └──────────────────────┴─────────────────────────┘                     │
│                             │                                               │
│                             ▼                                               │
│                      src/module/code.py                                     │
│                      tests/test_module.py                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.4.1 `.copilot/instructions.md` Template

```markdown
# Project: {project-name}

## Overview
{One paragraph description of what this project does}

## Technology Stack
- Language: Python 3.12 / TypeScript 5.x
- Framework: FastMCP 2.0 / VS Code Extension API
- Key Dependencies: {list critical dependencies}

## Architecture Context
- This is a {library/server/extension} in the MCP Platform
- It depends on: {upstream dependencies}
- It is used by: {downstream consumers}
- See: docs/design/README.md for architecture diagrams

## Coding Standards

### DO
- Use type hints on all functions
- Write docstrings for public functions
- Follow existing patterns in the codebase
- Reference ADRs when making architectural changes

### DON'T
- Don't add new dependencies without an ADR
- Don't bypass the Kong gateway for auth
- Don't log sensitive data (tokens, passwords)
- Don't use print() - use structured logging

## Key Patterns
See: .copilot/patterns.md

## Domain Glossary
See: .copilot/glossary.md

## Related Documentation
- Requirements: docs/requirements/README.md
- Design: docs/design/README.md
- ADRs: docs/adr/README.md
```

---

### 2.4.2 `docs/adr/README.md` Template (ADR Index)

```markdown
# Architecture Decision Records

ADRs capture significant architectural decisions along with their context and consequences.

## Index

| ID | Title | Status | Date | Supersedes |
|----|-------|--------|------|------------|
| [ADR-001](ADR-001-use-pydantic-settings.md) | Use Pydantic for Settings | Accepted | 2026-01-15 | - |
| [ADR-002](ADR-002-structured-json-logging.md) | Structured JSON Logging | Accepted | 2026-01-20 | - |
| [ADR-003](ADR-003-timescaledb-for-audit.md) | TimescaleDB for Audit | Accepted | 2026-02-01 | - |

## ADR Template

Use this template for new ADRs:

\`\`\`markdown
# ADR-NNN: {Title}

## Status
{Proposed | Accepted | Deprecated | Superseded by ADR-XXX}

## Context
{What is the issue we're addressing? What forces are at play?}

## Decision
{What is the decision we've made?}

## Consequences

### Positive
- {Benefit 1}
- {Benefit 2}

### Negative
- {Tradeoff 1}
- {Tradeoff 2}

### Risks
- {Risk and mitigation}

## References
- REQ-XXX: {Link to requirement this addresses}
- {External links, RFCs, etc.}
\`\`\`

## Lifecycle
1. **Proposed** → Create PR with new ADR
2. **Accepted** → Merged after team review
3. **Deprecated** → No longer relevant (keep for history)
4. **Superseded** → Replaced by newer ADR (link to new one)
```

---

### 2.4.3 `docs/requirements/README.md` Template

```markdown
# Requirements Specification

## Traceability Matrix

| ID | Requirement | Design | ADR | Status |
|----|-------------|--------|-----|--------|
| [REQ-001](REQ-001-user-context.md) | Extract user from Kong headers | [context-extraction.md](../design/context-extraction.md) | ADR-001 | Implemented |
| [REQ-002](REQ-002-audit-logging.md) | Log all tool invocations | [audit-pipeline.md](../design/audit-pipeline.md) | ADR-003 | Implemented |
| [REQ-003](REQ-003-error-handling.md) | Standardized error responses | [error-handling.md](../design/error-handling.md) | - | In Progress |

## Requirement Template

\`\`\`markdown
# REQ-NNN: {Title}

## Description
{What capability is needed?}

## Acceptance Criteria
- [ ] {Criterion 1}
- [ ] {Criterion 2}
- [ ] {Criterion 3}

## Technical Notes
{Implementation guidance, constraints}

## Dependencies
- REQ-YYY: {Dependency description}

## Status
{Draft | Approved | Implemented | Verified}
\`\`\`
```

---

### 2.4.4 Why This Structure Works for AI Agents

| Feature | Benefit for Copilot/AI |
|---------|------------------------|
| **`.copilot/instructions.md`** | First file AI reads — sets context, rules, constraints |
| **`.copilot/patterns.md`** | Shows code patterns to emulate, anti-patterns to avoid |
| **`.copilot/glossary.md`** | Defines domain terms so AI uses correct terminology |
| **Numbered IDs (ADR-001, REQ-001)** | AI can reference and link documents precisely |
| **Traceability matrix** | AI understands dependency chain: Req → Design → ADR → Code |
| **Consistent templates** | AI learns structure, can generate new docs in same format |
| **`docs/README.md` index** | AI has entry point to navigate documentation |

---

### 2.4.5 Blank Templates (Copy & Create)

> **Usage:** Copy these templates to create new documentation files. Replace `{placeholders}` with actual content.

#### `.copilot/instructions.md` — Blank

```markdown
# Project: {project-name}

## Overview
{One paragraph describing what this project does and its role in the platform}

## Technology Stack
- Language: {Python 3.12 | TypeScript 5.x | ...}
- Framework: {FastMCP | VS Code Extension API | ...}
- Key Dependencies:
  - {dependency-1}: {purpose}
  - {dependency-2}: {purpose}

## Architecture Context
- This is a: {library | server | extension | tool}
- Upstream dependencies: {what this project depends on}
- Downstream consumers: {what depends on this project}
- Architecture docs: docs/design/README.md

## Coding Standards

### DO
- {Rule 1: e.g., Use type hints on all functions}
- {Rule 2: e.g., Write docstrings for public functions}
- {Rule 3: e.g., Follow existing patterns in codebase}
- {Rule 4: e.g., Reference ADRs when making architectural changes}

### DON'T
- {Anti-pattern 1: e.g., Don't add dependencies without an ADR}
- {Anti-pattern 2: e.g., Don't bypass security patterns}
- {Anti-pattern 3: e.g., Don't log sensitive data}
- {Anti-pattern 4: e.g., Don't use print() - use structured logging}

## Key Files
- Entry point: {src/main.py | src/extension.ts | ...}
- Configuration: {src/config.py | package.json | ...}
- Main logic: {src/module/... | src/auth/... | ...}

## Related Documentation
- Requirements: docs/requirements/README.md
- Design: docs/design/README.md
- ADRs: docs/adr/README.md
```

#### `.copilot/patterns.md` — Blank

```markdown
# Code Patterns

## Patterns to Follow

### Pattern 1: {Pattern Name}
**When to use:** {Situation}
**Example:**
\`\`\`{language}
{code example}
\`\`\`

### Pattern 2: {Pattern Name}
**When to use:** {Situation}
**Example:**
\`\`\`{language}
{code example}
\`\`\`

## Anti-Patterns to Avoid

### Anti-Pattern 1: {Name}
**Why it's bad:** {Explanation}
**Instead do:** {Correct approach}

### Anti-Pattern 2: {Name}
**Why it's bad:** {Explanation}
**Instead do:** {Correct approach}

## Error Handling Pattern
\`\`\`{language}
{standard error handling code}
\`\`\`

## Logging Pattern
\`\`\`{language}
{standard logging code}
\`\`\`

## Testing Pattern
\`\`\`{language}
{standard test structure}
\`\`\`
```

#### `.copilot/glossary.md` — Blank

```markdown
# Domain Glossary

## Core Concepts

| Term | Definition | Example |
|------|------------|---------|
| {Term 1} | {Definition} | {Example usage} |
| {Term 2} | {Definition} | {Example usage} |
| {Term 3} | {Definition} | {Example usage} |

## Abbreviations

| Abbrev | Full Form | Context |
|--------|-----------|---------|
| {ABC} | {Full form} | {When used} |
| {XYZ} | {Full form} | {When used} |

## Domain-Specific Terms

### {Category 1}
- **{Term}**: {Definition}
- **{Term}**: {Definition}

### {Category 2}
- **{Term}**: {Definition}
- **{Term}**: {Definition}

## Related Standards/Protocols
- {Standard 1}: {Brief description, link}
- {Standard 2}: {Brief description, link}
```

#### `docs/README.md` — Blank (Documentation Index)

```markdown
# {Project Name} Documentation

## Quick Links
- [Getting Started](#getting-started)
- [Architecture Decisions](adr/README.md)
- [Technical Design](design/README.md)
- [Requirements](requirements/README.md)
- [API Reference](api/README.md)

## Getting Started
{Brief description of how to get started with this project}

## Documentation Structure

| Folder | Purpose | Start Here |
|--------|---------|------------|
| `adr/` | Architecture decisions | [ADR Index](adr/README.md) |
| `design/` | Technical designs | [Design Overview](design/README.md) |
| `requirements/` | Requirements & stories | [Requirements Matrix](requirements/README.md) |
| `api/` | API documentation | [API Reference](api/README.md) |
| `runbooks/` | Operational procedures | [Runbook Index](runbooks/README.md) |

## Key Documents
1. [{Document 1 Title}](path/to/doc.md) — {One-line description}
2. [{Document 2 Title}](path/to/doc.md) — {One-line description}
3. [{Document 3 Title}](path/to/doc.md) — {One-line description}

## Contributing
See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.
```

#### `docs/adr/ADR-NNN-{title}.md` — Blank

```markdown
# ADR-{NNN}: {Title}

**Date:** {YYYY-MM-DD}  
**Status:** {Proposed | Accepted | Deprecated | Superseded by ADR-XXX}  
**Deciders:** {Names or roles}

## Context
{What is the issue we're addressing?}
{What forces are at play (technical, business, team)?}
{What constraints exist?}

## Decision
{What is the decision?}
{State it clearly and concisely.}

## Options Considered

### Option 1: {Name}
- **Pros:** {List benefits}
- **Cons:** {List drawbacks}

### Option 2: {Name}
- **Pros:** {List benefits}
- **Cons:** {List drawbacks}

### Option 3: {Name}
- **Pros:** {List benefits}
- **Cons:** {List drawbacks}

## Consequences

### Positive
- {Benefit 1}
- {Benefit 2}

### Negative
- {Tradeoff 1}
- {Tradeoff 2}

### Risks
- {Risk 1}: {Mitigation}
- {Risk 2}: {Mitigation}

## References
- REQ-{NNN}: {Link to requirement}
- {External link, RFC, documentation}
- {Related ADR if any}
```

#### `docs/design/{feature}.md` — Blank

```markdown
# {Feature/Component} Design

**Author:** {Name}  
**Date:** {YYYY-MM-DD}  
**Status:** {Draft | Review | Approved}  
**Reviewers:** {Names}

## Overview
{What is this design about? 2-3 sentences.}

## Goals
- {Goal 1}
- {Goal 2}
- {Goal 3}

## Non-Goals
- {What this design explicitly does NOT address}

## Requirements
- [REQ-{NNN}](../requirements/REQ-{NNN}.md): {Title}
- [REQ-{NNN}](../requirements/REQ-{NNN}.md): {Title}

## Design

### Architecture
{Description of the architecture}

\`\`\`
{ASCII diagram or reference to diagram file}
\`\`\`

### Component Diagram
![{Diagram Name}](diagrams/{filename}.svg)

### Data Flow
1. {Step 1}
2. {Step 2}
3. {Step 3}

### API/Interface
\`\`\`{language}
{API definition or interface}
\`\`\`

### Data Model
\`\`\`{language}
{Data structures}
\`\`\`

## Implementation Notes
- {Note 1}
- {Note 2}

## Security Considerations
- {Security aspect 1}
- {Security aspect 2}

## Testing Strategy
- Unit tests: {Approach}
- Integration tests: {Approach}
- E2E tests: {Approach}

## Open Questions
- [ ] {Question 1}
- [ ] {Question 2}

## References
- ADR-{NNN}: {Related decision}
- {External documentation}
```

#### `docs/requirements/REQ-NNN-{title}.md` — Blank

```markdown
# REQ-{NNN}: {Title}

**Priority:** {Must Have | Should Have | Nice to Have}  
**Status:** {Draft | Approved | Implemented | Verified}  
**Owner:** {Name or team}

## Description
{What capability is needed? Be specific.}

## Business Value
{Why is this needed? What problem does it solve?}

## Acceptance Criteria
- [ ] {Criterion 1: Given X, When Y, Then Z}
- [ ] {Criterion 2: Given X, When Y, Then Z}
- [ ] {Criterion 3: Given X, When Y, Then Z}
- [ ] {Criterion 4: Given X, When Y, Then Z}

## Technical Notes
{Implementation guidance, constraints, considerations}

## Dependencies
- REQ-{NNN}: {How this requirement depends on another}
- {External dependency}: {Description}

## Out of Scope
- {What is explicitly NOT part of this requirement}

## Design
- [Design Document](../design/{feature}.md)

## ADR
- [ADR-{NNN}](../adr/ADR-{NNN}.md): {Related architectural decision}

## Verification
- Test: `test_{feature}.py`
- Manual verification: {Steps}
```

#### `docs/runbooks/{procedure}.md` — Blank

```markdown
# Runbook: {Procedure Name}

**Last Updated:** {YYYY-MM-DD}  
**Author:** {Name}  
**Review Frequency:** {Quarterly | Annually | As needed}

## Purpose
{What does this runbook help you do?}

## When to Use
- {Scenario 1}
- {Scenario 2}

## Prerequisites
- [ ] {Prerequisite 1: Access, tools, permissions}
- [ ] {Prerequisite 2}
- [ ] {Prerequisite 3}

## Procedure

### Step 1: {Title}
{Description}
\`\`\`bash
{command}
\`\`\`
**Expected output:** {What you should see}

### Step 2: {Title}
{Description}
\`\`\`bash
{command}
\`\`\`
**Expected output:** {What you should see}

### Step 3: {Title}
{Description}

## Verification
{How to verify the procedure worked}
\`\`\`bash
{verification command}
\`\`\`

## Rollback
{How to undo if something goes wrong}
\`\`\`bash
{rollback command}
\`\`\`

## Troubleshooting

### Issue: {Problem description}
**Symptom:** {What you see}  
**Cause:** {Why it happens}  
**Solution:**
\`\`\`bash
{fix command}
\`\`\`

### Issue: {Problem description}
**Symptom:** {What you see}  
**Cause:** {Why it happens}  
**Solution:** {Steps to resolve}

## Contacts
- Primary: {Name} - {contact}
- Escalation: {Name} - {contact}

## References
- {Link to related documentation}
- {Link to monitoring dashboard}
```

#### `docs/api/{component}-api.md` — Blank

```markdown
# {Component} API Reference

**Version:** {X.Y.Z}  
**Last Updated:** {YYYY-MM-DD}

## Overview
{What this API does, who should use it}

## Authentication
{How to authenticate, required headers}

## Base URL
\`\`\`
{base-url}
\`\`\`

## Endpoints / Tools / Methods

### {Method/Tool 1}: {name}

**Description:** {What this does}

**Request:**
\`\`\`{language}
{request format}
\`\`\`

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| {param1} | {type} | {Yes/No} | {description} |
| {param2} | {type} | {Yes/No} | {description} |

**Response:**
\`\`\`{language}
{response format}
\`\`\`

**Example:**
\`\`\`{language}
{example request and response}
\`\`\`

**Errors:**
| Code | Message | Description |
|------|---------|-------------|
| {code} | {message} | {when this occurs} |

---

### {Method/Tool 2}: {name}

{Same structure as above}

---

## Error Codes

| Code | Message | Description | Resolution |
|------|---------|-------------|------------|
| {code} | {message} | {description} | {how to fix} |

## Rate Limits
{Rate limiting rules if applicable}

## Changelog
| Version | Date | Changes |
|---------|------|---------|
| {X.Y.Z} | {YYYY-MM-DD} | {What changed} |
```

#### `CHANGELOG.md` — Blank

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- {New feature}

### Changed
- {Change in existing functionality}

### Deprecated
- {Soon-to-be removed feature}

### Removed
- {Removed feature}

### Fixed
- {Bug fix}

### Security
- {Security fix}

## [{X.Y.Z}] - {YYYY-MM-DD}

### Added
- {Feature 1}
- {Feature 2}

### Changed
- {Change 1}

### Fixed
- {Bug fix 1}

## [{X.Y.Z}] - {YYYY-MM-DD}

### Added
- Initial release
- {Feature list}

[Unreleased]: https://github.com/{org}/{repo}/compare/v{X.Y.Z}...HEAD
[{X.Y.Z}]: https://github.com/{org}/{repo}/compare/v{prev}...v{X.Y.Z}
```

#### `CONTRIBUTING.md` — Blank

```markdown
# Contributing to {Project Name}

## Getting Started

### Prerequisites
- {Tool 1}: {version}
- {Tool 2}: {version}

### Setup
\`\`\`bash
{setup commands}
\`\`\`

## Development Workflow

### 1. Create a Branch
\`\`\`bash
git checkout -b {type}/{description}
# Types: feature/, bugfix/, docs/, refactor/
\`\`\`

### 2. Make Changes
- Follow patterns in `.copilot/patterns.md`
- Update documentation as needed
- Add tests for new functionality

### 3. Test
\`\`\`bash
{test command}
\`\`\`

### 4. Commit
Follow [Conventional Commits](https://www.conventionalcommits.org/):
\`\`\`
{type}({scope}): {description}

{body}

{footer}
\`\`\`

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

### 5. Submit PR
- Fill out PR template
- Link related issues/stories
- Request review

## Code Standards
See `.copilot/instructions.md` for detailed guidelines.

### Quick Rules
- {Rule 1}
- {Rule 2}
- {Rule 3}

## Documentation
- Update `docs/` for user-facing changes
- Add ADR for architectural decisions
- Update API docs for interface changes

## Review Process
1. Automated checks must pass
2. At least {N} reviewer approval(s)
3. No unresolved comments

## Release Process
{How releases are done}

## Questions?
- Slack: {channel}
- Email: {email}
```

---

### Directory Naming Convention

| Directory | Purpose | Audience |
|-----------|---------|----------|
| `.copilot/` | AI agent context | Copilot, Claude, etc. |
| `docs/adr/` | Architecture decisions | Architects, Tech Leads |
| `docs/design/` | Technical design | Developers, Architects |
| `docs/requirements/` | What to build | Product, Developers |
| `docs/api/` | API reference | Developers, Integrators |
| `docs/runbooks/` | Operations | DevOps, SRE |
| `docs/user-guide/` | End-user help | End Users |

---

## 3. Shared Library Design

> **Note:** Authentication is handled by **Kong Gateway** (JWT validation) and **VS Code Auth Extension** (OAuth 2.0 PKCE).
> MCP servers do NOT validate tokens — they receive pre-validated user context via headers.
> See [REMOTE_MCP_ARCHITECTURE_GUIDE.md](REMOTE_MCP_ARCHITECTURE_GUIDE.md) for the full auth flow.

### 3.1 mcp-common Package — User Context Extraction

```python
# mcp-common/src/mcp_common/context/user.py
from dataclasses import dataclass
from typing import Optional
from fastapi import Request

@dataclass
class UserContext:
    """User context extracted from Kong-injected headers.
    
    Kong validates JWT and strips it before forwarding to MCP servers.
    User claims are passed as headers (X-User-Email, X-User-Teams).
    """
    email: str
    teams: list[str]
    correlation_id: Optional[str] = None

def get_user_context(request: Request) -> UserContext:
    """Extract user context from Kong-injected headers.
    
    Headers set by Kong RequestTransformer plugin:
    - X-User-Email: User's email from JWT 'email' claim
    - X-User-Teams: Comma-separated list from JWT 'groups' claim
    - X-Correlation-ID: Request tracing ID
    """
    email = request.headers.get("X-User-Email")
    if not email:
        raise ValueError("Missing X-User-Email header - request must go through Kong")
    
    teams_header = request.headers.get("X-User-Teams", "")
    teams = [t.strip() for t in teams_header.split(",") if t.strip()]
    
    return UserContext(
        email=email,
        teams=teams,
        correlation_id=request.headers.get("X-Correlation-ID"),
    )
```

### 3.1.1 Context Middleware for FastMCP

```python
# mcp-common/src/mcp_common/context/middleware.py
from contextvars import ContextVar
from mcp_common.context.user import UserContext, get_user_context

# Context variable for current request's user
_current_user: ContextVar[UserContext] = ContextVar("current_user")

def get_current_user() -> UserContext:
    """Get the current user from context (for use in tool handlers)."""
    return _current_user.get()

def setup_user_context(mcp):
    """Middleware to extract user context from Kong headers."""
    
    @mcp.middleware
    async def inject_user_context(request, call_next):
        user = get_user_context(request)
        token = _current_user.set(user)
        try:
            return await call_next(request)
        finally:
            _current_user.reset(token)
```

### 3.2 Base Settings Pattern

```python
# mcp-common/src/mcp_common/config/settings.py
from pydantic_settings import BaseSettings
from functools import lru_cache

class BaseServerSettings(BaseSettings):
    """Base configuration inherited by all MCP servers.
    
    Note: No OAuth settings here — Kong handles JWT validation.
    MCP servers receive pre-validated user context via headers.
    """
    
    # Server
    server_name: str
    server_version: str = "1.0.0"
    environment: str = "dev"
    
    # Observability
    log_level: str = "INFO"
    metrics_port: int = 9090
    
    # Transport
    host: str = "0.0.0.0"
    port: int = 8000
    
    # Audit (TimescaleDB)
    audit_db_url: str | None = None
    
    class Config:
        env_prefix = "MCP_"
        env_file = ".env"
```

---

## 4. Server Implementation Pattern

### 4.1 Topic Management MCP Server Example

```python
# topic-management-mcp/src/topic_management_mcp/server.py
from fastmcp import FastMCP
from mcp_common.context.middleware import setup_user_context
from mcp_common.logging.structured import setup_logging
from mcp_common.health.probes import setup_health_checks

from topic_management_mcp.config import TopicManagementSettings
from topic_management_mcp.tools import topics, acls, clusters
from topic_management_mcp.clients.admin import KafkaAdminClient

# Initialize
settings = TopicManagementSettings()
mcp = FastMCP(
    name="topic-management-mcp",
    version=settings.server_version,
    stateless_http=True,
    json_response=True,
)

# Setup shared infrastructure
# NOTE: No auth setup here — Kong validates JWT and injects X-User-* headers
setup_logging(mcp, settings)
setup_user_context(mcp)  # Extract user from Kong headers
setup_health_checks(mcp)

# Lifespan for client management
@mcp.lifespan
async def lifespan():
    admin = await KafkaAdminClient.create(settings.bootstrap_servers)
    try:
        yield {"kafka_admin": admin}
    finally:
        await admin.close()

# Register tools from modules
mcp.include_router(topics.router)
mcp.include_router(acls.router)
mcp.include_router(clusters.router)

if __name__ == "__main__":
    mcp.run(transport="streamable-http")
```

### 4.2 Tool Module Pattern

```python
# topic-management-mcp/src/topic_management_mcp/tools/topics.py
from fastmcp import Context
from fastmcp.routing import Router
from pydantic import BaseModel, Field

router = Router(prefix="topic")

class CreateTopicInput(BaseModel):
    """Input schema for topic creation."""
    topic_name: str = Field(..., description="Name of the topic to create")
    partitions: int = Field(default=3, ge=1, le=100)
    replication_factor: int = Field(default=3, ge=1, le=5)

class TopicResult(BaseModel):
    """Result of topic operation."""
    topic: str
    partitions: int
    replication_factor: int
    created: bool

@router.tool()
async def create_topic(
    input: CreateTopicInput,
    ctx: Context,
) -> TopicResult:
    """
    Create a new Kafka topic.
    
    This tool creates a topic with the specified configuration.
    Use list_topics first to verify the topic doesn't exist.
    """
    admin = ctx.request_context.lifespan_context["kafka_admin"]
    
    result = await admin.create_topic(
        topic_name=input.topic_name,
        partitions=input.partitions,
        replication_factor=input.replication_factor,
    )
    
    return TopicResult(**result)

@router.tool()
async def list_topics(ctx: Context) -> list[str]:
    """List all Kafka topics in the cluster."""
    admin = ctx.request_context.lifespan_context["kafka_admin"]
    return await admin.list_topics()

@router.tool()
async def delete_topic(topic_name: str, ctx: Context) -> dict:
    """Delete a Kafka topic. Use with caution."""
    admin = ctx.request_context.lifespan_context["kafka_admin"]
    return await admin.delete_topic(topic_name)
```

---

## 5. Dependency Management

### 5.1 Private PyPI Setup

Configure Artifactory or GitLab Package Registry to host `mcp-common` and `mcp-testing`.

```bash
# Configure uv to use private PyPI
export UV_EXTRA_INDEX_URL="https://artifactory.company.com/api/pypi/pypi-local/simple"
export UV_INDEX_USERNAME="$ARTIFACTORY_USER"
export UV_INDEX_PASSWORD="$ARTIFACTORY_TOKEN"
```

### 5.2 Server pyproject.toml

```toml
# topic-management-mcp/pyproject.toml
[project]
name = "topic-management-mcp"
version = "2.1.0"
requires-python = ">=3.12"
dependencies = [
    "fastmcp>=2.0.0",
    "confluent-kafka>=2.3.0",
    "mcp-common==1.2.0",    # Pinned from private PyPI
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "pytest-asyncio>=0.24",
    "mcp-testing==1.0.0",   # Test utilities from private PyPI
    "ruff>=0.4.0",
]

[project.scripts]
topic-management-mcp = "topic_management_mcp.server:main"

[tool.uv]
extra-index-url = "https://nexus.company.com/repository/pypi-internal/simple"

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]
```

### 5.3 Shared Library pyproject.toml

```toml
# mcp-common/pyproject.toml
[project]
name = "mcp-common"
version = "1.2.0"           # Semver: MAJOR.MINOR.PATCH
requires-python = ">=3.12"
description = "Shared infrastructure for MCP servers"

dependencies = [
    "fastmcp>=2.0.0",
    "pydantic-settings>=2.0",
    "structlog>=24.0",
    "asyncpg>=0.29.0",      # For TimescaleDB audit writer
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "pytest-asyncio>=0.24",
    "ruff>=0.4.0",
]

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"
```

---

## 6. Dockerfile Template

```dockerfile
# topic-management-mcp/Dockerfile
FROM python:3.12-slim AS base

WORKDIR /app
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    UV_SYSTEM_PYTHON=1

# Install uv (pinned version for reproducibility)
COPY --from=ghcr.io/astral-sh/uv:0.4.30 /uv /bin/uv

# Build stage
FROM base AS builder

# Configure private PyPI (use build args for CI)
ARG ARTIFACTORY_URL
ARG ARTIFACTORY_USER
ARG ARTIFACTORY_TOKEN
ENV UV_EXTRA_INDEX_URL=${ARTIFACTORY_URL}/simple
ENV UV_INDEX_USERNAME=${ARTIFACTORY_USER}
ENV UV_INDEX_PASSWORD=${ARTIFACTORY_TOKEN}

# Copy project files
COPY pyproject.toml uv.lock ./
COPY src/ src/

# Install dependencies (mcp-common from private PyPI)
RUN uv sync --frozen --no-dev

# Runtime image
FROM python:3.12-slim AS runtime
WORKDIR /app

# Create non-root user for security
RUN groupadd --gid 1000 mcp && \
    useradd --uid 1000 --gid mcp --shell /bin/bash --create-home mcp

COPY --from=builder --chown=mcp:mcp /app/.venv /app/.venv
COPY --from=builder --chown=mcp:mcp /app/src /app/src

ENV PATH="/app/.venv/bin:$PATH"
ENV PYTHONPATH="/app/src"

# Switch to non-root user
USER mcp

EXPOSE 8000 9090

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import httpx; httpx.get('http://localhost:8000/health')"

CMD ["python", "-m", "topic_management_mcp.server"]
```

---

## 7. Local Development

### 7.1 Per-Server Development

```bash
# Clone the server you're working on
git clone your-org/topic-management-mcp
cd topic-management-mcp

# Install dependencies (pulls mcp-common from private PyPI)
uv sync

# Run tests
uv run pytest

# Start server locally
uv run python -m topic_management_mcp.server
```

### 7.2 Working on Shared Library + Server Together

```bash
# Clone both repos side by side
git clone your-org/mcp-common
git clone your-org/topic-management-mcp

# In topic-management-mcp, use local mcp-common for development
cd topic-management-mcp
uv add --editable ../mcp-common

# Now changes to mcp-common are reflected immediately
# When done, revert to published version:
uv remove mcp-common
uv add "mcp-common==1.2.0"
```

### 7.3 docker-compose.yml (Per-Server)

> **Note:** For local development, MCP servers are called directly without Kong.
> Pass `X-User-Email` header manually for testing. In production, Kong injects these headers.

```yaml
# topic-management-mcp/docker-compose.yml
services:
  topic-management-mcp:
    build:
      context: .
      args:
        ARTIFACTORY_URL: ${ARTIFACTORY_URL}
        ARTIFACTORY_USER: ${ARTIFACTORY_USER}
        ARTIFACTORY_TOKEN: ${ARTIFACTORY_TOKEN}
    ports:
      - "8000:8000"
      - "9090:9090"
    environment:
      - MCP_SERVER_NAME=topic-management-mcp
      - MCP_ENVIRONMENT=dev
      - MCP_BOOTSTRAP_SERVERS=kafka:9092
      - MCP_AUDIT_DB_URL=postgresql://postgres:pass@timescale:5432/audit
    depends_on:
      - kafka
      - timescale

  # Local infrastructure
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"

  timescale:
    image: timescale/timescaledb:latest-pg16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: pass
      POSTGRES_DB: audit
```

### 7.4 Development Commands

```bash
# Setup development environment (per-server)
cd topic-management-mcp
uv sync

# Run server locally
uv run python -m topic_management_mcp.server

# Run tests
uv run pytest

# Format and lint
uv run ruff format .
uv run ruff check .

# Start local dependencies
docker compose up -d
```

### 7.5 Pre-commit Configuration

All repositories use pre-commit for consistent code quality checks before commits.

#### 7.5.1 Python MCP Server `.pre-commit-config.yaml`

```yaml
# .pre-commit-config.yaml
default_language_version:
  python: python3.12

repos:
  # General file hygiene
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.6.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-yaml
      - id: check-toml
      - id: check-json
      - id: check-added-large-files
        args: ['--maxkb=500']
      - id: debug-statements
      - id: check-merge-conflict

  # Python linting and formatting (Ruff)
  - repo: https://github.com/astral-sh/ruff-pre-commit
    rev: v0.6.9
    hooks:
      - id: ruff
        args: ['--fix', '--exit-non-zero-on-fix']
      - id: ruff-format

  # Type checking (mypy)
  - repo: https://github.com/pre-commit/mirrors-mypy
    rev: v1.11.2
    hooks:
      - id: mypy
        additional_dependencies:
          - types-pyyaml
          - types-requests
        args: ['--config-file=pyproject.toml']

  # Security - detect secrets
  - repo: https://github.com/Yelp/detect-secrets
    rev: v1.5.0
    hooks:
      - id: detect-secrets
        args: ['--baseline', '.secrets.baseline']
        exclude: package-lock\.json|\.lock$

  # Dockerfile linting
  - repo: https://github.com/hadolint/hadolint
    rev: v2.12.0
    hooks:
      - id: hadolint-docker
        args: ['--ignore', 'DL3008', '--ignore', 'DL3013']

  # Conventional commits
  - repo: https://github.com/compilerla/conventional-pre-commit
    rev: v3.4.0
    hooks:
      - id: conventional-pre-commit
        stages: [commit-msg]
        args: ['--strict']

  # Config file formatting
  - repo: https://github.com/pre-commit/mirrors-prettier
    rev: v4.0.0-alpha.8
    hooks:
      - id: prettier
        types_or: [yaml, json, markdown]
        exclude: ^(\.copilot/|docs/templates/)
```

#### 7.5.2 TypeScript Extension `.pre-commit-config.yaml`

```yaml
# .pre-commit-config.yaml (for mcp-vscode-extension)
repos:
  # General file hygiene
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v4.6.0
    hooks:
      - id: trailing-whitespace
      - id: end-of-file-fixer
      - id: check-yaml
      - id: check-json
      - id: check-added-large-files
        args: ['--maxkb=500']
      - id: check-merge-conflict

  # ESLint for TypeScript
  - repo: local
    hooks:
      - id: eslint
        name: eslint
        entry: npm run lint:fix
        language: system
        types: [typescript, tsx]
        pass_filenames: false

      - id: prettier
        name: prettier
        entry: npm run format
        language: system
        types_or: [typescript, tsx, json, yaml, markdown]
        pass_filenames: false

      - id: typecheck
        name: typecheck
        entry: npm run typecheck
        language: system
        types: [typescript, tsx]
        pass_filenames: false

  # Security - detect secrets
  - repo: https://github.com/Yelp/detect-secrets
    rev: v1.5.0
    hooks:
      - id: detect-secrets
        args: ['--baseline', '.secrets.baseline']
        exclude: package-lock\.json

  # Conventional commits
  - repo: https://github.com/compilerla/conventional-pre-commit
    rev: v3.4.0
    hooks:
      - id: conventional-pre-commit
        stages: [commit-msg]
        args: ['--strict']
```

#### 7.5.3 Tool Configuration in `pyproject.toml`

```toml
# pyproject.toml - Ruff and mypy configuration

[tool.ruff]
target-version = "py312"
line-length = 120
src = ["src", "tests"]

[tool.ruff.lint]
select = [
    "E",    # pycodestyle errors
    "W",    # pycodestyle warnings
    "F",    # Pyflakes
    "I",    # isort
    "B",    # flake8-bugbear
    "C4",   # flake8-comprehensions
    "UP",   # pyupgrade
    "ARG",  # flake8-unused-arguments
    "SIM",  # flake8-simplify
    "TCH",  # flake8-type-checking
    "PTH",  # flake8-use-pathlib
    "ERA",  # eradicate (commented code)
    "PL",   # Pylint
    "RUF",  # Ruff-specific rules
]
ignore = [
    "PLR0913",  # Too many arguments
    "PLR2004",  # Magic value comparison
]

[tool.ruff.lint.isort]
known-first-party = ["mcp_common", "topic_management_mcp"]
force-single-line = false
combine-as-imports = true

[tool.ruff.lint.per-file-ignores]
"tests/**/*.py" = ["ARG001", "PLR2004"]

[tool.mypy]
python_version = "3.12"
strict = true
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true
disallow_incomplete_defs = true
check_untyped_defs = true
plugins = ["pydantic.mypy"]

[[tool.mypy.overrides]]
module = ["kafka.*", "confluent_kafka.*"]
ignore_missing_imports = true

[tool.pydantic-mypy]
init_forbid_extra = true
init_typed = true
warn_required_dynamic_aliases = true
```

#### 7.5.4 Setup Commands

```bash
# Install pre-commit
uv add --dev pre-commit

# Install hooks (run once per clone)
uv run pre-commit install
uv run pre-commit install --hook-type commit-msg

# Run all hooks manually
uv run pre-commit run --all-files

# Update hooks to latest versions
uv run pre-commit autoupdate

# Skip hooks temporarily (emergency only)
git commit --no-verify -m "emergency fix"

# Initialize secrets baseline
uv run detect-secrets scan > .secrets.baseline
```

#### 7.5.5 Pre-commit Hooks Summary

| Hook | Purpose | Stage | Exit on Failure |
|------|---------|-------|-----------------|
| `trailing-whitespace` | Remove trailing whitespace | pre-commit | Yes |
| `end-of-file-fixer` | Ensure files end with newline | pre-commit | Yes |
| `check-yaml` | Validate YAML syntax | pre-commit | Yes |
| `check-toml` | Validate TOML syntax | pre-commit | Yes |
| `check-json` | Validate JSON syntax | pre-commit | Yes |
| `ruff` | Python linting + auto-fix | pre-commit | Yes |
| `ruff-format` | Python formatting | pre-commit | Yes |
| `mypy` | Static type checking | pre-commit | Yes |
| `detect-secrets` | Prevent committing secrets | pre-commit | Yes |
| `hadolint` | Dockerfile best practices | pre-commit | Yes |
| `prettier` | Format YAML, JSON, MD | pre-commit | Yes |
| `conventional-pre-commit` | Enforce commit message format | commit-msg | Yes |
| `eslint` | TypeScript linting | pre-commit | Yes |

---

## 8. CI/CD Pipeline (Per-Server Repo)

### 8.1 CI Workflow

```yaml
# topic-management-mcp/.github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:

env:
  UV_EXTRA_INDEX_URL: ${{ secrets.ARTIFACTORY_URL }}/simple

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: astral-sh/setup-uv@v4
      - run: uv lock --check          # Ensure lockfile is up-to-date
      - run: uv sync --frozen         # Install from lockfile only
      - run: uv run ruff check .
      - run: uv run ruff format --check .

  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: astral-sh/setup-uv@v4
        with:
          enable-cache: true
      - run: uv sync --frozen         # Install from lockfile only
      - run: uv run pytest --cov=topic_management_mcp
```

### 8.2 Build & Push Workflow

```yaml
# topic-management-mcp/.github/workflows/build.yml
name: Build & Push

on:
  push:
    branches: [main]
    tags: ['v*']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          build-args: |
            ARTIFACTORY_URL=${{ secrets.ARTIFACTORY_URL }}
            ARTIFACTORY_USER=${{ secrets.ARTIFACTORY_USER }}
            ARTIFACTORY_TOKEN=${{ secrets.ARTIFACTORY_TOKEN }}
          tags: |
            ghcr.io/${{ github.repository }}:${{ github.sha }}
            ghcr.io/${{ github.repository }}:latest
```

### 8.3 Deploy Workflow

```yaml
# topic-management-mcp/.github/workflows/deploy.yml
name: Deploy to Kubernetes

on:
  workflow_dispatch:
    inputs:
      environment:
        description: 'Target environment'
        required: true
        type: choice
        options: [dev, sit, uat, prod]

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: ${{ inputs.environment }}
    steps:
      - uses: actions/checkout@v4

      - name: Configure kubectl
        uses: azure/setup-kubectl@v3

      - name: Set kubeconfig
        run: echo "${{ secrets.KUBECONFIG }}" > $HOME/.kube/config

      - name: Deploy with Helm
        run: |
          helm upgrade --install topic-management-mcp ./deploy/helm \
            --namespace mcp \
            --values ./deploy/helm/values-${{ inputs.environment }}.yaml \
            --set image.tag=${{ github.sha }} \
            --atomic \
            --wait \
            --timeout 5m

      - name: Post-deploy smoke test
        run: |
          # Wait for rollout
          kubectl rollout status deployment/topic-management-mcp -n mcp --timeout=120s
          
          # Health check
          POD=$(kubectl get pod -n mcp -l app=topic-management-mcp -o jsonpath='{.items[0].metadata.name}')
          kubectl exec -n mcp $POD -- python -c "import httpx; r = httpx.get('http://localhost:8000/health'); assert r.status_code == 200"
          echo "✅ Smoke test passed"
```

### 8.4 mcp-common Publish Workflow

```yaml
# mcp-common/.github/workflows/publish.yml
name: Publish to Private PyPI

on:
  push:
    tags: ['v*']

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Python & uv
        uses: astral-sh/setup-uv@v4
      
      - name: Build package
        run: uv build
      
      - name: Publish to Artifactory
        run: |
          uv publish \
            --repository ${{ secrets.ARTIFACTORY_URL }} \
            --username ${{ secrets.ARTIFACTORY_USER }} \
            --password ${{ secrets.ARTIFACTORY_TOKEN }}
```

---

## 9. Adding a New MCP Server

### Checklist

1. **Create new repository**: `your-org/<name>-mcp`
2. **Clone template**: Copy structure from existing server (e.g., topic-management-mcp)
3. **Update pyproject.toml**:
   - Set package name and version
   - Pin `mcp-common` version
   - Add server-specific dependencies
4. **Configure private PyPI**: Add `extra-index-url` for Artifactory
5. **Implement tools** in `src/<name>_mcp/tools/`
6. **Add CI/CD workflows**:
   - `.github/workflows/ci.yml` — Lint, test
   - `.github/workflows/build.yml` — Docker build & push
   - `.github/workflows/deploy.yml` — K8s deployment
7. **Add Helm chart**: `deploy/helm/` with environment values
8. **Configure container registry**: Setup GHCR/ECR access
9. **Document API**: Update central tool reference docs

### Quick Start: Create from Template

```bash
# Clone template repo
git clone your-org/mcp-server-template <name>-mcp
cd <name>-mcp

# Rename package
mv src/template_mcp src/<name>_mcp
sed -i 's/template-mcp/<name>-mcp/g' pyproject.toml
sed -i 's/template_mcp/<name>_mcp/g' src/**/*.py

# Install dependencies
uv sync

# Verify setup
uv run pytest
uv run python -m <name>_mcp.server
```

---

## 10. Technology Stack Reference

> **Purpose:** Complete technology inventory with version, support lifecycle, advantages, and risks.
> Use this for planning upgrades, risk assessment, and onboarding new team members.

### 10.1 Core Platform (All MCP Servers)

| Technology | Version | Support | Advantage | Risk |
|------------|---------|---------|-----------|------|
| **Python** | 3.12 | EOL: Oct 2028 | Type hints, async native, pattern matching, fastest CPython yet | Annual version upgrades; 3.13 migration needed by 2028 |
| **FastMCP** | ≥2.0.0 | Active (Community) | Pythonic decorators, automatic schema, streamable HTTP transport | Community-maintained; not official Anthropic; API may evolve |
| **mcp** (SDK) | ≥1.0.0 | Active (Anthropic) | Official MCP protocol implementation, JSON-RPC 2.0 | Protocol still evolving; breaking changes possible |
| **Pydantic** | ≥2.0 | Active | Fast validation, Settings management, JSON schema generation | v1→v2 migration was breaking; future major versions |
| **pydantic-settings** | ≥2.0 | Active | Env vars, .env files, type coercion, nested config | Coupled to Pydantic major versions |
| **structlog** | ≥24.0 | Active | Structured JSON logging, context binding, processors | Less common than stdlib logging; team learning curve |
| **httpx** | ≥0.27 | Active | Async HTTP, HTTP/2 support, timeout handling | Replaces requests; team familiarity transition |
| **uv** | ≥0.4.0 | Active (Astral) | 10-100x faster than pip, lockfile, private index | New tool (2024); less battle-tested than pip |

### 10.2 Server-Specific Dependencies

| Technology | Version | Support | Advantage | Risk |
|------------|---------|---------|-----------|------|
| **confluent-kafka** | ≥2.3.0 | Active (Confluent) | 95% of Java perf (librdkafka C), Kafka AdminClient, Schema Registry | C library dependency; build complexity on some platforms |
| **asyncpg** | ≥0.29.0 | Active | Fastest PostgreSQL driver, native async, prepared statements | PostgreSQL only; no connection pooling built-in |
| **psycopg2** | ≥2.9.0 | Maintenance | Mature, stable, widely used PostgreSQL adapter | Sync only; psycopg3 is the future |
| **psycopg** (v3) | ≥3.1.0 | Active | Async support, better typing, connection pooling | Newer; less adoption than psycopg2 |
| **boto3** | ≥1.34 | Active (AWS) | AWS Secrets Manager, S3, official AWS SDK | Large dependency; AWS lock-in |

### 10.3 VS Code Extension (TypeScript)

| Technology | Version | Support | Advantage | Risk |
|------------|---------|---------|-----------|------|
| **Node.js** | 20 LTS | EOL: Apr 2026 | VS Code runtime, npm ecosystem | Annual LTS cycle; upgrade to Node 22 by 2026 |
| **TypeScript** | ≥5.3 | Active (Microsoft) | Type safety, VS Code API types, IDE support | Compilation step; version alignment with VS Code |
| **VS Code API** | ≥1.85 | Active (Microsoft) | AuthenticationProvider, Secrets API, Extension API | API changes with VS Code releases; test on updates |
| **@vscode/test-electron** | ≥2.3 | Active | Official extension testing framework | E2E tests slow; flaky on CI |

### 10.4 Infrastructure & Services

| Technology | Version | Support | Advantage | Risk |
|------------|---------|---------|-----------|------|
| **Kong Gateway** | ≥3.4 | Active (Kong Inc) | JWT validation, rate limiting, routing, plugin ecosystem | Operational complexity; Kong-specific config |
| **TimescaleDB** | ≥2.14 | Active (Timescale) | PostgreSQL extension, auto-retention, compression, SQL-native | Adds to PostgreSQL; licensing changes possible |
| **PostgreSQL** | 16 | EOL: Nov 2028 | ACID, mature, TimescaleDB host | 5-year support cycle; plan upgrade to 17 |
| **Amazon MSK** | Kafka 3.6+ | Active (AWS) | Managed Kafka, auto-scaling, IAM auth | AWS lock-in; less control than self-managed |
| **Amazon EKS** | K8s 1.29+ | Active (AWS) | Managed Kubernetes, auto-upgrades, IRSA | AWS lock-in; K8s version deprecation cycle |
| **Prometheus** | ≥2.50 | Active (CNCF) | Pull-based metrics, PromQL, ecosystem | Requires Grafana for viz; storage scaling |
| **Grafana** | ≥10.0 | Active (Grafana Labs) | Dashboards, alerting, data source plugins | Licensing (OSS vs Enterprise); config complexity |

### 10.5 Development & CI/CD

| Technology | Version | Support | Advantage | Risk |
|------------|---------|---------|-----------|------|
| **pytest** | ≥8.0 | Active | Fixtures, parametrize, plugin ecosystem | Complex fixtures can be hard to debug |
| **pytest-asyncio** | ≥0.24 | Active | Native async test support, auto mode | Mode configuration can be confusing |
| **Ruff** | ≥0.4.0 | Active (Astral) | 10-100x faster than flake8+isort+black, all-in-one | Newer; replaces multiple tools team knows |
| **GitHub Actions** | N/A | Active (GitHub) | Native GitHub integration, marketplace | GitHub lock-in; self-hosted runners needed for security |
| **Helm** | ≥3.14 | Active (CNCF) | K8s package manager, values per environment | Template complexity; chart versioning overhead |
| **Docker** | ≥24.0 | Active (Docker Inc) | Container standard, multi-stage builds | Image size management; security scanning needed |

### 10.6 Security & Auth

| Technology | Version | Support | Advantage | Risk |
|------------|---------|---------|-----------|------|
| **PingFederate** | ≥12.0 | Active (Ping Identity) | Enterprise SSO, OAuth 2.0 + PKCE, SAML | Vendor lock-in; license cost; integration complexity |
| **OAuth 2.0 + PKCE** | RFC 7636 | Standard | No client secret needed, secure for public clients | Implementation complexity; token lifecycle management |
| **JWT (RS256)** | RFC 7519 | Standard | Stateless validation, JWKS caching | Token size; revocation complexity; clock skew |
| **macOS Keychain / Windows Credential Manager** | OS Native | Active | Secure token storage, OS-level encryption | Platform-specific APIs; testing complexity |

### 10.7 Support Lifecycle Summary

| Technology | Current Version | Upgrade By | Action Required |
|------------|-----------------|------------|-----------------|
| Python 3.12 | 3.12.x | Oct 2028 | Plan 3.13 migration in 2027 |
| Node.js 20 | 20 LTS | Apr 2026 | Upgrade to Node 22 LTS |
| PostgreSQL 16 | 16.x | Nov 2028 | Plan PostgreSQL 17 migration |
| Kubernetes | 1.29+ | Rolling | Follow EKS version support |
| Kong Gateway | 3.4+ | Rolling | Follow Kong LTS releases |

### 10.8 Risk Matrix

| Risk Level | Technologies | Mitigation |
|------------|--------------|------------|
| 🔴 **High** | PingFederate (vendor), AWS (lock-in) | Abstract auth interface; multi-cloud design if needed |
| 🟡 **Medium** | FastMCP (new), uv (new), Ruff (new) | Pin versions; monitor releases; have fallback plan |
| 🟢 **Low** | Python, PostgreSQL, Prometheus | Mature, stable, large community support |

---

## 11. Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Polyrepo** | Yes | Independent deployments, team velocity, selective releases |
| **Shared Libs** | Private PyPI | Versioned, opt-in upgrades, no forced deployments |
| **Package Manager** | uv | Fast, lockfile support, private index support |
| **Framework** | FastMCP | Pythonic, decorator-based, active development |
| **Transport** | Streamable HTTP | Production-ready, stateless mode for K8s |
| **Auth** | Kong Gateway + VS Code Extension | Kong validates JWT (RS256), strips token, injects user headers; MCP servers have NO auth code |
| **Config** | Pydantic Settings | Validation, env support, type safety |
| **Testing** | pytest-asyncio | Async-native, fixture support |
| **Audit Storage** | TimescaleDB | SQL-native, auto-retention, compression |

### Why Polyrepo over Monorepo?

| Concern | Monorepo | Polyrepo |
|---------|----------|----------|
| Shared lib change | Forces rebuild of ALL servers | Each server upgrades independently |
| Low-traffic server | Must redeploy even if unchanged | No deployment needed |
| Team blocking | One broken test blocks everyone | Isolated CI/CD per repo |
| Release cycle | Coupled releases | Independent releases |
| Developer working on multiple servers | Single checkout | Multiple checkouts (manageable) |

---

## 12. Time Series Database for Audit Trail

### Why Time Series DB for MCP Auditing?

| Requirement | TSDB Fit |
|-------------|----------|
| High write throughput | Optimized for append-only writes |
| Time-based queries | Native timestamp indexing |
| Auto-purge after 6 months | Built-in retention policies |
| Low storage cost | Compression designed for time data |

### Recommended: TimescaleDB

PostgreSQL extension with native retention policies. Familiar SQL interface.

```sql
-- Create hypertable for tool audit events
CREATE TABLE tool_audit_events (
    event_id      UUID DEFAULT gen_random_uuid(),
    event_time    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id       TEXT NOT NULL,
    user_email    TEXT,
    server_name   TEXT NOT NULL,          -- topic-management-mcp, database-management-mcp
    tool_name     TEXT NOT NULL,          -- create_topic, execute_query
    request       JSONB NOT NULL,         -- Tool input parameters
    response      JSONB,                  -- Tool output (truncated)
    status        TEXT NOT NULL,          -- success, error, timeout
    duration_ms   INTEGER,
    correlation_id TEXT,                  -- Request tracing
    client_ip     INET,
    PRIMARY KEY (event_time, event_id)
);

-- Convert to hypertable (automatic time-based partitioning)
SELECT create_hypertable('tool_audit_events', 'event_time');

-- Auto-purge after 6 months
SELECT add_retention_policy('tool_audit_events', INTERVAL '6 months');

-- Compression for older data (reduces storage ~90%)
ALTER TABLE tool_audit_events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'server_name, tool_name'
);
SELECT add_compression_policy('tool_audit_events', INTERVAL '7 days');
```

### Alternative: InfluxDB

If you prefer a dedicated TSDB with Flux query language:

```flux
// InfluxDB bucket with 180-day retention
bucket = "mcp_audit"
retention = 180d  // 6 months

// Sample query: Tool usage by user last 24h
from(bucket: "mcp_audit")
  |> range(start: -24h)
  |> filter(fn: (r) => r._measurement == "tool_execution")
  |> group(columns: ["user_id", "tool_name"])
  |> count()
```

### Event Schema

```python
# mcp-common/src/mcp_common/audit/models.py
from datetime import datetime
from pydantic import BaseModel
from typing import Any, Optional

class ToolAuditEvent(BaseModel):
    """Audit event for every MCP tool execution."""
    event_time: datetime
    user_id: str
    user_email: Optional[str] = None
    server_name: str           # topic-management-mcp, database-management-mcp
    tool_name: str             # create_topic, execute_query
    request: dict[str, Any]    # Input parameters (sanitized)
    response: Optional[dict[str, Any]] = None  # Output (truncated)
    status: str                # success | error | timeout
    duration_ms: int
    correlation_id: Optional[str] = None
    client_ip: Optional[str] = None
```

### Audit Middleware for FastMCP

```python
# mcp-common/src/mcp_common/audit/middleware.py
import time
from functools import wraps
from datetime import datetime
from mcp_common.audit.models import ToolAuditEvent
from mcp_common.audit.writer import AuditWriter
from mcp_common.context.middleware import get_current_user

def audit_tool(writer: AuditWriter):
    """Decorator to audit tool executions to TSDB."""
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            start = time.perf_counter()
            status = "success"
            response = None
            user = get_current_user()  # User context from Kong headers
            
            try:
                response = await func(*args, **kwargs)
                return response
            except Exception as e:
                status = "error"
                response = {"error": str(e)}
                raise
            finally:
                duration_ms = int((time.perf_counter() - start) * 1000)
                event = ToolAuditEvent(
                    event_time=datetime.utcnow(),
                    user_id=user.email,
                    user_email=user.email,
                    server_name=get_server_name(),
                    tool_name=func.__name__,
                    request=sanitize_request(kwargs),
                    response=truncate_response(response),
                    status=status,
                    duration_ms=duration_ms,
                    correlation_id=user.correlation_id,
                )
                await writer.write(event)
        return wrapper
    return decorator
```

### Usage in MCP Server

```python
# topic-management-mcp/src/topic_management_mcp/tools/topics.py
from mcp_common.audit import audit_tool, get_audit_writer

audit = get_audit_writer()

@mcp.tool()
@audit_tool(audit)
async def create_topic(
    name: str,
    partitions: int = 3,
    replication_factor: int = 3
) -> str:
    """Create a new Kafka topic."""
    # Implementation...
```

### Sample Audit Queries

```sql
-- Top 10 most used tools (last 30 days)
SELECT tool_name, COUNT(*) as executions
FROM tool_audit_events
WHERE event_time > NOW() - INTERVAL '30 days'
GROUP BY tool_name
ORDER BY executions DESC
LIMIT 10;

-- User activity report
SELECT user_email, server_name, COUNT(*) as calls,
       AVG(duration_ms) as avg_duration_ms
FROM tool_audit_events
WHERE event_time > NOW() - INTERVAL '7 days'
GROUP BY user_email, server_name
ORDER BY calls DESC;

-- Error rate by tool
SELECT tool_name,
       COUNT(*) FILTER (WHERE status = 'error') as errors,
       COUNT(*) as total,
       ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'error') / COUNT(*), 2) as error_rate
FROM tool_audit_events
WHERE event_time > NOW() - INTERVAL '24 hours'
GROUP BY tool_name
HAVING COUNT(*) > 10
ORDER BY error_rate DESC;

-- Slow tool executions
SELECT event_time, user_email, tool_name, duration_ms, request
FROM tool_audit_events
WHERE duration_ms > 5000  -- > 5 seconds
  AND event_time > NOW() - INTERVAL '24 hours'
ORDER BY duration_ms DESC;
```

### TSDB Options Comparison

| Feature | TimescaleDB | InfluxDB | QuestDB |
|---------|-------------|----------|---------|
| Query Language | SQL | Flux/InfluxQL | SQL |
| Retention Policy | Native | Native | Manual |
| Compression | ~90% | ~90% | ~95% |
| Postgres Compatible | Yes | No | Partial |
| Learning Curve | Low (SQL) | Medium | Low |
| Cloud Managed | Timescale Cloud | InfluxDB Cloud | QuestDB Cloud |

### Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     MCP Platform                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌────────────────────┐  ┌─────────────────────┐           │
│  │topic-management-mcp│  │database-management- │           │
│  │                    │  │         mcp         │           │
│  └─────────┬──────────┘  └──────────┬──────────┘           │
│            │                        │                       │
│            └────────────┬───────────┘                       │
│                         │                                   │
│  ┌──────────────────────┐                                  │
│  │ queue-management-mcp │                                  │
│  └───────────┬──────────┘                                  │
│              │                                             │
│              └──────────────┐                              │
│                             │                              │
│                             ▼                              │
│              ┌────────────────────────┐                    │
│              │   Audit Writer (async) │                    │
│              │   - Batched writes     │                    │
│              │   - Non-blocking       │                    │
│              └───────────┬────────────┘                    │
│                          │                                 │
└──────────────────────────┼─────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │   TimescaleDB          │
              │   - Hypertable         │
              │   - 6-month retention  │
              │   - Auto-compression   │
              └────────────────────────┘
```

---

## 13. Open Questions

- [x] ~~Event sourcing for audit trail of tool calls?~~ → **TimescaleDB with retention policy**
- [x] ~~gRPC between MCP servers?~~ → **Not needed** (AI agent orchestrates; servers stay independent)
- [x] ~~Rate limiting at Kong or per-server?~~ → **Kong only** (centralized, Redis-backed)
- [x] ~~Shared resource caching (Redis)?~~ → **Not needed** (static data in config; dynamic data must be fresh)

---

## 14. Next Steps

1. **Review this proposal** - Gather feedback from team
2. **Setup private PyPI** - Configure Nexus/Artifactory for Python packages
3. **Create mcp-common repo** - Implement auth, logging, config, audit
4. **Publish mcp-common v1.0.0** - First release to private PyPI
5. **Create topic-management-mcp repo** - Port from Java to Python, pin mcp-common
6. **Create database-management-mcp repo** - Validate polyrepo patterns work
7. **Create queue-management-mcp repo** - RabbitMQ/SQS queue operations
8. **Setup CI/CD per repo** - GitHub Actions for build, test, deploy
9. **Create mcp-server-template** - Cookiecutter or template repo for new servers

---

*This is a living document. Update as architecture evolves.*
