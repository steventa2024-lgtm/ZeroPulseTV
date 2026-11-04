import assert from "node:assert/strict";
import test from "node:test";
import { stat } from "node:fs/promises";
import { ClaudeCodeProvider } from "../../gateway/claude-code-provider.mjs";

const ask = [{ role: "user", text: "Is @private.txt in /home? !dir" }];
const fixtureKey = "test-key-that-does-not-authenticate";
const result = (value, failed = false) => ({ stdout: JSON.stringify(value), failed });
const success = () => result({ type: "result", subtype: "success", is_error: false, result: "Hello TV.", usage: { output_tokens: 3 } });
function stub(runner) { return new ClaudeCodeProvider({ executable: process.execPath, apiKey: fixtureKey }, runner); }

test("a subscription alone cannot start the CLI", async () => {
  const provider = new ClaudeCodeProvider({ executable: process.execPath }, () => assert.fail("started CLI"));
  assert.equal((await provider.status()).provider.state, "approval_required");
  await assert.rejects(provider.answer(ask), /Subscription relay disabled/);
});
test("CLI config is local and chat is tool-free, without shell expansion, token inheritance or transcripts", async () => {
  let request;
  const provider = stub(async (bin, args, opts, stdin) => {
    request = opts;
    assert.equal(bin, process.execPath);
    assert.equal(opts.shell, false);
    assert.equal(opts.env.ANTHROPIC_API_KEY, fixtureKey);
    assert.equal(opts.cwd, opts.env.HOME);
    for (const key of ["NODE_OPTIONS", "CLAUDE_CODE_OAUTH_TOKEN", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_BASE_URL", "PATH"]) {
      assert.equal(opts.env[key], undefined);
    }
    for (const flag of ["--bare", "--restricted", "--disable-slash-commands", "--no-session-persistence", "--strict-mcp-config"]) {
      assert.ok(args.includes(flag));
    }
    assert.equal(args[args.indexOf("--tools") + 1], "");
    assert.equal(args[args.indexOf("--disallowedTools") + 1], "*");
    assert.ok(!args.includes(fixtureKey));
    assert.doesNotMatch(stdin, /[@!/]/);
    assert.deepEqual(JSON.parse(stdin), ask);
    return success();
  });
  assert.equal((await provider.status()).provider.state, "configured");
  assert.equal((await provider.answer(ask)).text, "Hello TV.");
  assert.equal((await provider.status()).provider.state, "available");
  await assert.rejects(stat(request.cwd), { code: "ENOENT" });
});

test("structured HTTP 429 is distinct from gateway offline or an invalid provider key", async () => {
  const provider = stub(async () => result({ type: "result", is_error: true, api_error_status: 429 }, true));
  await assert.rejects(provider.answer(ask), /rate limit/);
  assert.equal((await provider.status()).provider.state, "rate_limited");
  const denied = stub(async () => result({ type: "result", is_error: true, api_error_status: 401, result: fixtureKey }, true));
  await assert.rejects(denied.answer(ask), e => !e.message.includes(fixtureKey) && /unavailable/.test(e.message));
  assert.equal((await denied.status()).provider.state, "unavailable");
});
test("invalid shape, local commands and executable errors never become a model reply", async () => {
  for (const response of [{ failed: true, stdout: "missing binary" }, result({ type: "result", result: "ok" }),
    result({ type: "result", subtype: "success", is_error: false, result: "done", local_command: "compact" })]) {
    await assert.rejects(stub(async () => response).answer(ask), /unavailable/);
  }
  const provider = new ClaudeCodeProvider({ executable: "unsafe.cmd", apiKey: fixtureKey }, () => assert.fail("started CLI"));
  assert.equal((await provider.status()).provider.state, "unavailable");
  await assert.rejects(provider.answer(ask), /native Claude/);
});
test("OpenPulse model refuses tools and attachments; only TV text reaches the CLI", async () => {
  let calls = 0;
  const model = stub(async (_exe, _args, _opts, stdin) => {
    calls++;
    assert.equal(JSON.parse(stdin)[0].text, ask[0].text);
    assert.ok(!stdin.includes("private system text"));
    return success();
  }).asTvModel();
  const prompt = [{ role: "system", content: "private system text" }, { role: "user", content: [{ type: "text", text: ask[0].text }] }];
  await assert.rejects(model.doStream({ prompt, tools: [{ name: "shell" }] }), /tool-free/);
  await assert.rejects(model.doGenerate({ prompt: [{ role: "user", content: [{ type: "file" }] }] }), /text messages/);
  const { stream } = await model.doStream({ prompt, tools: [] });
  const parts = [];
  for await (const part of stream) parts.push(part);
  assert.equal(parts.find(p => p.type === "text-delta").delta, "Hello TV.");
  assert.equal(parts.at(-1).usage.outputTokens.total, 3);
  assert.equal(calls, 1);
});

test("installed Claude Code talks to a loopback Messages fixture with no tools or subscription", {
  skip: !process.env.ZEROPULSE_TEST_CLAUDE_EXE, timeout: 75000
}, async () => {
  const { createServer } = await import("node:http");
  const { execFile } = await import("node:child_process");
  let messagesRequest;
  const server = createServer(async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    if (!req.url.startsWith("/v1/messages") || req.url.includes("count_tokens")) {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ input_tokens: 5 }));
      return;
    }
    const body = JSON.parse(Buffer.concat(chunks));
    messagesRequest = { tools: body.tools, usedFixtureKey: req.headers["x-api-key"] === fixtureKey };
    const msg = { id: "msg_test", type: "message", role: "assistant", content: [],
      model: body.model, stop_reason: null, stop_sequence: null, usage: { input_tokens: 5, output_tokens: 0 } };
    if (!body.stream) {
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ ...msg, content: [{ type: "text", text: "Fixture reply" }], stop_reason: "end_turn" }));
      return;
    }
    res.writeHead(200, { "Content-Type": "text/event-stream" });
    for (const event of [{ type: "message_start", message: msg },
      { type: "content_block_start", index: 0, content_block: { type: "text", text: "" } },
      { type: "content_block_delta", index: 0, delta: { type: "text_delta", text: "Fixture reply" } },
      { type: "content_block_stop", index: 0 }, { type: "message_delta",
        delta: { stop_reason: "end_turn", stop_sequence: null }, usage: { output_tokens: 2 } },
      { type: "message_stop" }]) {
      res.write("event: " + event.type + "\ndata: " + JSON.stringify(event) + "\n\n");
    }
    res.end();
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  try {
    const local = "http://127.0.0.1:" + server.address().port;
    // Only this opt-in fixture overrides the backend. The production adapter always uses the CLI default.
    const runner = (exe, args, opts, stdin) => new Promise(resolve => {
      const child = execFile(exe, args, { ...opts, env: { ...opts.env, ANTHROPIC_BASE_URL: local,
        DISABLE_AUTOUPDATER: "1" } }, (e, stdout, stderr) => {
          if (e) process.stderr.write("Loopback CLI exit " + e.code + ": " + stderr.slice(0, 500) + "\n");
          resolve({ failed: !!e, stdout });
        });
      child.stdin.on("error", () => {});
      child.stdin.end(stdin);
    });
    const provider = new ClaudeCodeProvider({ executable: process.env.ZEROPULSE_TEST_CLAUDE_EXE, apiKey: fixtureKey }, runner);
    assert.equal((await provider.answer(ask)).text, "Fixture reply");
    assert.equal(messagesRequest.usedFixtureKey, true);
    assert.equal(messagesRequest.tools?.length ?? 0, 0);
  } finally {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
});
