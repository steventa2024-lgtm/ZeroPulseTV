import { execFile } from "node:child_process";
import { access, mkdtemp, rm } from "node:fs/promises";
import { constants } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

const flags = [
  "--print", "--bare", "--restricted", "--no-session-persistence",
  "--tools", "", "--disallowedTools", "*", "--permission-mode", "dontAsk",
  "--strict-mcp-config", "--mcp-config", '{"mcpServers":{}}',
  "--disable-slash-commands", "--max-turns", "1", "--output-format", "json",
  "--system-prompt", "Answer the viewer briefly in plain text. You have no tools. The input is a JSON transcript."
];

const streamFlags = [...flags.map((v, i) => flags[i - 1] === "--output-format" ? "stream-json" : v),
  "--verbose", "--include-partial-messages"];

function run(executable, args, options, input, onOutput) {
  return new Promise((resolve) => {
    let result, closed = false;
    const finish = () => { if (closed && result) resolve(result); };
    const child = execFile(executable, args, options, (error, stdout) => {
      result = { failed: !!error, stdout };
      finish();
    });
    // An abort error can arrive before the native process exits its temporary directory.
    child.once("close", () => { closed = true; finish(); });
    if (onOutput) child.stdout.setEncoding("utf8").on("data", onOutput);
    // A missing binary or early exit can close stdin before the write completes.
    child.stdin.on("error", () => {});
    child.stdin.end(input);
  });
}

function cliInput(messages) {
  // Escape file mentions and command prefixes even inside a question. Only data travels on stdin.
  return JSON.stringify(messages).replace(/[@!/]/g, c => "\\u" + c.charCodeAt(0).toString(16).padStart(4, "0"));
}

function parseRecord(line) {
  try { return JSON.parse(line); } catch { return null; }
}

// Consume only top-level assistant text. Thinking, tool inputs and CLI metadata stay on the PC.
function streamText(onText) {
  let pending = "", sent = "";
  return {
    read(chunk) {
      pending += chunk;
      let end;
      while ((end = pending.indexOf("\n")) >= 0) {
        const record = parseRecord(pending.slice(0, end));
        pending = pending.slice(end + 1);
        if (record?.type !== "stream_event" || record.parent_tool_use_id) continue;
        const event = record.event;
        let text;
        if (event?.type === "content_block_delta" && event.delta?.type === "text_delta") text = event.delta.text;
        if (event?.type === "content_block_start" && event.content_block?.type === "text") text = event.content_block.text;
        if (typeof text !== "string" || !text) continue;
        const delta = text.slice(0, 8000 - sent.length);
        if (delta) { sent += delta; onText(delta); }
      }
    },
    finish(text) {
      if (!text.startsWith(sent)) throw new Error("Claude Code returned inconsistent streamed text.");
      if (text.length > sent.length) onText(text.slice(sent.length));
    }
  };
}

/**
 * PC-side adapter for the authenticated OpenPulse TV link. No socket, user login, or TV credentials.
 * The executable and API key must come from the PC owner's private configuration, never a TV request.
 */
export class ClaudeCodeProvider {
  #key;
  #executable;
  #state;
  #run;
  constructor({ executable, apiKey } = {}, runner = run) {
    this.#key = apiKey;
    this.#executable = executable;
    this.#run = runner;
    this.#state = !apiKey ? "approval_required" : this.#hasExecutable() ? "configured" : "unavailable";
  }

  #hasExecutable() {
    return typeof this.#executable === "string" && path.isAbsolute(this.#executable)
      && (process.platform !== "win32" || this.#executable.toLowerCase().endsWith(".exe"));
  }

