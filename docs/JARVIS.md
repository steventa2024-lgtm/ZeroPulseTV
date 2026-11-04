# JARVIS on ZeroPulse TV (optional, experimental)

JARVIS lets you type questions on the TV and get answers from the AI assistant running in **your own**
OpenPulse gateway on a PC on your home network. Nothing about it is required for the launcher to work.

- No shared server, no account, no bundled language model, no paid API key needed by the TV.
- Answers come from the model your OpenPulse gateway is configured with (for example a local Ollama model).
- From the TV, JARVIS can only chat. It cannot read files, run commands or change settings on the PC.
- Voice input is not implemented in 2.1.

> **Availability:** JARVIS needs an OpenPulse gateway that includes the **TV link**. At the time of
> v2.1.0-beta.1 the TV link is not yet in a public OpenPulse release, so JARVIS cannot be paired with a public
> OpenPulse build yet. The TV side is complete and documented here.

## Setup

On the PC (OpenPulse with the TV link):

1. In OpenPulse's config, enable the TV link and allow only your TV's address:
   ```json5
   gateway: { tvLink: { enabled: true, host: "0.0.0.0", port: 18800, allowFrom: ["<tv-ip>/32"] } }
   ```
   Restart the gateway. Consider a DHCP reservation for the TV and the PC in your router, and a firewall rule
   that allows TCP 18800 only from the TV.
2. Run `openpulse tv invite`. It shows the PC's address, the port and a one-time code
   `XXXX-XXXX-XXXX-XXXX` (valid 5 minutes, single use).

On the TV:

3. Open **Assistant → Connect your PC** and enter the PC's IPv4 address, the port and the code.
4. The TV checks the PC's certificate against the code and shows **Waiting for approval — TV code XXXX-XXXX**.
5. On the PC, approve the request whose TV code matches (`openpulse tv invite` asks; or
   `openpulse devices approve <requestId>`).
6. The Assistant shows **JARVIS connected**. Type a question in the command box. TV commands such as
   "open YouTube" still run on the TV directly.

## Everyday behaviour

- The TV connects only while the Assistant screen is open and disconnects when you leave it.
- If the PC is off or OpenPulse is stopped, the Assistant shows **Offline** and retries after 3, 8 and 20 seconds,
  then waits for **Retry**. Home and local commands are unaffected.
- **Disconnect** closes the link but keeps the pairing. **Forget this PC** removes the pairing on the PC (if
  reachable) and deletes the TV's key, token and pin.
- If the PC's certificate changes (`openpulse tv rotate-cert`), the TV refuses it and shows
  **PC identity changed**; enter a new code under **Re-verify PC**.
- If the TV is removed on the PC (`openpulse devices remove <id>`), the TV shows that it was removed and deletes
  its credentials.

## Protocol summary (TV link)

| Item | Value |
|---|---|
| Transport | `wss://<pc-ipv4>:<port>/tv`, **TLS 1.3 only**; no plaintext fallback exists in the client |
| PC identity | Self-signed ECDSA P-256 certificate, pinned by SHA-256 of its DER encoding |
| Pairing code | 16 Crockford base32 characters: 8-character secret (40 bits) + 8-character check = first 40 bits of `HMAC-SHA256(secret, "zeropulse-tv-pin|v1|" + certSha256Hex)` |
| Trust decision | Made inside the TLS handshake: pairing mode compares the check value, later connections compare the pin. A mismatch aborts before any request is sent |
| TV identity | ECDSA P-256 key in Android Keystore; device id = first 32 hex characters of SHA-256(SPKI) |
| Connect proof | Signature (IEEE P1363) over `openpulse-connect|v1|<nonce>|<signedAt>|<clientId>|tv` |
| Credentials | First connection: pairing secret → owner approval → device token. Later: device token. Token stored AES-256-GCM encrypted under a Keystore key |
| Methods | `tv.status`, `tv.chat.send`, `tv.chat.history`, `tv.chat.abort`, `tv.chat.reset`, `tv.unpair` |
| Events | `chat` and `agent` (lifecycle) for the TV's own conversation, `tick`, `shutdown` |

Implementation: `launcher/src/tv/calmhome/launcher/jarvis/` (protocol, pinning, WebSocket, session policy) and
`launcher/src/tv/calmhome/launcher/assistant/` (Keystore storage, UI). Tests: `qa/tests/tv/calmhome/launcher/jarvis/`.

## Beta 3 PC connection

Open **Assistant → Connect your PC**. Make an invitation with the compatible OpenPulse TV link on the PC, enter its address, port and invite code, then compare the TV code before approving the device on the PC. Android Keystore protects the device key and pairing token. Forgetting a PC removes the local credentials and requests remote removal when connected. An offline PC can remove the device later. Local TV commands work during setup, disconnection and provider outages.

