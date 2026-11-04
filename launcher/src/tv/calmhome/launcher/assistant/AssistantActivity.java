package tv.calmhome.launcher.assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tv.calmhome.launcher.MainActivity;
import tv.calmhome.launcher.core.CommandParser;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.TvInputs;
import tv.calmhome.launcher.jarvis.JarvisSession;
import tv.calmhome.launcher.jarvis.ClaudeCodeStatus;
import tv.calmhome.launcher.jarvis.TvSuggestion;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.Navigation;
import tv.calmhome.launcher.ui.GraphiteBackdrop;
import tv.calmhome.launcher.jarvis.ChatHistory;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * ZeroPulse Assistant: local TV commands, explicit push-to-talk and, once paired, a conversation with
 * JARVIS on the PC through the OpenPulse TV link.
 *
 * Local commands work with no PC and are tried first. JARVIS only connects while this screen is visible
 * (JarvisSession: bounded timeout, three retries, then Offline); Home never waits for it. Every status
 * shown comes from the real link state. JARVIS can suggest a TV action, which appears as a button and runs
 * only through the TV's own command matching when the viewer presses OK.
 */
public class AssistantActivity extends Activity implements JarvisSession.Listener {

    private AssistantVoice voice;
    private TextView mic, voiceStatus, speakToggle, silence, stopReply, send;
    private ChatHistory history;
    private SharedPreferences localChat;
    private boolean responding, visible, pairingNewPc;

    private Prefs prefs;
    private Theme t;
    private Widgets w;
    private Apps apps;
    private ReactorView reactor;
    private TextView response, stateLabel, chipText;
    private View chipDot;
    private LinearLayout commandList, actions, messages;
    private ScrollView convo;
    private EditText input;
    private Row pcRow, linkRow, keyRow, providerRow;
    private FrameLayout overlay;
    private LinearLayout dashboard;
    private TextView overlayStatus;
    private EditText hostField, portField, codeField;
    private boolean reverifyMode;

    private final Handler main = new Handler(Looper.getMainLooper());
    private KeystoreCredentials creds;
    private JarvisSession session;
    private JarvisSession.State jarvisState = JarvisSession.State.NOT_CONFIGURED;
    private TextView liveReply;
    private String assistantName = "JARVIS";