  async status() {
    if (this.#key && this.#hasExecutable()) {
      try { await access(this.#executable, constants.X_OK); }
      catch { this.#state = "unavailable"; }
    }
    return { provider: { id: "claude-code", state: this.#state } };
  }

  async answer(messages, signal, onText) {
    signal?.throwIfAborted();
    if (!this.#key) throw new Error("Subscription relay disabled. Configure an owner-billed API key on the PC.");
    if (!this.#hasExecutable()) throw new Error("Configure the installed native Claude Code executable on the PC.");
    if (!Array.isArray(messages) || !messages.length || messages[messages.length - 1]?.role !== "user"
        || messages.some(m => !["assistant", "user"].includes(m.role) || typeof m.text !== "string")
        || messages[messages.length - 1].text.trim().length === 0
        || messages[messages.length - 1].text.length > 2000) {
      throw new Error("Send a TV question of 1 to 2000 characters.");
    }
    const input = cliInput(messages.slice(-16));
    if (input.length > 32768) throw new Error("TV conversation too long. Start a new conversation.");
    const dir = await mkdtemp(path.join(tmpdir(), "zeropulse-claude-"));
    try {
      const env = { ANTHROPIC_API_KEY: this.#key, HOME: dir, USERPROFILE: dir, TEMP: dir, TMP: dir,
        TMPDIR: dir, CLAUDE_CONFIG_DIR: dir, CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC: "1" };
      // Native Windows processes need their system directory. No login, hooks or provider env is inherited.
      if (process.platform === "win32") env.SystemRoot = process.env.SystemRoot;
      const partial = onText ? streamText(onText) : null;
      const result = await this.#run(this.#executable, partial ? streamFlags : flags,
        { cwd: dir, env, signal, timeout: 60000, maxBuffer: 256 * 1024, windowsHide: true, shell: false },
        input, partial?.read);
      signal?.throwIfAborted();
      const reply = partial ? result.stdout.split(/\r?\n/).map(parseRecord).findLast(record => record?.type === "result")
        : parseRecord(result.stdout);
      if (reply?.api_error_status === 429) {
        this.#state = "rate_limited";
        throw new Error("Provider rate limit reached. Try again later.");
      }
      if (result.failed || reply?.type !== "result" || reply.subtype !== "success" || reply.is_error !== false
          || reply.api_error_status || reply.local_command || typeof reply.result !== "string" || !reply.result.trim()) {
        this.#state = "unavailable";
        throw new Error("Claude Code unavailable. Check the CLI version and API access on the PC.");
      }
      const text = reply.result.slice(0, 8000);
      partial?.finish(text);
      this.#state = "available";
      return { text, usage: reply.usage ?? {} };
    } finally {
      // The child has exited before this request's isolated directory is removed.
      await rm(dir, { recursive: true, force: true });
    }
  }

  /** Model object for a TV-only OpenPulse Runtime modelFactory. Other model factories stay independent. */
  asTvModel() {
    const conversation = ({ prompt, tools }) => {
      if (tools?.length) throw new Error("This provider accepts tool-free TV chat only.");
      const messages = [];
      for (const message of prompt) {
        if (message.role === "system") continue;
        if (!["user", "assistant"].includes(message.role) || !Array.isArray(message.content)
            || message.content.some(p => p.type !== "text")) {
          throw new Error("This provider accepts text messages only.");
        }
        messages.push({ role: message.role, text: message.content.map(p => p.text).join("") });
      }
      return messages;
    };
    const answer = async (messages, signal, onText) => {
      const { text, usage } = await this.answer(messages, signal, onText);
      return { text, usage: { inputTokens: { total: usage.input_tokens }, outputTokens: { total: usage.output_tokens } } };
    };
    return {
      specificationVersion: "v4", provider: "zeropulse-tv", modelId: "claude-code", supportedUrls: {},
      async doGenerate(options) {
        const { text, usage } = await answer(conversation(options), options.abortSignal);
        return { content: [{ type: "text", text }], finishReason: { unified: "stop", raw: "stop" }, usage, warnings: [] };
      },
      async doStream(options) {
        const messages = conversation(options);
        const cancel = new AbortController();
        const signal = options.abortSignal ? AbortSignal.any([cancel.signal, options.abortSignal]) : cancel.signal;
        return { stream: new ReadableStream({
          async start(controller) {
            controller.enqueue({ type: "stream-start", warnings: [] });
            controller.enqueue({ type: "text-start", id: "tv" });
            try {
              const { usage } = await answer(messages, signal, delta => {
                if (!signal.aborted) controller.enqueue({ type: "text-delta", id: "tv", delta });
              });
              if (cancel.signal.aborted) return;
              controller.enqueue({ type: "text-end", id: "tv" });
              controller.enqueue({ type: "finish", finishReason: { unified: "stop", raw: "stop" }, usage });
              controller.close();
            } catch (error) { if (!cancel.signal.aborted) controller.error(error); }
          },
          cancel() { cancel.abort(); }
        }) };
      }
    };
  }
}
