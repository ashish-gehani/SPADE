# Developer Helper How-To

## Remote debugging

Build with debug symbols by passing `JAVA_DEBUG=1` to `configure` (run from `module/java/`):

```bash
cd module/java && JAVA_DEBUG=1 ./configure SPADE_ROOT=../../ && make
```

`JAVA_DEBUG=1` causes `configure` to bake `-Dmaven.compiler.debug=true -Dmaven.compiler.debuglevel=lines,vars,source` into the generated `Makefile`, so `make` passes them to Maven automatically.

Start SPADE with `--debug-remote-port`:

```bash
bin/spade start --debug-remote-port 8686
```

Add the following configuration to `.vscode/launch.json`:

```json
{
    "type": "java",
    "name": "Attach SPADE (remote)",
    "request": "attach",
    "hostName": "localhost",
    "port": 8686,
    "sourcePaths": [
        "${workspaceFolder}/src"
    ]
}
```

Attach from VS Code using the **Attach SPADE (remote)** configuration (Run → Start Debugging).
