# Assistant, Claude and voice

Open **Assistant** in the navigation bar or use its shortcut in Downloads. **Send** matches built-in commands first. “Open Apps,” “open Downloads,” “show my downloads,” “open Settings” and “show my installed applications” navigate locally. “Open YouTube” works when the TV has a matching launchable app. Other text is sent to the PC only while the gateway connection is authenticated.

## PC pairing and Claude

Connect your PC uses the existing invitation, pinned TLS link, owner approval and Keystore-backed TV identity. Compare the device code on the PC before approving. A saved pairing can coexist with an offline gateway. A connected gateway may report an unknown provider. The Claude Code row distinguishes configured, available, unavailable, rate-limited and subscription relay disabled. A model name alone cannot establish readiness. Re-verify a changed pin with a fresh invitation. Forgetting the PC clears its local pairing and chat. [Pairing and PC adapter setup](JARVIS.md)

The unmodified CLI supports noninteractive operation and a restricted, API-key-authenticated bare mode. Anthropic’s terms distinguish personal Claude Code use from a third-party product integration. The support article describes how signed-in CLI or SDK use counts against a plan. It does not authorize a TV app to collect subscriptions or route requests for viewers using those credentials. ZeroPulse keeps subscription relay disabled. The optional PC adapter accepts a separately configured owner-billed credential, invokes the CLI without tools in an isolated work directory and returns text. It does not open a listener or collect browser credentials. Only the authenticated gateway handles TV requests. [Claude Code legal and compliance](https://code.claude.com/docs/en/legal-and-compliance) · [Agent SDK plan usage](https://support.claude.com/en/articles/15036540-use-the-claude-agent-sdk-with-your-claude-plan) · [Programmatic CLI](https://code.claude.com/docs/en/headless)

## Conversation controls

The authenticated gateway can supply remote history, running status, streaming text snapshots and completed replies. Stop response invokes its scoped cancellation method. The TV distinguishes a confirmed abort from a request that reached the PC after generation ended. A safe app or input suggestion becomes a button and requires selection. Chat cannot approve installations or access PC files or a shell.

The optional PC adapter reads the native CLI’s partial text events as they arrive. It forwards top-level text only and checks the final CLI result before reporting completion. Canceling the stream signals the CLI process.

The TV retains at most 30 text turns in app-private, backup-excluded storage. Focus the transcript to scroll. Clear TV history deletes the local text and suppresses re-import of older remote turns. The PC keeps its copy until the viewer confirms New PC conversation. A confirmed empty PC history begins a fresh thread.

## Voice

Select Talk to launch Android’s recognition activity. The launcher checks for a compatible handler and offers text input if none exists. Android’s recognizer may process speech online. Review or edit the transcription before selecting Send. The OS owns capture and its microphone controls. The launcher does not request RECORD_AUDIO or listen in the background. Speak replies is off by default. When enabled, it uses Android TTS and audio focus. Stop speaking ends playback. Only newly completed replies are spoken. History, partial output and connection status are never read aloud. [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent) · [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)

## Acceptance boundaries

With Speak replies enabled, select Preview voice to hear a local sample. It does not contact the PC, add a transcript turn or represent a model reply.

After installing the approved build, run `node qa/assistant-device-smoke.mjs SERIAL` to check editable typing, both local commands, navigation and the saved pairing indicator. Select a specific device. The optional `--tts-preview` check restores the original preference after checking engine completion and Stop. `--recognizer-cancel` and `--recognizer-dictate` explicitly launch the Android recognizer. Run the latter only with the viewer ready to speak. The script never sends the recognized draft. Screenshots and results stay under the ignored private evidence folder.

The host tests cover bounded local history, offline clear, provider states and safe commands. Real loopback integration uses a pinned gateway, ephemeral pairing and a scripted model to test the two-way protocol, stop acknowledgements and reset. The native CLI fixture uses a synthetic local Messages API without live billing. A live authenticated Claude response requires authorized PC configuration. No real Claude subscription or paid API call was used for this build. A recognizer or TTS package being installed does not verify microphone audio or audible output at the viewer’s seat. [Physical acceptance](../qa/GRAPHITE_V3_TV_ACCEPTANCE.md)
