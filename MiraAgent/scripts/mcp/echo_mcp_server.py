#!/usr/bin/env python3
"""A minimal, dependency-free MCP server speaking JSON-RPC 2.0 over stdio.

Implements the subset MiraAgent uses: initialize, notifications/initialized,
tools/list, tools/call. Exposes three demo tools (echo / add / long_text).
Runs offline — used both as MiraAgent's MCP integration-test fixture and as a
ready-to-run example server (point mira.mcp at it via `python3 echo_mcp_server.py`).
"""
import json
import sys

PROTOCOL_VERSION = "2024-11-05"

TOOLS = [
    {
        "name": "echo",
        "description": "Echo back the given message.",
        "inputSchema": {
            "type": "object",
            "properties": {"message": {"type": "string"}},
            "required": ["message"],
        },
    },
    {
        "name": "add",
        "description": "Add two numbers and return the sum.",
        "inputSchema": {
            "type": "object",
            "properties": {"a": {"type": "number"}, "b": {"type": "number"}},
            "required": ["a", "b"],
        },
    },
    {
        "name": "long_text",
        "description": "Return a long block of text (for artifact externalization).",
        "inputSchema": {
            "type": "object",
            "properties": {"lines": {"type": "integer"}},
            "required": ["lines"],
        },
    },
]


def call_tool(name, args):
    if name == "echo":
        return {"content": [{"type": "text", "text": "echo: " + str(args.get("message", ""))}]}
    if name == "add":
        total = (args.get("a", 0) or 0) + (args.get("b", 0) or 0)
        return {"content": [{"type": "text", "text": str(total)}]}
    if name == "long_text":
        n = int(args.get("lines", 1) or 1)
        body = "\n".join(f"line {i}: lorem ipsum dolor sit amet" for i in range(n))
        return {"content": [{"type": "text", "text": body}]}
    return {
        "content": [{"type": "text", "text": "unknown tool: " + str(name)}],
        "isError": True,
    }


def handle(req):
    method = req.get("method")
    if method == "initialize":
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "echo-mcp-server", "version": "0.1.0"},
        }
    if method == "tools/list":
        return {"tools": TOOLS}
    if method == "tools/call":
        params = req.get("params", {})
        return call_tool(params.get("name"), params.get("arguments", {}))
    raise ValueError("unknown method: " + str(method))


def main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except json.JSONDecodeError:
            continue
        # notifications carry no id and expect no response
        if "id" not in req:
            continue
        resp = {"jsonrpc": "2.0", "id": req["id"]}
        try:
            resp["result"] = handle(req)
        except Exception as e:  # noqa: BLE001
            resp["error"] = {"code": -32603, "message": str(e)}
        sys.stdout.write(json.dumps(resp) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