    private static final class Row {
        View dot;
        TextView value;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        t = new Theme(this, prefs);
        w = new Widgets(this, t);
        apps = new Apps(this);
        creds = new KeystoreCredentials(this);
        localChat = getSharedPreferences("zp_chat_v1", MODE_PRIVATE);
        history = ChatHistory.read(localChat.getString("turns", null));
        voice = new AssistantVoice(this, new AssistantVoice.Listener() {
            @Override public void onVoiceChanged() { renderVoice(); }
            @Override public void onTranscript(String text) {
                input.setText(text);
                input.setSelection(input.length());
                input.requestFocus();
                say("Review your words, then select Send.", false);
            }
        });
        voice.restore(state);
        String name = Settings.Global.getString(getContentResolver(), "device_name");
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { version = "unknown"; }
        session = new JarvisSession(creds, this, name != null && !name.isEmpty() ? name : "ZeroPulse TV", version, 4000);
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.DEEP_BLACK));
        setContentView(build());
        if (state != null) input.setText(state.getString("draft", ""));
        displayHistory();
        render(creds.load() == null ? JarvisSession.State.NOT_CONFIGURED : JarvisSession.State.OFFLINE, "");
        renderVoice();
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        voice.refresh();
        session.start();          // connect only while the Assistant is on screen
    }

    @Override
    protected void onResume() {
        super.onResume();
        reactor.setAnimated(prefs.reactorAnimation() && !t.reducedMotion);
        reactor.setRunning(true);
        if (getCurrentFocus() == null && overlay.getVisibility() != View.VISIBLE) input.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        reactor.setRunning(false);    // no animation work while another app or screen is in front
    }

    @Override
    protected void onStop() {
        super.onStop();
        visible = false;
        voice.onBackground();
        session.stop();           // no network activity once the Assistant is closed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        voice.close();
        session.shutdown();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        voice.onResult(request, result, data);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        voice.save(out);
        out.putString("draft", input.getText().toString());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && overlay.getVisibility() == View.VISIBLE) {
            closeOverlay(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ JarvisSession.Listener (any thread)

    @Override
    public void onState(JarvisSession.State s, String detail) {
        main.post(() -> { if (!isDestroyed()) render(s, detail); });
    }

    @Override
    public void onReply(String state, String text) {
        main.post(() -> { if (!isDestroyed()) reply(state, text); });
    }

    @Override
    public void onProvider(ClaudeCodeStatus status) {
        main.post(() -> {
            if (isDestroyed()) return;
            if (jarvisState != JarvisSession.State.CONNECTED) {
                setRow(providerRow, "Requires PC link", Theme.MUTED_GRAY);
            } else {
                int color = status == ClaudeCodeStatus.AVAILABLE ? Theme.SUCCESS
                        : status == ClaudeCodeStatus.RATE_LIMITED ? Theme.WARNING : Theme.MUTED_GRAY;
                setRow(providerRow, status.label, color);
            }
        });
    }

    @Override
    public void onHistory(List<String[]> list) {
        main.post(() -> {
            if (isDestroyed()) return;
            if (history.syncFromGateway(list)) {
                persistHistory();
                displayHistory();
            }
        });
    }

    // ------------------------------------------------------------------ building

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GraphiteBackdrop());
        dashboard = vertical();
        dashboard.setPadding(t.safeX(), t.safeY(), t.safeX(), t.safeY());
        dashboard.setClipChildren(false);
        root.addView(dashboard, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        dashboard.addView(new Navigation(this, t, w, prefs, "Assistant", tab -> {
            if (!"Assistant".equals(tab)) openPanel(tab.toLowerCase(java.util.Locale.ROOT), "Opening " + tab);
        }), new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = t.text(this, 29, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        title.setText("Assistant");
        heading.addView(title, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1));
        reactor = new ReactorView(this);
        heading.addView(reactor, new LinearLayout.LayoutParams(t.dp(28), t.dp(28)));
        stateLabel = t.text(this, Theme.T_CAPTION, Theme.MUTED_GRAY, Theme.FONT_MEDIUM);
        stateLabel.setPadding(t.dp(4), 0, t.dp(10), 0);
        heading.addView(stateLabel, w.wrap());
        heading.addView(statusChip(), w.wrap());
        dashboard.addView(heading, spaced(7));

        LinearLayout mid = new LinearLayout(this);
        mid.setClipChildren(false);
        LinearLayout thread = vertical();
        thread.setPadding(t.dp(13), t.dp(9), t.dp(13), t.dp(8));
        thread.setBackground(t.glass(Theme.R_PANEL, .78f));
        TextView transcriptTitle = t.text(this, Theme.T_TITLE, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        transcriptTitle.setText("Conversation");
        thread.addView(transcriptTitle);
        convo = new ScrollView(this);
        convo.setFocusable(true);
        convo.setFillViewport(true);
        convo.setContentDescription("Conversation history. Up and down to read.");
        convo.setSmoothScrollingEnabled(!t.reducedMotion);
        convo.setBackground(t.rounded(Theme.withAlpha(Theme.DEEP_BLACK, .35f), 10));
        convo.setOnFocusChangeListener((v, has) -> v.setForeground(w.ring(has, 10)));
        messages = vertical();
        messages.setPadding(t.dp(10), t.dp(6), t.dp(10), t.dp(8));
        convo.addView(messages);
        LinearLayout.LayoutParams historySpace = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        historySpace.topMargin = t.dp(5);
        thread.addView(convo, historySpace);
        response = t.text(this, Theme.T_LABEL, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        response.setMaxLines(2);
        response.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        thread.addView(response, spaced(4));
        mid.addView(thread, new LinearLayout.LayoutParams(0, Widgets.MATCH, 1f));

        LinearLayout conn = vertical();
        actions = vertical();
        conn.addView(actions);
        pcRow = connRow(conn, Glyph.Kind.INPUTS, "PC pairing");
        linkRow = connRow(conn, Glyph.Kind.ASSISTANT, "PC gateway");
        providerRow = connRow(conn, Glyph.Kind.ASSISTANT, "Claude Code on PC");
        setRow(providerRow, "Requires PC link", Theme.MUTED_GRAY);
        keyRow = connRow(conn, Glyph.Kind.INFO, "Device key");
        Row local = connRow(conn, Glyph.Kind.TV, "Local TV commands");
        setRow(local, "Available", Theme.SOFT_WHITE);
        conn.addView(button("Clear TV history", this::confirmClear), spaced(4));
        commandList = vertical();
        buildCommands();
        conn.addView(commandList, spaced(7));
        ScrollView side = new ScrollView(this);
        side.setSmoothScrollingEnabled(!t.reducedMotion);
        side.setFocusable(false);
        side.addView(panel("Connection & commands", conn));
        LinearLayout.LayoutParams sideSize = new LinearLayout.LayoutParams(t.dp(225), Widgets.MATCH);
        sideSize.leftMargin = t.dp(10);
        mid.addView(side, sideSize);
        LinearLayout.LayoutParams midSize = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        midSize.topMargin = t.dp(6);
        dashboard.addView(mid, midSize);

        LinearLayout compose = vertical();
        compose.setPadding(t.dp(11), t.dp(7), t.dp(11), t.dp(7));
        compose.setBackground(t.glass(Theme.R_PANEL, .88f));
        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        input = field("Ask a question or type a TV command", InputType.TYPE_CLASS_TEXT);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean submit = actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN);
            if (submit) runTyped(v.getText().toString());
            return submit;
        });
        convo.setId(View.generateViewId());
        input.setNextFocusUpId(convo.getId());
        composer.addView(input, new LinearLayout.LayoutParams(0, t.dp(40), 1f));
        send = button("Send", () -> runTyped(input.getText().toString()));
        composer.addView(send, pillSize());
        mic = button("Talk", () -> { if (voice.recognizing()) voice.cancelRecognition(); else voice.startRecognition(); });
        android.graphics.drawable.Drawable micGlyph = getResources().getDrawable(android.R.drawable.ic_btn_speak_now, getTheme()).mutate();
        micGlyph.setTint(Theme.SOFT_WHITE);
        micGlyph.setBounds(0, 0, t.dp(21), t.dp(21));
        mic.setCompoundDrawables(micGlyph, null, null, null);
        mic.setCompoundDrawablePadding(t.dp(5));
        composer.addView(mic, pillSize());
        stopReply = button("Stop response", session::abort);
        composer.addView(stopReply, pillSize());
        compose.addView(composer);
        LinearLayout audio = new LinearLayout(this);
        audio.setGravity(Gravity.CENTER_VERTICAL);
        voiceStatus = t.text(this, Theme.T_CAPTION, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        voiceStatus.setMaxLines(2);
        voiceStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        audio.addView(voiceStatus, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
        speakToggle = button("", () -> voice.setSpeakEnabled(!voice.speakEnabled()));
        audio.addView(speakToggle, w.wrap());
        silence = button("Stop speaking", voice::stopSpeaking);
        audio.addView(silence, w.wrap());
        compose.addView(audio, spaced(4));
        dashboard.addView(compose, spaced(8));

        overlay = buildOverlay();
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        return root;
    }

    private LinearLayout vertical() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private LinearLayout.LayoutParams spaced(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        p.topMargin = t.dp(top);
        return p;
    }

    private LinearLayout.LayoutParams pillSize() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(40));
        p.leftMargin = t.dp(6);
        return p;
    }

    private TextView button(String title, Runnable click) {
        TextView b = t.text(this, Theme.T_NAV, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        b.setText(title);
        b.setGravity(Gravity.CENTER);
        b.setPadding(t.dp(10), t.dp(5), t.dp(10), t.dp(5));
        b.setBackground(t.glass(Theme.R_PILL, .88f));
        w.focusable(b, Theme.R_PILL, null, true);
        b.setOnClickListener(v -> click.run());
        return b;
    }

    private void enable(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setFocusable(enabled);
        view.setAlpha(enabled ? 1f : .55f);
    }

    private void renderVoice() {
        if (mic == null || silence == null || voice == null) return;
        mic.setText(voice.recognizing() ? "Cancel" : "Talk");
        mic.setContentDescription(voice.recognizing() ? "Cancel speech recognition" : "Push to talk");
        enable(mic, voice.recognizing() || voice.hasRecognizer());
        String status = voice.status();
        if (voice.hasRecognizer() && !status.contains("online")) status += " Recognition may use an online service.";
        voiceStatus.setText(status);
        speakToggle.setText(voice.speakEnabled() ? "Speak replies on" : "Speak replies off");
        enable(silence, voice.speaking());
    }

    private FrameLayout buildOverlay() {
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.82f));
        scrim.setClickable(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(t.dp(26), t.dp(22), t.dp(26), t.dp(22));
        card.setBackground(t.glass(Theme.R_PANEL, 0.96f));
        TextView title = t.text(this, Theme.T_BODY + 6, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        title.setText("Connect your PC");
        card.addView(title, w.wrap());
        TextView help = t.text(this, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        help.setText("1. On your PC, run openpulse tv invite in an OpenPulse build with the TV link.\n"
                + "2. Enter its address, port and one-time code below.\n"
                + "3. Match the TV code on your PC and approve the request there.");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        hl.topMargin = t.dp(6);
        card.addView(help, hl);
        hostField = field("PC address, e.g. 192.168.4.33", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        portField = field("Port", InputType.TYPE_CLASS_NUMBER);
        codeField = field("Code: XXXX-XXXX-XXXX-XXXX",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        codeField.setSaveEnabled(false);
        codeField.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        LinearLayout addr = new LinearLayout(this);
        addr.addView(hostField, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(t.dp(110), Widgets.WRAP);
        pl.leftMargin = t.dp(10);
        addr.addView(portField, pl);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        fl.topMargin = t.dp(14);
        card.addView(addr, fl);
        LinearLayout.LayoutParams cfl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        cfl.topMargin = t.dp(10);
        card.addView(codeField, cfl);
        overlayStatus = t.text(this, Theme.T_BODY, Theme.HOLO_BLUE, Theme.FONT_REGULAR);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        sl.topMargin = t.dp(12);
        card.addView(overlayStatus, sl);
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(w.chip(Glyph.Kind.ASSISTANT, "Connect", true, v -> submitOverlay()),
                new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(38)));
        LinearLayout.LayoutParams cbl = new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(38));
        cbl.leftMargin = t.dp(12);
        buttons.addView(w.chip(Glyph.Kind.HOME, "Cancel", false, v -> closeOverlay(true)), cbl);
        LinearLayout.LayoutParams bl = w.wrap();
        bl.topMargin = t.dp(14);
        card.addView(buttons, bl);
        scrim.addView(card, new FrameLayout.LayoutParams(t.dp(560), Widgets.WRAP, Gravity.CENTER));
        return scrim;
    }

    // ------------------------------------------------------------------ JARVIS state → UI

    private void render(JarvisSession.State s, String detail) {
        boolean actionHadFocus = actions.hasFocus();
        jarvisState = s;
        if (s != JarvisSession.State.CONNECTED) { setResponding(false); liveReply = null; }
        String keyInfo = creds.keyProtection();
        setRow(keyRow, keyInfo != null ? keyInfo : "Not created", keyInfo != null ? Theme.SUCCESS : Theme.MUTED_GRAY);
        actions.removeAllViews();
        switch (s) {
            case CONNECTED:
                if (pairingNewPc) { eraseHistory(); pairingNewPc = false; }
                if (detail != null && !detail.isEmpty()) assistantName = detail;
                chip(assistantName + " connected", Theme.SUCCESS);
                setRow(pcRow, "Paired", Theme.SUCCESS);
                setRow(linkRow, "Online · pinned TLS", Theme.SUCCESS);
                setRow(providerRow, ClaudeCodeStatus.UNKNOWN.label, Theme.MUTED_GRAY);
                label("ONLINE", Theme.HOLO_BLUE);
                action(Glyph.Kind.ASSISTANT, "New PC conversation", this::confirmReset);
                action(Glyph.Kind.INFO, "Disconnect", session::disconnect);
                action(Glyph.Kind.GEAR, "Forget this PC", this::forget);
                if (overlay.getVisibility() == View.VISIBLE) closeOverlay(false);
                say("Connected to " + assistantName + ". Type a question, or a TV command.", false);
                if (messages.getChildCount() == 0) hintMessage();
                break;
            case CONNECTING:
                chip("Connecting…", Theme.HOLO_BLUE);
                setRow(pcRow, creds.load() == null ? "Pairing…" : "Paired", Theme.HOLO_BLUE);
                setRow(linkRow, "Connecting…", Theme.MUTED_GRAY);
                label("CONNECTING", Theme.HOLO_BLUE);
                if (overlay.getVisibility() == View.VISIBLE) overlayStatus("Checking the PC's certificate…", false);
                break;
            case WAITING_APPROVAL:
                chip("Waiting for approval", Theme.WARNING);
                setRow(pcRow, "Waiting for approval", Theme.WARNING);
                label("AWAITING APPROVAL", Theme.WARNING);
                overlayStatus("Certificate verified. Approve this TV on your PC — the PC will show TV code "
                        + detail + ".", false);
                break;
            case OFFLINE:
                pairingNewPc = false;
                chip(assistantName + " offline", Theme.WARNING);
                setRow(pcRow, creds.load() == null ? "Not paired" : "Paired", Theme.MUTED_GRAY);
                setRow(linkRow, "Offline", Theme.WARNING);
                label("LOCAL MODE", Theme.HOLO_BLUE);
                action(Glyph.Kind.ASSISTANT, "Retry", session::retry);
                action(Glyph.Kind.GEAR, "Forget this PC", this::forget);
                if (detail != null && !detail.isEmpty()) say(detail, false);
                break;
            case CERT_CHANGED:
                chip("PC identity changed", Theme.ERROR);
                setRow(pcRow, "Certificate changed", Theme.ERROR);
                setRow(linkRow, "Refused (pin mismatch)", Theme.ERROR);
                label("LOCAL MODE", Theme.HOLO_BLUE);
                action(Glyph.Kind.INFO, "Re-verify PC", () -> openOverlay(true));
                action(Glyph.Kind.GEAR, "Forget this PC", this::forget);
                say(detail != null && !detail.isEmpty() ? detail : "The PC's certificate changed.", true);
                if (overlay.getVisibility() == View.VISIBLE && reverifyMode) overlayStatus(detail, true);
                break;
            case REVOKED:
                eraseHistory();
                notPaired();
                chip("Pairing revoked", Theme.ERROR);
                setRow(pcRow, "Revoked by PC", Theme.ERROR);
                say("This TV was removed on the PC. Create a new invitation to reconnect.", true);
                break;
            case DENIED:
                notPaired();
                chip("Access denied", Theme.WARNING);
                setRow(pcRow, creds.load() == null ? "Pairing denied" : "Paired", Theme.WARNING);
                setRow(linkRow, "Access denied", Theme.ERROR);
                if (creds.load() != null) action(Glyph.Kind.GEAR, "Forget this PC", this::forget);
                say("Access denied. Check the invitation or device access on the PC.", true);
                overlayStatus(detail, true);
                break;
            case PAIRING_FAILED:
                pairingNewPc = false;
                if (creds.load() == null) notPaired();
                overlayStatus(detail, true);
                break;
            case NOT_CONFIGURED:
            default:
                notPaired();
                break;
        }
        if (actionHadFocus && overlay.getVisibility() != View.VISIBLE) {
            View target = actions.getChildCount() == 0 ? input : actions.getChildAt(0);
            target.requestFocus();
        }
    }

    private void notPaired() {
        chip("JARVIS not connected", Theme.MUTED_GRAY);
        setRow(pcRow, "Not paired", Theme.MUTED_GRAY);
        setRow(linkRow, "Off", Theme.MUTED_GRAY);
        label("LOCAL MODE", Theme.HOLO_BLUE);
        actions.removeAllViews();
        action(Glyph.Kind.ASSISTANT, "Connect your PC", () -> openOverlay(false));
        if (messages.getChildCount() == 0) {
            TextView m = t.text(this, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            m.setText("TV commands work without a PC. Pair with JARVIS on your PC to ask questions here.");
            messages.addView(m, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        }
    }

    private void reply(String state, String text) {
        if (!"busy".equals(state) && !"delta".equals(state)) setResponding(false);
        switch (state) {
            case "idle":
                reactor.setState(ReactorView.State.IDLE);
                label("ONLINE", Theme.HOLO_BLUE);
                return;
            case "busy":
                setResponding(true);
                reactor.setState(ReactorView.State.WORKING);
                label("THINKING…", Theme.SOFT_WHITE);
                if (text != null && !text.isEmpty()) say(text, false);
                break;
            case "delta":
                setResponding(true);
                reactor.setState(ReactorView.State.WORKING);
                label("ANSWERING…", Theme.SOFT_WHITE);
                if (liveReply == null) liveReply = addMessage(false, "");
                liveReply.setText(TvSuggestion.clean(text));
                scrollToEnd();
                break;
            case "final": {
                reactor.setState(ReactorView.State.IDLE);
                label("ONLINE", Theme.HOLO_BLUE);
                if (liveReply == null) liveReply = addMessage(false, "");
                String completed = TvSuggestion.clean(text);
                liveReply.setText(completed);
                history.append("assistant", completed);
                persistHistory();
                if (visible && !completed.isEmpty()) voice.speakReply(completed);
                liveReply = null;
                TvSuggestion sug = TvSuggestion.find(text);
                if (sug != null) suggestionButton(sug);
                say("", false);
                scrollToEnd();
                break;
            }
            case "aborted":
                reactor.setState(ReactorView.State.IDLE);
                label("ONLINE", Theme.HOLO_BLUE);
                liveReply = null;
                say("Stopped.", false);
                break;
            default:   // error, timeout
                liveReply = null;
                label(jarvisState == JarvisSession.State.CONNECTED ? "ONLINE" : "LOCAL MODE", Theme.HOLO_BLUE);
                say(text, true);
                break;
        }
    }

    // ------------------------------------------------------------------ conversation

    private void setResponding(boolean active) {
        responding = active;
        if (stopReply == null) return;
        boolean returnFocus = !active && stopReply.hasFocus();
        boolean focusStop = active && send.hasFocus();
        enable(stopReply, active && jarvisState == JarvisSession.State.CONNECTED);
        if (focusStop) stopReply.requestFocus();
        enable(send, !active);
        if (returnFocus) input.requestFocus();
    }

    private void persistHistory() {
        localChat.edit().putString("turns", history.encode()).apply();
    }

    private void displayHistory() {
        messages.removeAllViews();
        liveReply = null;
        for (String[] m : history.entries()) addMessage("user".equals(m[0]), m[1]);
        hintMessage();
    }

    private void eraseHistory() {
        history = new ChatHistory();
        localChat.edit().clear().apply();
        displayHistory();
    }

    private void confirmClear() {
        if (responding) { say("Stop the response before clearing history.", false); return; }
        confirm("Clear history on this TV?", "The PC keeps its own conversation. This TV will stop importing those earlier messages.",
                "Clear TV history", () -> {
                    voice.stopSpeaking();
                    history.clearOnTv();
                    persistHistory();
                    displayHistory();
                    say("TV history cleared.", false);
                });
    }

    private void confirmReset() {
        if (responding) { say("Stop the response before starting a new conversation.", false); return; }
        confirm("Start a new PC conversation?", "This resets the paired PC’s TV conversation and clears the copy on this TV.",
                "New conversation", () -> { voice.stopSpeaking(); session.newConversation(); });
    }

    private void confirm(String title, String explanation, String action, Runnable accepted) {
        AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setMessage(explanation)
                .setNegativeButton("Cancel", null).setPositiveButton(action, (dialog, which) -> accepted.run()).show();
        d.getWindow().setBackgroundDrawable(t.glass(Theme.R_PANEL, .98f));
        d.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    private TextView addMessage(boolean user, String text) {
        while (messages.getChildCount() >= ChatHistory.LIMIT * 2) messages.removeViewAt(0);
        if (messages.getChildCount() == 1 && !(messages.getChildAt(0).getTag() instanceof String)) messages.removeAllViews();
        TextView who = t.text(this, Theme.T_CAPTION, user ? Theme.MUTED_GRAY : Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        who.setText(user ? "YOU" : "ASSISTANT");
        who.setLetterSpacing(0.14f);
        who.setTag("msg");
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        if (messages.getChildCount() > 0) wl.topMargin = t.dp(10);
        messages.addView(who, wl);
        TextView body = t.text(this, Theme.T_BODY + 1, user ? Theme.MUTED_GRAY : Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        body.setText(text);
        body.setTag("msg");
        body.setLineSpacing(t.dp(2), 1f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        bl.topMargin = t.dp(2);
        messages.addView(body, bl);
        scrollToEnd();
        return body;
    }

    private void hintMessage() {
        if (messages.getChildCount() > 0) return;
        TextView m = t.text(this, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        m.setText("Type a local command such as “open Downloads.” To ask your PC assistant a question, connect your PC.");
        messages.addView(m, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
    }

    /** A JARVIS suggestion becomes a button only if the TV's own matching finds a real app or input. */
    private void suggestionButton(TvSuggestion sug) {
        final String command = sug.asCommand();
        CommandParser.Result r = parse(command);
        if (r.kind == CommandParser.Kind.UNKNOWN) return;
        String label = r.kind == CommandParser.Kind.HOME ? "Go Home"
                : r.kind == CommandParser.Kind.SWITCH_INPUT ? "Switch to " + r.label : "Open " + r.label;
        LinearLayout chip = w.chip(Glyph.Kind.PLAY, label, true, v -> runTyped(command));
        chip.setTag("msg");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.WRAP, t.dp(34));
        lp.topMargin = t.dp(8);
        messages.addView(chip, lp);
    }

    private void scrollToEnd() {
        // scrollTo, not fullScroll: fullScroll also moves focus into the conversation, which took the
        // remote's focus away from the command list whenever history loaded (found on the TV).
        if (!convo.hasFocus()) convo.post(() -> convo.scrollTo(0, messages.getBottom()));
    }

    // ------------------------------------------------------------------ pairing overlay

    private void openOverlay(boolean reverify) {
        reverifyMode = reverify;
        hostField.setVisibility(reverify ? View.GONE : View.VISIBLE);
        portField.setVisibility(reverify ? View.GONE : View.VISIBLE);
        if (!reverify) {
            String last = creds.lastHost();
            if (last != null && hostField.getText().length() == 0) hostField.setText(last);
            if (portField.getText().length() == 0) portField.setText(String.valueOf(creds.lastPort()));
        }
        codeField.setText("");
        overlayStatus(reverify ? "The PC's certificate changed. Run  openpulse tv invite  and enter the new code to verify it."
                : "", false);
        dashboard.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        dashboard.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        overlay.setVisibility(View.VISIBLE);
        (reverify || hostField.getText().length() > 0 ? codeField : hostField).requestFocus();
    }

    private void submitOverlay() {
        String code = codeField.getText().toString();
        if (reverifyMode) {
            session.reverify(code);
            return;
        }
        String host = hostField.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        // IPv4 only: a name would be looked up in DNS, and a mistyped code must never leave the TV that way.
        if (!JarvisSession.isIpv4(host) || port < 1 || port > 65535) {
            overlayStatus("Enter the PC's IP address (e.g. 192.168.4.33) and the port shown by  openpulse tv invite.", true);
            return;
        }
        creds.rememberHost(host, port);
        pairingNewPc = true;
        session.pair(host, port, code);
    }

    private void closeOverlay(boolean cancel) {
        if (cancel) pairingNewPc = false;
        dashboard.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        dashboard.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        overlay.setVisibility(View.GONE);
        codeField.setText("");
        if (cancel && (jarvisState == JarvisSession.State.WAITING_APPROVAL
                || jarvisState == JarvisSession.State.CONNECTING)) session.cancelPairing();
        if (actions.getChildCount() > 0) actions.getChildAt(0).requestFocus();
    }

    private void overlayStatus(String text, boolean problem) {
        overlayStatus.setText(text == null ? "" : text);
        overlayStatus.setTextColor(problem ? Theme.WARNING : Theme.HOLO_BLUE);
    }

    private void forget() {
        voice.stopSpeaking();
        session.forget();
        eraseHistory();
        say("This TV forgot the PC. Its key and token were deleted.", false);
    }

    // ------------------------------------------------------------------ local commands

    private void buildCommands() {
        appCommand("com.netflix.ninja", "Open Netflix");
        appCommand("com.google.android.youtube.tv", "Open YouTube");
        appCommand("com.plexapp.android", "Open Plex");
        command(Glyph.Kind.GRID, "Open All Apps", () -> openPanel("apps", "Opening All Apps"));
        command(Glyph.Kind.DOWNLOAD, "Open Downloads", () -> openPanel("downloads", "Opening Downloads"));
        command(Glyph.Kind.INPUTS, "Show Inputs", () -> openPanel("inputs", "Opening Inputs"));
        // One input shortcut: an input whose device reports its name over HDMI-CEC, else the first one Android
        // reports as connected (CEC names disappear while e.g. a Fire TV Stick is asleep).
        TvInputs.Source pick = null;
        for (TvInputs.Source s : TvInputs.list(this)) {
            if (s.tuner) continue;
            if (s.deviceName != null && s.state != TvInputs.State.DISCONNECTED) { pick = s; break; }
            if (pick == null && s.state == TvInputs.State.CONNECTED) pick = s;
        }
        if (pick != null) {
            final TvInputs.Source src = pick;
            command(Glyph.Kind.HDMI, "Switch to " + src.name, () -> switchInput(src));
        }
        command(Glyph.Kind.HOME, "Return Home", this::goHome);
    }

    private void appCommand(String pkg, String label) {
        if (!apps.usable(pkg)) return;    // only apps that are actually installed
        command(Glyph.Kind.PLAY, label, () -> openApp(pkg));
    }

    private void command(Glyph.Kind icon, String label, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8));
        row.setFocusable(true);
        row.setClickable(true);
        row.setContentDescription(label);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Glyph(icon, Theme.HOLO_BLUE));
        row.addView(iv, new LinearLayout.LayoutParams(t.dp(18), t.dp(18)));
        TextView tv = t.text(this, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        tv.setText(label);
        LinearLayout.LayoutParams ll = w.wrap();
        ll.leftMargin = t.dp(12);
        row.addView(tv, ll);
        row.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.6f), Theme.withAlpha(Theme.BORDER, 0.9f), 1, 10));
        row.setOnFocusChangeListener((v, has) -> v.setBackground(has
                ? t.outlined(Theme.withAlpha(Theme.ELECTRIC_BLUE, 0.25f), Theme.ELECTRIC_BLUE, 1.5f, 10)
                : t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.6f), Theme.withAlpha(Theme.BORDER, 0.9f), 1, 10)));
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        if (commandList.getChildCount() > 0) lp.topMargin = t.dp(6);
        commandList.addView(row, lp);
    }

    private CommandParser.Result parse(String text) {
        List<CommandParser.Named> appNames = new ArrayList<>();
        for (String p : apps.all()) appNames.add(new CommandParser.Named(p, apps.label(p)));
        List<CommandParser.Named> inputNames = new ArrayList<>();
        for (TvInputs.Source s : TvInputs.list(this)) {
            inputNames.add(new CommandParser.Named(s.id, s.name));
            if (s.deviceName != null) inputNames.add(new CommandParser.Named(s.id, s.deviceName));
        }
        return CommandParser.parse(text, appNames, inputNames);
    }

    private void runTyped(String text) {
        if (text.trim().isEmpty()) { say("Type a question or select Talk.", false); return; }
        CommandParser.Result r = parse(text);
        android.view.inputmethod.InputMethodManager ime = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (ime != null) ime.hideSoftInputFromWindow(input.getWindowToken(), 0);
        switch (r.kind) {
            case HOME: goHome(); break;
            case ALL_APPS: openPanel("apps", "Opening All Apps"); break;
            case DOWNLOADS: openPanel("downloads", "Opening Downloads"); break;
            case DOWNLOAD_LIBRARY: openPanel("downloads:library", "Opening Manage Downloads"); break;
            case INPUTS: openPanel("inputs", "Opening Inputs"); break;
            case SETTINGS: openPanel("settings", "Opening Settings"); break;
            case WALLPAPERS: openPanel("wallpapers", "Opening Wallpapers"); break;
            case OPEN_APP: openApp(r.target); break;
            case SWITCH_INPUT:
                for (TvInputs.Source s : TvInputs.list(this)) if (s.id.equals(r.target)) { switchInput(s); return; }
                say("I couldn't find that input.", true);
                break;
            default:
                String q = text.trim();
                if (q.isEmpty()) return;
                if (jarvisState == JarvisSession.State.CONNECTED) {
                    if (responding) { say("Wait for the reply or select Stop response.", false); return; }
                    voice.stopSpeaking();
                    setResponding(true);
                    history.append("user", q);
                    persistHistory();
                    addMessage(true, q);
                    input.setText("");
                    reactor.setState(ReactorView.State.WORKING);
                    label("SENDING…", Theme.SOFT_WHITE);
                    session.send(q);
                } else {
                    say("JARVIS isn't connected, so I can only run TV commands, like \"open YouTube\" or \"inputs\".", true);
                }
        }
    }

    private void openApp(String pkg) {
        reactor.pulseWorking(600);
        if (apps.launch(pkg)) say("Opening " + apps.label(pkg) + "…", false);
        else say(apps.label(pkg) + " couldn't be opened.", true);
    }

    private void switchInput(TvInputs.Source s) {
        reactor.pulseWorking(600);
        if (apps.start(s.intent)) {
            say("Switching to " + s.name + "…", false);
            finish();
        } else {
            say("This TV didn't accept the switch to " + s.name + ".", true);
        }
    }

    private void openPanel(String which, String message) {
        reactor.pulseWorking(400);
        say(message + "…", false);
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, which));
        finish();
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void say(String text, boolean problem) {
        response.setText(text);
        response.setTextColor(problem ? Theme.WARNING : Theme.SOFT_WHITE);
        if (problem) {
            reactor.setState(ReactorView.State.ERROR);
            reactor.postDelayed(() -> reactor.setState(ReactorView.State.IDLE), 1500);
        }
    }

    // ------------------------------------------------------------------ building blocks

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        t.style(e, Theme.T_BODY + 1, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        e.setHint(hint);
        e.setHintTextColor(Theme.MUTED_GRAY);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        e.setPadding(t.dp(14), t.dp(9), t.dp(14), t.dp(9));
        e.setBackground(t.outlined(Theme.withAlpha(Theme.DEEP_BLACK, 0.6f), Theme.BORDER, 1, 10));
        e.setOnFocusChangeListener((v, has) -> v.setBackground(t.outlined(Theme.withAlpha(Theme.DEEP_BLACK, 0.6f),
                has ? Theme.ELECTRIC_BLUE : Theme.BORDER, has ? 2 : 1, 10)));
        return e;
    }

    private void action(Glyph.Kind icon, String label, Runnable run) {
        LinearLayout chip = w.chip(icon, label, false, v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, t.dp(32));
        lp.topMargin = t.dp(5);
        actions.addView(chip, lp);
    }

    private void label(String text, int color) {
        stateLabel.setText(text);
        stateLabel.setTextColor(color);
    }

    private void chip(String text, int color) {
        chipText.setText(text);
        chipDot.setBackground(t.rounded(color, 4));
    }

    private View panel(String title, View content) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(t.dp(14), t.dp(14), t.dp(14), t.dp(14));
        p.setBackground(t.glass(Theme.R_PANEL, 0.8f));
        p.setClipChildren(false);
        TextView h = t.text(this, Theme.T_CAPTION + 1, Theme.HOLO_BLUE, Theme.FONT_MEDIUM);
        h.setText(title.toUpperCase());
        h.setLetterSpacing(0.16f);
        p.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        lp.topMargin = t.dp(10);
        p.addView(content, lp);
        return p;
    }

    private Row connRow(LinearLayout parent, Glyph.Kind icon, String name) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(t.dp(4), t.dp(4), t.dp(4), t.dp(4));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Glyph(icon, Theme.HOLO_BLUE));
        row.addView(iv, new LinearLayout.LayoutParams(t.dp(16), t.dp(16)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView n = t.text(this, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        n.setText(name);
        text.addView(n, w.wrap());   // explicit WRAP: see stateLabel
        LinearLayout v = new LinearLayout(this);
        v.setGravity(Gravity.CENTER_VERTICAL);
        Row r = new Row();
        r.dot = new View(this);
        v.addView(r.dot, new LinearLayout.LayoutParams(t.dp(7), t.dp(7)));
        r.value = t.text(this, Theme.T_CAPTION, Theme.MUTED_GRAY, Theme.FONT_MEDIUM);
        LinearLayout.LayoutParams vl = w.wrap();
        vl.leftMargin = t.dp(6);
        v.addView(r.value, vl);
        LinearLayout.LayoutParams vvl = w.wrap();
        vvl.topMargin = t.dp(2);
        text.addView(v, vvl);
        LinearLayout.LayoutParams tl = w.wrap();
        tl.leftMargin = t.dp(12);
        row.addView(text, tl);
        parent.addView(row, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        return r;
    }

    private void setRow(Row r, String value, int color) {
        r.value.setText(value);
        r.value.setTextColor(color);
        r.dot.setBackground(t.rounded(color, 4));
    }

    private View statusChip() {
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(t.dp(12), t.dp(6), t.dp(14), t.dp(6));
        chip.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.8f), Theme.BORDER, 1, Theme.R_PILL));
        chipDot = new View(this);
        chip.addView(chipDot, new LinearLayout.LayoutParams(t.dp(8), t.dp(8)));
        chipText = t.text(this, Theme.T_LABEL, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        LinearLayout.LayoutParams lp = w.wrap();
        lp.leftMargin = t.dp(8);
        chip.addView(chipText, lp);
        return chip;
    }
}
