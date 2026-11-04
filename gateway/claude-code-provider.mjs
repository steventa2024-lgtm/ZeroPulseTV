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

function run(executable, args, options, input) {
  return new Promise((resolve) => {
    const child = execFile(executable, args, options, (error, stdout) => {
      resolve({ failed: !!error, stdout });
    });
    // A missing binary or early exit can close stdin before the write completes.
    child.stdin.on("error", () => {});
    child.stdin.end(input);
  });
}

function cliInput(messages) {
  // Escape file mentions and command prefixes even inside a question. Only data travels on stdin.
  return JSON.stringify(messages).replace(/[@!/]/g, c => "\\u" + c.charCodeAt(0).toString(16).padStart(4, "0"));
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

  async answer(messages, signal) {
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
      const result = await this.#run(this.#executable, flags,
        { cwd: dir, env, signal, timeout: 60000, maxBuffer: 256 * 1024, windowsHide: true, shell: false }, input);
      let reply;
      try { reply = JSON.parse(result.stdout); } catch { /* failed executable or incompatible version */ }
      if (reply?.api_error_status === 429) {
        this.#state = "rate_limited";
        throw new Error("Provider rate limit reached. Try again later.");
      }
      if (result.failed || reply?.type !== "result" || reply.subtype !== "success" || reply.is_error !== false
          || reply.api_error_status || reply.local_command || typeof reply.result !== "string" || !reply.result.trim()) {
        this.#state = "unavailable";
        throw new Error("Claude Code unavailable. Check the CLI version and API access on the PC.");
      }
      this.#state = "available";
      return { text: reply.result.slice(0, 8000), usage: reply.usage ?? {} };
    } finally {
      // dir was created above for this request; abort/timeout waits for the child before cleanup.
      await rm(dir, { recursive: true, force: true });
    }
  }

  /** Model object for a TV-only OpenPulse Runtime modelFactory. Other model factories stay independent. */
  asTvModel() {
    const answer = async ({ prompt, tools, abortSignal }) => {
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
      const { text, usage } = await this.answer(messages, abortSignal);
      return { text, usage: { inputTokens: { total: usage.input_tokens }, outputTokens: { total: usage.output_tokens } } };
    };
    return {
      specificationVersion: "v4", provider: "zeropulse-tv", modelId: "claude-code", supportedUrls: {},
      async doGenerate(options) {
        const { text, usage } = await answer(options);
        return { content: [{ type: "text", text }], finishReason: { unified: "stop", raw: "stop" }, usage, warnings: [] };
      },
      async doStream(options) {
        const { text, usage } = await answer(options);
        return { stream: new ReadableStream({ start(controller) {
          for (const part of [{ type: "stream-start", warnings: [] }, { type: "text-start", id: "tv" },
            { type: "text-delta", id: "tv", delta: text }, { type: "text-end", id: "tv" },
            { type: "finish", finishReason: { unified: "stop", raw: "stop" }, usage }]) controller.enqueue(part);
          controller.close();
        } }) };
      }
    };
  }
}
