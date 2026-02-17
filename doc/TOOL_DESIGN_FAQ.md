# Tool Design & LLM Orchestration FAQ

> Best practices for building MCP tools and configuring LLM workflows

---

## Table of Contents

1. [Single Responsibility Tools](#1-single-responsibility-tools)
2. [LLM Tool Orchestration](#2-llm-tool-orchestration)
3. [Automatic Workflows](#3-automatic-workflows)
4. [System Prompt Design](#4-system-prompt-design)
5. [Asking Follow-up Questions](#5-asking-follow-up-questions)
6. [Example: Kafka Topic Provisioning](#6-example-kafka-topic-provisioning)
7. [Error Handling & Recovery](#7-error-handling--recovery)
8. [Security & Validation](#8-security--validation)
9. [Tool Design Patterns](#9-tool-design-patterns)
10. [Performance & Optimization](#10-performance--optimization)
11. [Testing & Debugging](#11-testing--debugging)

---

## 1. Single Responsibility Tools

### Q: Should a tool like `create_topic` also create service accounts, assign ACLs, and send emails?

**A: No.** Tools should follow the Single Responsibility Principle.

| Aspect | Single-Responsibility ✅ | Multi-Purpose Tool ❌ |
|--------|--------------------------|----------------------|
| **Reusability** | Can be composed differently for different use cases | Locked to one specific workflow |
| **Testing** | Easy to test in isolation | Complex integration tests required |
| **Error Handling** | Clear error attribution ("topic creation failed") | Ambiguous ("something failed") |
| **Flexibility** | LLM can skip/reorder steps as needed | All-or-nothing execution |
| **Maintenance** | Change one piece without affecting others | Changes risk breaking entire flow |

### Q: How should I design my Kafka tools?

**A:** Create separate, focused tools:

```
┌─────────────────────────────────────────────────────────────────┐
│                    KAFKA TOOLBOX                                │
├─────────────────────────────────────────────────────────────────┤
│  1. create_topic(name, partitions, replication_factor)          │
│     → Creates topic only                                        │
│                                                                 │
│  2. create_service_account(topic_name)                          │
│     → Creates SA in Secret Manager, returns credentials         │
│                                                                 │
│  3. assign_acl(service_account, topic, permissions)             │
│     → Sets ACL permissions                                      │
│                                                                 │
│  4. send_credentials_email(recipient, credentials)              │
│     → Sends email with credentials                              │
│                                                                 │
│  5. list_topics() / describe_topic() / delete_topic()           │
│     → Read/management operations                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. LLM Tool Orchestration

### Q: If I have multiple single-responsibility tools, how does the LLM know which order to call them?

**A:** The LLM uses a **Think → Act → Observe** loop to reason about tool sequencing:

```
┌─────────────────────────────────────────┐
│              THOUGHT                    │
│   "What should I do next?"              │
│              ↓                          │
│              ACTION                     │
│   Call tool with arguments              │
│              ↓                          │
│           OBSERVATION                   │
│   Receive and process result            │
│              ↓                          │
│   [Loop until objective met]            │
└─────────────────────────────────────────┘
```

### Q: If a user says "create topic 'orders'", will the LLM automatically call all related tools (service account, ACL, email)?

**A: No.** By default, the LLM is **intent-driven**, not workflow-driven. It will only do what the user explicitly asks:

```
User: "Create a topic 'orders'"

┌────────────────────────────────────────────────────────────────┐
│ THOUGHT: "User wants to create a topic called 'orders'.        │
│           They didn't mention service accounts or ACLs.        │
│           I should just create the topic."                     │
│                                                                │
│ ACTION: call create_topic("orders")                            │
│                                                                │
│ OBSERVATION: "Topic 'orders' created successfully"             │
│                                                                │
│ FINAL: "Done! Topic 'orders' has been created."                │
└────────────────────────────────────────────────────────────────┘
```

To make the LLM follow a workflow, you need to configure it via the **system prompt**.

---

## 3. Automatic Workflows

### Q: How do I make the LLM automatically execute a multi-step workflow?

**A:** Define **mandatory workflows** in the system prompt:

```markdown
You are a Kafka Platform Assistant.

MANDATORY WORKFLOW: When a user requests to create a topic:
1. Create the topic with default settings (3 partitions, replication=3)
2. Create a service account in Secret Manager
3. Assign read/write ACLs to the service account
4. Send credentials email to the requesting user

Execute ALL steps automatically. Do not ask for confirmation.
If any step fails, report the error and what was completed.
```

### Q: What are the different approaches to handle workflows?

| Approach | User Says | Tools Called | UX |
|----------|-----------|--------------|-----|
| **Minimal (default)** | "create topic orders" | 1 tool | User must know to ask for more |
| **Ask for confirmation** | "create topic orders" | 1 tool + follow-up question | Extra back-and-forth |
| **Auto-workflow** | "create topic orders" | 4 tools automatically | Simple UX ✅ |
| **Composite tool** | "create topic orders" | 1 composite tool | Simple UX, less flexible |

### Q: When should I use auto-workflow vs. confirmation?

| Use Auto-Workflow When | Use Confirmation When |
|------------------------|----------------------|
| Standard process that's always needed | Optional/expensive operations |
| Internal users who expect full setup | External users who pay per resource |
| Consistent business rules | Variable requirements |
| Low-risk operations | Destructive/irreversible actions |

---

## 4. System Prompt Design

### Q: How do I handle required vs. optional parameters?

**A:** Follow this pattern:

| Input Type | Behavior |
|------------|----------|
| **Required + Missing** | Ask user once |
| **Required + Provided** | Use it, don't ask |
| **Optional** | Use default, never ask |
| **Destructive action** | Always confirm |

### Q: What's the recommended system prompt for Kafka topic provisioning?

```markdown
You are a Kafka Platform Assistant.

## Topic Creation Workflow

When a user requests to create a topic, execute this workflow:

### Required Information:
- Topic name (REQUIRED - from user request)
- Email address (REQUIRED - ask if not provided)

### Optional Information (use defaults):
- Partitions: 3 (default)
- Replication factor: 3 (default)
- Team name: derive from email domain or ask if unclear

### Execution Steps (execute ALL automatically):
1. Call `create_topic(name, partitions, replication_factor)`
2. Call `create_service_account(topic_name)`
3. Call `assign_acl(service_account, topic, "read,write")`
4. Call `send_credentials_email(email, credentials)`

### Rules:
- If email is NOT provided: Ask "I'll create topic '{name}' and set up access. What email should I send the credentials to?"
- If email IS provided: Execute all 4 steps without asking
- If any step fails: Report error and what was completed
- Do NOT ask for optional parameters - use defaults

## Topic Deletion Workflow

**ALWAYS confirm before executing**: "Are you sure you want to delete topic '{name}'? This is irreversible."

## Read-Only Operations (list/describe)

Execute immediately - no confirmation needed.
```

---

## 5. Asking Follow-up Questions

### Q: What makes a good follow-up question?

| Quality | Question | Why |
|---------|----------|-----|
| ❌ Bad | "Please provide email" | Robotic, unclear purpose |
| ⚠️ Ok | "What is your email?" | Ambiguous - personal or for credentials? |
| ✅ Good | "What email should I send the credentials to?" | Clear purpose, actionable |
| ✅ Best | "I'll create topic 'orders' and set up access. What email should I send the credentials to?" | Confirms action + clear purpose |

### Q: What are the best practices for follow-up questions?

1. **State the purpose**
   ```
   ❌ "What's your email?"
   ✅ "What email should I send the Kafka credentials to?"
   ```

2. **One question at a time**
   ```
   ❌ "What's your email and team name?"
   ✅ "What email should I send the credentials to?"
   ```

3. **Offer defaults when possible**
   ```
   ✅ "What email should I send credentials to? (or reply 'me' to use your registered email)"
   ```

4. **Be specific about what you'll do**
   ```
   ✅ "I'll create topic 'orders' with default settings. What email should I send the access credentials to?"
   ```

---

## 6. Example: Kafka Topic Provisioning

### Scenario 1: Email NOT provided

```
User: "Create topic 'orders'"

LLM: "I'll create topic 'orders' and set up access. What email should I send the credentials to?"

User: "john@company.com"

LLM: [Executes all 4 tools automatically]
     "Done! Topic 'orders' created with the following:
      ✓ Topic created (3 partitions, replication=3)
      ✓ Service account: orders-sa
      ✓ ACLs assigned: read/write
      ✓ Credentials sent to john@company.com"
```

### Scenario 2: Email provided upfront

```
User: "Create topic 'orders', send credentials to john@company.com"

LLM: [Executes all 4 tools automatically - no questions]
     "Done! Topic 'orders' created. Credentials sent to john@company.com"
```

### Scenario 3: Failure with automatic rollback

```
User: "Create topic 'orders', email: john@company.com"

LLM: [Executes tools...]
     
┌────────────────────────────────────────────────────────────────┐
│ ACTION 1: create_topic("orders")                               │
│ OBSERVATION: ✓ Success                                         │
├────────────────────────────────────────────────────────────────┤
│ ACTION 2: create_service_account("orders")                     │
│ OBSERVATION: ✓ Success                                         │
├────────────────────────────────────────────────────────────────┤
│ ACTION 3: assign_acl("orders-sa", "orders", "read,write")      │
│ OBSERVATION: ✗ Error: Permission denied                        │
├────────────────────────────────────────────────────────────────┤
│ THOUGHT: "ACL failed. I must rollback all completed steps."    │
├────────────────────────────────────────────────────────────────┤
│ ROLLBACK 1: delete_service_account("orders-sa")                │
│ OBSERVATION: ✓ Service account deleted                         │
├────────────────────────────────────────────────────────────────┤
│ ROLLBACK 2: delete_topic("orders")                             │
│ OBSERVATION: ✓ Topic deleted                                   │
├────────────────────────────────────────────────────────────────┤
│ FINAL: "Failed to provision topic 'orders'.                    │
│         Error: ACL assignment failed - Permission denied.      │
│         All changes have been rolled back.                     │
│         Please contact admin to resolve permissions, then      │
│         try again."                                            │
└────────────────────────────────────────────────────────────────┘
```

### Scenario 4: Delete request (confirmation required)

```
User: "Delete topic 'orders'"

LLM: "Are you sure you want to delete topic 'orders'? This will:
      - Remove the topic and all messages
      - Revoke associated service accounts
      - This action is irreversible
      
      Reply 'yes' to confirm."

User: "yes"

LLM: [Executes delete]
     "Topic 'orders' has been deleted."
```

---

## 7. Error Handling & Recovery

### Q: What happens if a tool in the middle of a workflow fails?

**A:** When any step fails, the LLM must **automatically rollback all completed steps** to avoid leaving the system in a partial/unusable state. Partial completion is NOT acceptable - users cannot use half-provisioned resources.

Configure mandatory rollback in your system prompt:

```markdown
### Error Handling Rules (MANDATORY ROLLBACK):
If ANY step in a workflow fails:
1. STOP further execution immediately
2. ROLLBACK all completed steps in reverse order:
   - If `assign_acl` fails → delete service account → delete topic
   - If `create_service_account` fails → delete topic
   - If `send_credentials_email` fails → delete ACL → delete service account → delete topic
3. Report: "Failed to provision. Error: [reason]. All changes rolled back."
4. Suggest: How to resolve the issue and retry

Exception: If `create_topic` fails (first step), no rollback needed - just report error.
```

**Execution with failure and rollback:**
```
User: "Create topic 'orders', email: john@company.com"

┌────────────────────────────────────────────────────────────────┐
│ ACTION 1: create_topic("orders")                               │
│ OBSERVATION: ✓ Success                                         │
├────────────────────────────────────────────────────────────────┤
│ ACTION 2: create_service_account("orders")                     │
│ OBSERVATION: ✗ Error: Secret Manager quota exceeded            │
├────────────────────────────────────────────────────────────────┤
│ THOUGHT: "Service account failed. I must rollback the topic."  │
├────────────────────────────────────────────────────────────────┤
│ ROLLBACK: delete_topic("orders")                               │
│ OBSERVATION: ✓ Topic deleted                                   │
├────────────────────────────────────────────────────────────────┤
│ FINAL: "Failed to provision topic 'orders'.                    │
│         Error: Secret Manager quota exceeded.                  │
│         All changes have been rolled back.                     │
│         Please contact admin to increase quota, then retry."   │
└────────────────────────────────────────────────────────────────┘
```

### Q: Should tools be idempotent?

**A: Yes, whenever possible.** Idempotent tools can be safely retried without side effects.

| Tool | Idempotent? | How to Implement |
|------|-------------|------------------|
| `create_topic` | ✅ Yes | Check if exists first, return success if already exists |
| `assign_acl` | ✅ Yes | Upsert pattern - same result on repeat calls |
| `send_email` | ⚠️ Partially | Include idempotency key to prevent duplicate sends |
| `delete_topic` | ✅ Yes | Return success even if already deleted |

**Example idempotent tool response:**
```json
{
  "status": "success",
  "action": "none",
  "message": "Topic 'orders' already exists with matching configuration",
  "idempotent": true
}
```

### Q: How do I handle partial failures in multi-step workflows?

**A:** **Always implement automatic rollback (compensation pattern).** Partial completion is NOT acceptable - it leaves users with unusable resources and creates orphaned objects.

**Mandatory Rollback Pattern:**
```markdown
### On Failure (ALWAYS rollback):
1. Stop execution immediately
2. Rollback completed steps in REVERSE order
3. Report failure with:
   - What failed and why
   - Confirmation that all changes were rolled back
   - How to resolve and retry
```

**Rollback Order for Topic Provisioning:**
```
Workflow: create_topic → create_service_account → assign_acl → send_email

If send_email fails:
  → revoke_acl → delete_service_account → delete_topic
  
If assign_acl fails:
  → delete_service_account → delete_topic
  
If create_service_account fails:
  → delete_topic
  
If create_topic fails:
  → (nothing to rollback)
```

**System Prompt for Rollback:**
```markdown
### Rollback Rules:
On ANY failure in a multi-step workflow:
1. Execute rollback tools in reverse order of completion
2. If a rollback tool fails, log it but continue with remaining rollbacks
3. Report final status: "Provisioning failed. Rolled back: [list]. Rollback failures: [list if any]"

NEVER leave the system in a partial state.
```

**What if rollback itself fails?**
```
┌────────────────────────────────────────────────────────────────┐
│ WORKFLOW FAILED at step 3 (assign_acl)                         │
├────────────────────────────────────────────────────────────────┤
│ ROLLBACK 1: delete_service_account("orders-sa")                │
│ OBSERVATION: ✓ Success                                         │
├────────────────────────────────────────────────────────────────┤
│ ROLLBACK 2: delete_topic("orders")                             │
│ OBSERVATION: ✗ Error: Topic in use by consumers                │
├────────────────────────────────────────────────────────────────┤
│ FINAL: "Provisioning failed. Error: ACL assignment denied.     │
│         Rollback status:                                       │
│         ✓ Service account deleted                              │
│         ✗ Topic deletion failed - topic in use                 │
│                                                                │
│         Manual cleanup required: delete topic 'orders'         │
│         Contact: platform-team@company.com"                    │
└────────────────────────────────────────────────────────────────┘
```

---

## 8. Security & Validation

### Q: How do I validate user inputs before calling tools?

**A:** Add validation rules to your system prompt and/or implement validation in tools:

**System Prompt Validation:**
```markdown
### Input Validation Rules:
- Topic names: Must match pattern `^[a-z0-9-]+$` (lowercase, numbers, hyphens only)
- Topic names: Max 64 characters
- Email: Must be valid corporate email (@company.com)
- Partitions: Must be between 1-100

If validation fails, inform user of the requirement. Do not call the tool.
```

**Example:**
```
User: "Create topic 'My Topic!'"

LLM: "Topic names can only contain lowercase letters, numbers, and hyphens.
      Please provide a valid name like 'my-topic'."
```

### Q: Should sensitive data (credentials) be returned to the LLM?

**A: Minimize exposure.** Best practices:

| Approach | Security | UX |
|----------|----------|----|
| ❌ Return full credentials to LLM | Low | LLM can display them |
| ⚠️ Return masked credentials | Medium | `****-****-1234` |
| ✅ Return reference only | High | "Credentials stored in Secret Manager: `orders-sa-cred`" |
| ✅ Send directly to user | High | "Credentials sent to john@company.com" |

**Recommended tool response:**
```json
{
  "status": "success",
  "service_account": "orders-sa",
  "secret_reference": "projects/myproj/secrets/orders-sa-cred",
  "message": "Credentials stored securely. Use send_credentials_email to deliver."
}
```

### Q: How do I handle authorization - what if user can't create topics?

**A:** Implement authorization checks at multiple levels:

**Level 1: Tool-level authorization**
```java
@Tool
public String createTopic(String name, String userEmail) {
    if (!authService.canCreateTopic(userEmail)) {
        return "Error: You don't have permission to create topics. " +
               "Please request access from your team admin.";
    }
    // proceed with creation
}
```

**Level 2: System prompt guidance**
```markdown
### Authorization:
If any tool returns a permission error:
1. Do not retry the same action
2. Explain what permission is needed
3. Suggest who to contact for access
```

**Level 3: Pre-flight check tool**
```
check_permissions(user, action) → Returns list of allowed/denied actions
```

---

## 9. Tool Design Patterns

### Q: When should I use a composite workflow tool vs. individual tools?

**A:** Use this decision matrix:

| Scenario | Use Individual Tools | Use Composite Tool |
|----------|---------------------|--------------------|
| Workflow varies by context | ✅ | |
| Steps may be reordered | ✅ | |
| Need partial execution | ✅ | |
| Always same sequence | | ✅ |
| Performance critical (reduce round-trips) | | ✅ |
| Complex error handling needed | | ✅ |
| Atomic transaction required | | ✅ |

**Hybrid approach (recommended):**
```
┌─────────────────────────────────────────────────────────────────┐
│  INDIVIDUAL TOOLS (always available):                          │
│  - create_topic()                                              │
│  - create_service_account()                                    │
│  - assign_acl()                                                │
│  - send_credentials_email()                                    │
├─────────────────────────────────────────────────────────────────┤
│  COMPOSITE TOOL (convenience wrapper):                         │
│  - provision_topic_with_access()                               │
│    → Calls all 4 tools internally                              │
│    → Handles errors/rollback                                   │
│    → Single atomic operation                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Q: How detailed should tool descriptions be?

**A:** Include enough detail for the LLM to make correct decisions:

**❌ Too Brief:**
```
Tool: create_topic
Description: Creates a topic
```

**❌ Too Verbose:**
```
Tool: create_topic
Description: This tool creates a new Apache Kafka topic in the cluster.
It connects to the Kafka admin API using the configured bootstrap servers
and creates a topic with the specified parameters. The topic will be
created with the default cleanup policy of 'delete' unless overridden...
[500 more words]
```

**✅ Just Right:**
```
Tool: create_topic
Description: Creates a new Kafka topic. Use this for data streaming needs.
             Does NOT set up access - use provision_topic_with_access for
             full setup, or call create_service_account + assign_acl separately.
Arguments:
  - name (string, required): Topic name (lowercase, hyphens allowed)
  - partitions (int, optional): Default 3
  - replication_factor (int, optional): Default 3
Returns: Success/failure with topic details
```

**Key elements:**
- What it does (1 sentence)
- When to use it vs. alternatives
- Arguments with types and defaults
- What it returns

### Q: Should tools return structured data or human-readable text?

**A:** Return **structured data** - the LLM can format it for humans:

**❌ Human-readable only:**
```
"Topic 'orders' was created successfully with 3 partitions."
```

**✅ Structured data:**
```json
{
  "status": "success",
  "topic": {
    "name": "orders",
    "partitions": 3,
    "replication_factor": 3,
    "created_at": "2026-02-16T10:30:00Z"
  }
}
```

**Why structured is better:**
- LLM can extract specific fields for next steps
- LLM can format appropriately for context
- Enables programmatic processing
- Consistent parsing

---

## 10. Performance & Optimization

### Q: Can the LLM call multiple tools in parallel?

**A:** It depends on the LLM and framework:

| Framework | Parallel Tool Calls |
|-----------|--------------------|
| OpenAI Functions | ✅ Yes (native support) |
| Claude Tools | ✅ Yes (native support) |
| LangChain | ✅ Configurable |
| Custom Agent | Depends on implementation |

**When parallel makes sense:**
```
User: "List all topics and show cluster health"

┌─────────────────────────────────────────────────────────────────┐
│  PARALLEL EXECUTION:                                           │
│  ┌─────────────────┐     ┌─────────────────┐                   │
│  │ list_topics()   │     │ cluster_health()│                   │
│  └────────┬────────┘     └────────┬────────┘                   │
│           │                       │                            │
│           └───────────┬───────────┘                            │
│                       ▼                                        │
│              Combine results                                   │
└─────────────────────────────────────────────────────────────────┘
```

**When parallel does NOT work:**
```
User: "Create topic and then assign ACL"

→ Sequential required: ACL needs topic to exist first
```

### Q: How do I reduce the number of LLM round-trips?

**A:** Several strategies:

**1. Batch information in tool responses:**
```json
{
  "topic_created": true,
  "topic_details": { "name": "orders", "partitions": 3 },
  "next_steps_hint": "Call create_service_account to set up access",
  "related_info": {
    "existing_service_accounts": ["team-a-sa", "team-b-sa"]
  }
}
```

**2. Use composite tools for common workflows:**
```
Provision topic → 1 round-trip (vs. 4 for individual tools)
```

**3. Pre-fetch likely needed data:**
```markdown
### System Prompt:
When user asks about a topic, call describe_topic which returns:
- Topic config
- Associated ACLs
- Service accounts with access
This avoids separate calls for each piece.
```

**4. Smart defaults in system prompt:**
```markdown
Use defaults without asking:
- Partitions: 3
- Replication: 3
- Retention: 7 days

→ Eliminates back-and-forth for optional params
```

---

## 11. Testing & Debugging

### Q: How do I test my system prompt workflows?

**A:** Create a test suite with scenarios:

**Test Case Template:**
```markdown
### Test: Topic Creation - Happy Path
**Input:** "Create topic 'test-orders', email: test@company.com"
**Expected Tools Called:** 
1. create_topic("test-orders", 3, 3)
2. create_service_account("test-orders")
3. assign_acl("test-orders-sa", "test-orders", "read,write")
4. send_credentials_email("test@company.com", ...)
**Expected Response Contains:** "Topic 'test-orders' created"

### Test: Topic Creation - Missing Email
**Input:** "Create topic 'test-orders'"
**Expected Tools Called:** None (should ask first)
**Expected Response Contains:** "What email should I send the credentials to?"

### Test: Topic Creation - Validation Failure  
**Input:** "Create topic 'INVALID NAME!'"
**Expected Tools Called:** None
**Expected Response Contains:** "lowercase", "hyphens"
```

**Automation approach:**
```python
test_cases = [
    {
        "input": "Create topic 'orders', email: test@co.com",
        "expected_tools": ["create_topic", "create_service_account", "assign_acl", "send_credentials_email"],
        "response_contains": ["created", "credentials sent"]
    },
    # ... more cases
]

for test in test_cases:
    result = run_agent(test["input"])
    assert_tools_called(result, test["expected_tools"])
    assert_response_contains(result, test["response_contains"])
```

### Q: How do I debug when the LLM calls wrong tools?

**A:** Common issues and fixes:

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| LLM calls wrong tool | Ambiguous descriptions | Make descriptions more distinct |
| LLM skips required steps | Missing workflow rules | Add explicit "ALWAYS do X" in prompt |
| LLM asks unnecessary questions | Over-cautious prompt | Add "use defaults, don't ask" |
| LLM hallucinates tool names | Tool not in context | Verify tool is registered |
| LLM gives wrong arguments | Unclear parameter docs | Add examples to tool description |

**Debugging checklist:**

```markdown
1. □ Check tool descriptions are clear and distinct
2. □ Verify system prompt has explicit workflow rules
3. □ Confirm all tools are registered and visible to LLM
4. □ Review argument descriptions and types
5. □ Test with simpler prompt first
6. □ Check for conflicting instructions in prompt
7. □ Enable verbose logging to see LLM reasoning
```

**Enable reasoning visibility:**
```markdown
### System Prompt Addition (for debugging):
Before calling any tool, briefly explain:
1. What you understood from the user request
2. Why you chose this tool
3. What arguments you're using and why
```

**Example debug output:**
```
User: "Set up orders topic for team-a"

LLM Reasoning: "User wants to set up a topic called 'orders' for 'team-a'.
               'Set up' implies full provisioning, not just creation.
               I'll use the complete workflow.
               
               Step 1: create_topic('orders') - creating the topic first
               Arguments: name='orders', using default partitions=3"
               
[Tool executes...]
```

---

## Summary

| Concept | Recommendation |
|---------|----------------|
| Tool design | Single responsibility - one tool, one job |
| Tool orchestration | LLM handles via Think→Act→Observe loop |
| Automatic workflows | Define in system prompt with mandatory steps |
| Required inputs | Ask only if missing |
| Optional inputs | Use defaults, never ask |
| Destructive actions | Always confirm |
| Follow-up questions | State purpose, one at a time, be specific |
| Error handling | **Mandatory rollback** - never leave partial state |
| Idempotency | Make tools safe to retry |
| Security | Minimize credential exposure, validate inputs |
| Authorization | Check permissions before execution |
| Tool descriptions | Clear, distinct, with examples |
| Tool responses | Return structured data |
| Performance | Use composite tools, batch data, smart defaults |
| Testing | Create scenario-based test suites |
| Debugging | Check descriptions, enable reasoning visibility |

---

*Document created: February 2026*