The PC pairing row shows the saved relationship. The gateway row shows the current connection and whether the PC denied access. A valid pinned connection establishes the identity of the gateway. Only that gateway can report the optional provider capability. A legacy PC can continue its usual chat while the Claude Code row reads **Not reported**.

| Claude Code row | Meaning |
|---|---|
| Requires PC link | No authenticated connection to query |
| Not reported | This gateway supplied no recognized Claude Code status |
| Configured on PC | The owner configured the adapter. A model call has yet to succeed |
| Available | The last tool-free model call returned text successfully |
| Unavailable | Executable, provider authentication, model service or result failed |
| Rate limit reached | The CLI reported a provider HTTP 429 |
| Subscription relay disabled | API access was not configured. The subscription relay is gated |

### Owner-operated adapter

The optional module in **gateway/claude-code-provider.mjs** exports a PC-only model adapter for a compatible private OpenPulse gateway. It opens no network listener. This beta does not ship OpenPulse or a hosted service. Install the unmodified native Claude Code CLI on the PC. For the owner's normal personal sessions, use Anthropic's own CLI/browser sign-in on the PC. The adapter supports **owner-billed API key mode only**. A signed-in CLI or a Pro/Max account alone does not enable it. The beta has no override to relay subscription credentials.

Anthropic permits a user to sign into its unmodified client. Developers integrating Claude into their own apps should use API credentials or a supported cloud provider. Third-party subscription login or routing needs prior authorization for this design. The PC owner must confirm any approval with Anthropic before enabling a future subscription-backed connector. [Claude Code legal and compliance](https://code.claude.com/docs/en/legal-and-compliance) · [Agent SDK overview](https://code.claude.com/docs/en/agent-sdk/overview) · [PC authentication](https://code.claude.com/docs/en/authentication)

On the PC, select a dedicated TV-link runtime with model ID **zeropulse-tv/claude-code**. Its existing **tv.chat.send** continues to enforce device pairing, rate limits, isolation of per-TV sessions and the empty tool set. The gateway integrator can use the following wiring at the runtime factory and existing authenticated status handler. Load the adapter from its local file on the owner's PC. Supply the executable path and owner-billed API key from the gateway's private process configuration. Passwords, CLI sessions, API keys and the owner's gateway management token must never be sent to the TV.

~~~js
const provider = new ClaudeCodeProvider({
  executable: process.env.ZEROPULSE_CLAUDE_EXE,
  apiKey: process.env.ZEROPULSE_CLAUDE_API_KEY
});
const modelFactory = (ref, config) =>
  ref.provider === "zeropulse-tv" && ref.model === "claude-code"
    ? provider.asTvModel()
    : createModel(ref, config);

// Pass modelFactory to Runtime.create. Retain the gateway's original ACLs and chat handlers.
const originalStatus = TV_METHODS["tv.status"];
TV_METHODS["tv.status"] = async (rt, params, conn) => {
  const status = await originalStatus(rt, params, conn);
  return status.model === "zeropulse-tv/claude-code"
    ? { ...status, ...await provider.status() }
    : status;
};
~~~

The model accepts only user/assistant text from a tool-free TV session. It starts a one-shot CLI request with restricted mode, zero tools, no MCP servers, no slash commands, an isolated working/config directory, no transcript retention and no shell interpolation. The runner passes a PC-supplied API key to that child. Model output stays text. Existing allowlisted TV suggestions still need the viewer to select them. Limits include up to 2,000 characters per question and a 60-second subprocess timeout. An incompatible CLI fails closed. [CLI reference](https://code.claude.com/docs/en/cli-reference) · [Result error fields](https://code.claude.com/docs/en/agent-sdk/typescript) · [API error statuses](https://platform.claude.com/docs/en/api/errors)

**Validation.** Run **node --test qa/tests/claude-code-provider.test.mjs** for credential-free adapter checks. Setting **ZEROPULSE_TEST_CLAUDE_EXE** to a local native CLI path also runs a loopback API fixture with a synthetic key. The existing **JarvisIntegrationTests** suite uses a compatible private gateway with ephemeral credentials and a scripted model. These checks do not authenticate an Anthropic subscription.

**Recovery.** If the provider fails, continue using local commands or restore the gateway's previous model factory and restart the PC's TV link. If the pin changes, compare a fresh invitation and re-verify. If you suspect unauthorized TV access, revoke the TV on the PC, forget it on the TV, and rotate the PC-side API key separately if it was exposed.
