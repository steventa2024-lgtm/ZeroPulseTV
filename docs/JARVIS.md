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

3. Open **Assistant → Connect to JARVIS** and enter the PC's IPv4 address, the port and the code.
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
