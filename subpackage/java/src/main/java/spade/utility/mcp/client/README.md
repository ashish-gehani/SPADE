# SPADE MCP Client

A standalone client that connects an LLM (Anthropic or a mock) to a running MCP server, useful for testing tools without a full MCP-compatible app like Claude Code/Desktop. See [docs/mcp/HOW-TO.md](../../../../../../../../../docs/mcp/HOW-TO.md) for setup and usage (starting the server, running the client, config).

## Layout

```
client/
├── Main.java               entry point: parses settings, then builds the LLM and user client and runs it
├── Client.java              MCP protocol client: connects to the server, drives the LLM/tool-call loop
├── ChatHistory.java         conversation state for a chat session
├── llm/                    the LLM side of the conversation
│   ├── Factory.java           builds an LLM from Setting.LLM
│   ├── LLM.java                interface: respond to a conversation, given the available tools
│   ├── anthropic/               Anthropic API-backed LLM
│   │   └── Anthropic.java
│   └── mock/                    scenario-driven mock LLM, for testing without a real LLM
│       ├── Mock.java              replays a named Scenario's tool calls, verifying the real results
│       ├── ToolCall.java          one premade tool call: name, tool name, input, expected result
│       ├── Text.java              canned text responses
│       └── scenario/
│           ├── Scenario.java        a named, ordered list of ToolCalls
│           └── Registry.java        loads Scenarios from config, built on spade.utility.setting
├── setting/                this client's settings, built on spade.utility.setting
│   ├── Setting.java           the parsed result, grouped into MCP, LLM, and User
│   ├── Parser.java             field-level parsing/validation
│   ├── LLMType.java             anthropic|mock
│   └── UserClientMode.java      cli|web
└── user/                   the human-facing side of the conversation
    ├── Client.java             abstract base shared by CLI and Web
    ├── Factory.java             builds a Client from Setting.User
    ├── CLI.java                  stdin/stdout chat loop
    └── Web.java                  experimental web front end (not stable)
```

See [llm/mock/scenario/README.md](llm/mock/scenario/README.md) for the mock scenario config key format.

## Testing

See [the test README](../../../../../../test/java/spade/utility/mcp/client/README.md) for what functionality is tested where.
