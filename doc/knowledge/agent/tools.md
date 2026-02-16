# AI Agent Tools - Quick Reference

> **Source**: [Hugging Face Agents Course - Unit 1: Tools](https://huggingface.co/learn/agents-course/en/unit1/tools)  
> **Reading Time**: ~5 minutes

---

## 1. What are AI Tools?

**A Tool is a function given to the LLM that fulfills a clear objective.**

Tools extend LLM capabilities beyond text generation, allowing agents to perform real-world actions.

### Common Tool Examples

| Tool Type | Purpose |
|-----------|---------|
| **Web Search** | Fetch up-to-date information from the internet |
| **Image Generation** | Create images based on text descriptions |
| **Retrieval** | Retrieve information from external sources |
| **API Interface** | Interact with external APIs (GitHub, YouTube, Spotify, etc.) |
| **Calculator** | Perform arithmetic operations accurately |

### Why Tools are Essential

1. **Complement LLM weaknesses** - Calculators provide accurate arithmetic vs. LLM approximations
2. **Access real-time data** - LLMs only know training data; tools fetch current information
3. **Prevent hallucinations** - Without tools, asking "today's weather" causes LLM to make up data
4. **Perform specialized actions** - File operations, API calls, database queries

---

## 2. How Tools Work

**Key Insight**: LLMs can only receive text inputs and generate text outputs. They cannot call tools directly.

### The Tool-Calling Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. User asks: "What's the weather in Paris?"                    │
│                           ↓                                      │
│ 2. LLM recognizes opportunity to use "weather" tool             │
│                           ↓                                      │
│ 3. LLM generates text: call weather_tool('Paris')               │
│                           ↓                                      │
│ 4. Agent parses response, identifies tool call                  │
│                           ↓                                      │
│ 5. Agent EXECUTES the tool (LLM doesn't execute!)               │
│                           ↓                                      │
│ 6. Agent appends result to conversation                         │
│                           ↓                                      │
│ 7. LLM receives result, generates natural response to user      │
└─────────────────────────────────────────────────────────────────┘
```

**Important**: Tool-calling steps are hidden from users. The Agent handles execution in the background, making it appear as if the LLM directly interacted with the tool.

---

## 3. Tool Definition Requirements

A well-defined tool MUST contain:

| Component | Required | Description |
|-----------|----------|-------------|
| **Name** | ✅ Yes | Descriptive name (e.g., `calculator`) |
| **Description** | ✅ Yes | Textual description of what the function does |
| **Callable** | ✅ Yes | The actual function to perform the action |
| **Arguments** | ✅ Yes | Input parameters with type hints |
| **Outputs** | Optional | Return type(s) of the function |

### Textual Format for LLMs

Tools are described in the **system prompt** using precise, structured text:

```
Tool Name: calculator, Description: Multiply two integers., Arguments: a: int, b: int, Outputs: int
```

**Why precision matters**: LLMs parse this text to understand:
1. What the tool does
2. What exact inputs it expects
3. What output to expect

---

## 4. Implementing Tools in Python

### Basic Tool Function

```python
def calculator(a: int, b: int) -> int:
    """Multiply two integers."""
    return a * b
```

**Required elements**:
- Clear function name
- Type hints for parameters
- Docstring description
- Return type annotation

### Using the @tool Decorator

```python
@tool
def calculator(a: int, b: int) -> int:
    """Multiply two integers."""
    return a * b

print(calculator.to_string())
# Output: Tool Name: calculator, Description: Multiply two integers., Arguments: a: int, b: int, Outputs: int
```

The decorator uses Python's `inspect` module to automatically extract:
- Function name → Tool name
- Docstring → Description
- Type hints → Arguments and outputs

### Generic Tool Class Structure

```python
from typing import Callable

class Tool:
    """
    A reusable tool wrapper for LLM agents.
    
    Attributes:
        name (str): Name of the tool
        description (str): What the tool does
        func (callable): The function this tool wraps
        arguments (list): List of (name, type) tuples
        outputs (str): Return type(s)
    """
    def __init__(self,
                 name: str,
                 description: str,
                 func: Callable,
                 arguments: list,
                 outputs: str):
        self.name = name
        self.description = description
        self.func = func
        self.arguments = arguments
        self.outputs = outputs

    def to_string(self) -> str:
        """Generate LLM-friendly tool description."""
        args_str = ", ".join([
            f"{arg_name}: {arg_type}" 
            for arg_name, arg_type in self.arguments
        ])
        return (
            f"Tool Name: {self.name},"
            f" Description: {self.description},"
            f" Arguments: {args_str},"
            f" Outputs: {self.outputs}"
        )

    def __call__(self, *args, **kwargs):
        """Execute the underlying function."""
        return self.func(*args, **kwargs)
```

### Manual Tool Creation

```python
calculator_tool = Tool(
    "calculator",                   # name
    "Multiply two integers.",       # description
    calculator,                     # function to call
    [("a", "int"), ("b", "int")],   # inputs (names and types)
    "int",                          # output
)
```

---

## 5. System Prompt Integration

Tools are injected into the **system prompt** so the LLM knows what's available:

```
You are a helpful assistant with access to the following tools:

Tool Name: calculator, Description: Multiply two integers., Arguments: a: int, b: int, Outputs: int
Tool Name: weather, Description: Get current weather for a location., Arguments: location: str, Outputs: str

When you need to use a tool, respond with: call <tool_name>(<arguments>)
```

**Format consistency is critical** - Always use the same format for all tools to avoid parsing errors.

---

## 6. Model Context Protocol (MCP)

**MCP is an open protocol that standardizes how applications provide tools to LLMs.**

### MCP Benefits

| Feature | Benefit |
|---------|---------|
| **Pre-built integrations** | Growing list of ready-to-use tool integrations |
| **Vendor flexibility** | Switch between LLM providers without rewriting tools |
| **Security best practices** | Data stays within your infrastructure |
| **Framework compatibility** | Any MCP-implementing framework can use MCP tools |

**Key advantage**: Eliminates need to reimplement the same tool interface for each framework.

> 📚 Learn more: [Hugging Face MCP Course](https://huggingface.co/learn/mcp-course/)

---

## 7. Key Takeaways

| Concept | Summary |
|---------|---------|
| **Tools are functions** | Given to LLMs to extend their capabilities |
| **LLMs don't execute tools** | They generate text that the Agent parses and executes |
| **Clear definitions required** | Name, description, arguments, outputs |
| **System prompt injection** | Tools described in system prompt for LLM awareness |
| **MCP standardization** | Open protocol for portable tool definitions |
| **Type hints matter** | Enable auto-generation of tool descriptions |

---

## 8. Best Practices

### ✅ Do

- Use clear, descriptive function names
- Write detailed docstrings
- Add type hints for all parameters
- Test tools independently before integration
- Use consistent formatting across all tools
- Keep tools focused on single responsibilities

### ❌ Don't

- Rely on LLMs for precise calculations (use calculator tools)
- Expect LLMs to have real-time knowledge (use search tools)
- Create overly complex multi-purpose tools
- Skip type hints or descriptions
- Assume LLMs execute tools (they only generate text)

---

## 9. Quick Reference Card

```
┌────────────────────────────────────────────────────────────┐
│                    TOOL ANATOMY                            │
├────────────────────────────────────────────────────────────┤
│  @tool                                                     │
│  def tool_name(param1: type1, param2: type2) -> output:    │
│      """Description of what the tool does."""              │
│      return result                                         │
├────────────────────────────────────────────────────────────┤
│  EXECUTION FLOW:                                           │
│  User → LLM → "call tool(args)" → Agent → Tool → Result    │
│                                               → LLM → User │
├────────────────────────────────────────────────────────────┤
│  SYSTEM PROMPT FORMAT:                                     │
│  Tool Name: X, Description: Y, Arguments: Z, Outputs: W    │
└────────────────────────────────────────────────────────────┘
```

---

*Document created from [HuggingFace Agents Course Unit 1 - Tools](https://huggingface.co/learn/agents-course/en/unit1/tools)*
