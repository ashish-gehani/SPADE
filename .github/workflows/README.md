# GitHub Actions Workflows

| Workflow | Triggers | Purpose |
|---|---|---|
| `ci.yml` | `workflow_dispatch`, `push`/`pull_request` to `master` | Full build (configure + make, including the C/kernel components and the Java module's Maven test suite) on Linux and macOS. |
| `mcp-integration.yml` | `workflow_dispatch`, `push` to `master` (path-filtered, see below) | Builds SPADE, starts the MCP server over HTTP, and exercises it end-to-end with the mock LLM CLI client. |

## `mcp-integration.yml` path filter

This workflow provisions two full VMs (Linux and macOS) and runs the entire `./configure && make` build before it can even start testing the MCP server, so it's expensive to run on every push to `master` — most changes (unrelated reporters, storages, docs, other workflows) have nothing to do with MCP. The `paths:` filter under its `push:` trigger restricts it to changes that could plausibly affect the MCP client, server, or the scenarios the mock LLM replays.

Current paths:

- `.github/workflows/mcp-integration*` — the workflow itself, plus `mcp-integration-trigger.md`, a dedicated marker file for manually causing this push-triggered, path-filtered workflow to run without touching real code (see that file)
- `cfg/mcp/**` — MCP tool definitions (`spade_control`, `spade_query`, `spade_control_doc`, `spade_query_doc`, ...)
- `cfg/spade.utility.mcp.*` — top-level MCP client/server config files directly under `cfg/` (matches one path segment, e.g. `cfg/spade.utility.mcp.client.Main.config`, `cfg/spade.utility.mcp.server.Main.config`, `cfg/spade.utility.mcp.server.tool.registry.Registry.config`, `cfg/spade.utility.mcp.client.llm.mock.scenario.Registry.config`)
- `cfg/spade.utility.mcp.client.llm.mock.scenario.Registry/**` — mock scenario result-text fixtures (a `*` cannot cross a `/`, so files nested under this directory need their own pattern; see below)
- `pkg/java/src/main/java/spade/utility/mcp/**` — the MCP client and server Java source

### Updating this list

Add a new path when you introduce a file or directory under one of these areas that the existing patterns won't already cover:

- A new source directory under `spade.utility.mcp` (client, server, or a new package) is already covered by the `pkg/java/src/main/java/spade/utility/mcp/**` wildcard — no change needed.
- A new top-level `cfg/spade.utility.mcp.<something>.config` file is already covered by `cfg/spade.utility.mcp.*` — no change needed, **as long as it's a single path segment** (a `*` doesn't match `/`). A new *directory* under `cfg/` with a `spade.utility.mcp.` prefix (like the mock scenario Registry's result-text directory) needs its own explicit `<dir>/**` entry, since directory contents are nested paths.
- A new MCP tool definition under `cfg/mcp/` is already covered by `cfg/mcp/**` — no change needed.
- Anything genuinely outside these trees (e.g. a shared utility class the MCP code happens to depend on, or a config file with an unrelated naming scheme) needs an explicit new line.

When in doubt, prefer adding a path over relying on a broader wildcard — this filter's job is precision (skip irrelevant runs), not recall (catch every conceivable dependency); `ci.yml`'s unfiltered build+test still runs on every push regardless.
