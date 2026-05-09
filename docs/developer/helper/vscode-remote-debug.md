# Developer Helper How-To

## Remote debugging

Build with debug symbols:

```bash
mvn compile -Dmaven.compiler.debug=true -Dmaven.compiler.debuglevel=lines,vars,source
```

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
        "${workspaceFolder}/src",
        "${workspaceFolder}/src/test/java"
    ]
}
```

Attach from VS Code using the **Attach SPADE (remote)** configuration (Run → Start Debugging).
