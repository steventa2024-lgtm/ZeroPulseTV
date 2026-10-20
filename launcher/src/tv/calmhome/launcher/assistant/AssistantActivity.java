package tv.calmhome.launcher.assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tv.calmhome.launcher.MainActivity;
import tv.calmhome.launcher.core.CommandParser;
import tv.calmhome.launcher.data.Apps;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.data.TvInputs;
import tv.calmhome.launcher.jarvis.JarvisSession;
import tv.calmhome.launcher.jarvis.TvSuggestion;
import tv.calmhome.launcher.ui.Glyph;
import tv.calmhome.launcher.ui.PulseLogo;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * ZeroPulse Assistant: the holographic scene with local TV commands and, once paired, a conversation with
 * JARVIS on the PC through the OpenPulse TV link.
 *
 * Local commands work with no PC and are tried first. JARVIS only connects while this screen is visible
 * (JarvisSession: bounded timeout, three retries, then Offline); Home never waits for it. Every status
 * shown comes from the real link state. JARVIS can suggest a TV action, which appears as a button and runs
 * only through the TV's own command matching when the viewer presses OK.
 */
public class AssistantActivity extends Activity implements JarvisSession.Listener {

    private static final String VERSION = "2.1";
    private static final int MAX_MESSAGES = 30;

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
    private Row pcRow, linkRow, keyRow;
    private FrameLayout overlay;
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
        String name = Settings.Global.getString(getContentResolver(), "device_name");
        session = new JarvisSession(creds, this, name != null && !name.isEmpty() ? name : "ZeroPulse TV", VERSION, 4000);
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.DEEP_BLACK));
        setContentView(build());
        render(JarvisSession.State.NOT_CONFIGURED, "");
    }

    @Override
    protected void onStart() {
        super.onStart();
        session.start();          // connect only while the Assistant is on screen
    }

    @Override
    protected void onResume() {
        super.onResume();
        reactor.setAnimated(prefs.reactorAnimation() && !t.reducedMotion);
        reactor.setRunning(true);
        if (overlay.getVisibility() != View.VISIBLE && commandList.getChildCount() > 0 && getCurrentFocus() == null)
            commandList.getChildAt(0).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        reactor.setRunning(false);    // no animation work while another app or screen is in front
    }

    @Override
    protected void onStop() {
        super.onStop();
        session.stop();           // no network activity once the Assistant is closed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        session.shutdown();
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
    public void onHistory(List<String[]> list) {
        main.post(() -> {
            if (isDestroyed()) return;
            messages.removeAllViews();
            liveReply = null;
            for (String[] m : list) addMessage("user".equals(m[0]), m[1]);
            if (list.isEmpty()) hintMessage();
        });
    }

    // ------------------------------------------------------------------ building

    private View build() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.DEEP_BLACK, Theme.MIDNIGHT_NAVY, Theme.DEEP_BLACK});
        root.setBackground(bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(t.safeX(), t.safeY(), t.safeX(), Math.round(t.safeY() * 0.7f));
        col.setClipChildren(false);
        root.addView(col, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));

        // header
        FrameLayout header = new FrameLayout(this);
        header.addView(new PulseLogo(this, t.dp(26)).suffix("Assistant", Theme.PULSE_CYAN),
                new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.START | Gravity.CENTER_VERTICAL));
        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.addView(statusChip());
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        t.style(clock, 26, Theme.SOFT_WHITE, Theme.FONT_LIGHT);
        clock.setGravity(Gravity.END);
        clock.setSingleLine(true);
        LinearLayout.LayoutParams ckl = new LinearLayout.LayoutParams(t.dp(110), Widgets.WRAP);   // fixed: see Panel clock
        ckl.leftMargin = t.dp(20);
        right.addView(clock, ckl);
        header.addView(right, new FrameLayout.LayoutParams(Widgets.WRAP, Widgets.WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
        col.addView(header, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));

        // middle: commands | reactor + conversation | JARVIS
        LinearLayout mid = new LinearLayout(this);
        mid.setClipChildren(false);
        commandList = new LinearLayout(this);
        commandList.setOrientation(LinearLayout.VERTICAL);
        commandList.setClipChildren(false);
        buildCommands();
        mid.addView(panel("TV commands", commandList), new LinearLayout.LayoutParams(t.dp(230), Widgets.WRAP));

        LinearLayout centre = new LinearLayout(this);
        centre.setOrientation(LinearLayout.VERTICAL);
        centre.setGravity(Gravity.CENTER_HORIZONTAL);
        reactor = new ReactorView(this);
        centre.addView(reactor, new LinearLayout.LayoutParams(t.dp(150), t.dp(150)));
        stateLabel = t.text(this, Theme.T_LABEL, Theme.HOLO_BLUE, Theme.FONT_MEDIUM);
        stateLabel.setLetterSpacing(0.18f);
        // Explicit WRAP params: a vertical LinearLayout's default child width is MATCH_PARENT, which put this
        // label off-centre (and, in content-sized parents, clipped multi-word text to one line on this TV).
        centre.addView(stateLabel, w.wrap());
        convo = new ScrollView(this);
        convo.setFocusable(true);
        convo.setFillViewport(true);
        convo.setContentDescription("Conversation");
        convo.setPadding(t.dp(14), t.dp(10), t.dp(14), t.dp(10));
        convo.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.55f), Theme.withAlpha(Theme.HOLO_BLUE, 0.25f), 1, Theme.R_PANEL));
        convo.setOnFocusChangeListener((v, has) -> v.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.55f),
                has ? Theme.ELECTRIC_BLUE : Theme.withAlpha(Theme.HOLO_BLUE, 0.25f), has ? 1.5f : 1, Theme.R_PANEL)));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        convo.addView(messages, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        LinearLayout.LayoutParams cvl = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        cvl.topMargin = t.dp(8);
        centre.addView(convo, cvl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, Widgets.MATCH, 1f);
        cl.leftMargin = t.dp(16);
        cl.rightMargin = t.dp(16);
        mid.addView(centre, cl);

        LinearLayout conn = new LinearLayout(this);
        conn.setOrientation(LinearLayout.VERTICAL);
        pcRow = connRow(conn, Glyph.Kind.INPUTS, "JARVIS PC");
        linkRow = connRow(conn, Glyph.Kind.ASSISTANT, "OpenPulse TV link");
        keyRow = connRow(conn, Glyph.Kind.INFO, "Device key");
        Row local = connRow(conn, Glyph.Kind.TV, "Local TV commands");
        setRow(local, "Available", Theme.SUCCESS);
        Row voice = connRow(conn, Glyph.Kind.INFO, "Voice");
        setRow(voice, "Remote mic → Google", Theme.MUTED_GRAY);
        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        al.topMargin = t.dp(6);
        conn.addView(actions, al);
        mid.addView(panel("JARVIS", conn), new LinearLayout.LayoutParams(t.dp(230), Widgets.WRAP));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Widgets.MATCH, 0, 1f);
        ml.topMargin = t.dp(12);
        col.addView(mid, ml);

        // bottom: command box + response
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(t.dp(16), t.dp(10), t.dp(16), t.dp(10));
        bottom.setBackground(t.glass(Theme.R_PANEL, 0.8f));
        input = field("Type a command, e.g. \"open YouTube\"", InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setOnEditorActionListener((v, actionId, e) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                    || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN);
            if (go) runTyped(v.getText().toString());
            return go;
        });
        // Up from the command box reads the conversation (default focus search picked the command list).
        convo.setId(View.generateViewId());
        input.setNextFocusUpId(convo.getId());
        bottom.addView(input, new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f));
        response = t.text(this, Theme.T_BODY, Theme.SOFT_WHITE, Theme.FONT_REGULAR);
        response.setText("Select a command, or type one. Back returns Home.");
        response.setMaxLines(2);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, Widgets.WRAP, 1f);
        rl.leftMargin = t.dp(20);
        bottom.addView(response, rl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        bl.topMargin = t.dp(12);
        col.addView(bottom, bl);

        overlay = buildOverlay();
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(Widgets.MATCH, Widgets.MATCH));
        return root;
    }

    private FrameLayout buildOverlay() {
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(Theme.withAlpha(Theme.DEEP_BLACK, 0.82f));
        scrim.setClickable(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(t.dp(26), t.dp(22), t.dp(26), t.dp(22));
        card.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.97f), Theme.withAlpha(Theme.HOLO_BLUE, 0.5f), 1, Theme.R_PANEL));
        TextView title = t.text(this, Theme.T_BODY + 6, Theme.SOFT_WHITE, Theme.FONT_MEDIUM);
        title.setText("Connect to JARVIS");
        card.addView(title, w.wrap());
        TextView help = t.text(this, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
        help.setText("On your PC, run  openpulse tv invite  and enter what it shows. The TV checks the PC's "
                + "certificate against the code before sending anything, and you approve the TV on the PC.");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP);
        hl.topMargin = t.dp(6);
        card.addView(help, hl);
        hostField = field("PC address, e.g. 192.168.4.33", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        portField = field("Port", InputType.TYPE_CLASS_NUMBER);
        codeField = field("Code: XXXX-XXXX-XXXX-XXXX",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
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
        jarvisState = s;
        String keyInfo = creds.keyProtection();
        setRow(keyRow, keyInfo != null ? keyInfo : "Not created", keyInfo != null ? Theme.SUCCESS : Theme.MUTED_GRAY);
        actions.removeAllViews();
        switch (s) {
            case CONNECTED:
                if (detail != null && !detail.isEmpty()) assistantName = detail;
                chip(assistantName + " connected", Theme.SUCCESS);
                setRow(pcRow, "Connected", Theme.SUCCESS);
                setRow(linkRow, "TLS 1.3 · certificate pinned", Theme.SUCCESS);
                label("ONLINE", Theme.HOLO_BLUE);
                action(Glyph.Kind.ASSISTANT, "New conversation", session::newConversation);
                action(Glyph.Kind.INFO, "Disconnect", session::disconnect);
                action(Glyph.Kind.GEAR, "Forget this PC", this::forget);
                if (overlay.getVisibility() == View.VISIBLE) closeOverlay(false);
                say("Connected to " + assistantName + ". Type a question, or a TV command.", false);
                if (messages.getChildCount() == 0) hintMessage();
                break;
            case CONNECTING:
                chip("Connecting…", Theme.HOLO_BLUE);
                setRow(pcRow, "Connecting…", Theme.HOLO_BLUE);
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
                chip(assistantName + " offline", Theme.WARNING);
                setRow(pcRow, "Offline", Theme.WARNING);
                setRow(linkRow, "Not connected", Theme.MUTED_GRAY);
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
                notPaired();
                say(detail, true);
                break;
            case PAIRING_FAILED:
                if (creds.load() == null) notPaired();
                overlayStatus(detail, true);
                break;
            case NOT_CONFIGURED:
            default:
                notPaired();
                break;
        }
    }

    private void notPaired() {
        chip("JARVIS not connected", Theme.MUTED_GRAY);
        setRow(pcRow, "Not paired", Theme.MUTED_GRAY);
        setRow(linkRow, "Off", Theme.MUTED_GRAY);
        label("LOCAL MODE", Theme.HOLO_BLUE);
        actions.removeAllViews();
        action(Glyph.Kind.ASSISTANT, "Connect to JARVIS", () -> openOverlay(false));
        if (messages.getChildCount() == 0) {
            TextView m = t.text(this, Theme.T_BODY, Theme.MUTED_GRAY, Theme.FONT_REGULAR);
            m.setText("TV commands work without a PC. Pair with JARVIS on your PC to ask questions here.");
            messages.addView(m, new LinearLayout.LayoutParams(Widgets.MATCH, Widgets.WRAP));
        }
    }

    private void reply(String state, String text) {
        switch (state) {
            case "busy":
                reactor.setState(ReactorView.State.WORKING);
                label("THINKING…", Theme.PULSE_CYAN);
                if (text != null && !text.isEmpty()) say(text, false);
                break;
            case "delta":
                reactor.setState(ReactorView.State.WORKING);
                label("ANSWERING…", Theme.PULSE_CYAN);
                if (liveReply == null) liveReply = addMessage(false, "");
                liveReply.setText(TvSuggestion.clean(text));
                scrollToEnd();
                break;
            case "final": {
                reactor.setState(ReactorView.State.IDLE);
                label("ONLINE", Theme.HOLO_BLUE);
                if (liveReply == null) liveReply = addMessage(false, "");
                liveReply.setText(TvSuggestion.clean(text));
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

    private TextView addMessage(boolean user, String text) {
        while (messages.getChildCount() >= MAX_MESSAGES * 2) messages.removeViewAt(0);
        if (messages.getChildCount() == 1 && !(messages.getChildAt(0).getTag() instanceof String)) messages.removeAllViews();
        TextView who = t.text(this, Theme.T_CAPTION, user ? Theme.MUTED_GRAY : Theme.PULSE_CYAN, Theme.FONT_MEDIUM);
        who.setText(user ? "YOU" : assistantName.toUpperCase());
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
        m.setText("Ask " + assistantName + " anything. TV commands like \"open YouTube\" still run on the TV.");
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
        convo.post(() -> convo.scrollTo(0, messages.getBottom()));
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
        session.pair(host, port, code);
    }

    private void closeOverlay(boolean cancel) {
        overlay.setVisibility(View.GONE);
        codeField.setText("");
        if (cancel && jarvisState == JarvisSession.State.WAITING_APPROVAL) session.cancelPairing();
        if (actions.getChildCount() > 0) actions.getChildAt(0).requestFocus();
    }

    private void overlayStatus(String text, boolean problem) {
        overlayStatus.setText(text == null ? "" : text);
        overlayStatus.setTextColor(problem ? Theme.WARNING : Theme.HOLO_BLUE);
    }

    private void forget() {
        session.forget();
        messages.removeAllViews();
        say("This TV forgot the PC. Its key and token were deleted.", false);
    }

    // ------------------------------------------------------------------ local commands

    private void buildCommands() {
        appCommand("com.netflix.ninja", "Open Netflix");
        appCommand("com.google.android.youtube.tv", "Open YouTube");
        appCommand("com.plexapp.android", "Open Plex");
        command(Glyph.Kind.GRID, "Open All Apps", () -> openPanel("apps", "Opening All Apps"));
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
        CommandParser.Result r = parse(text);
        switch (r.kind) {
            case HOME: goHome(); break;
            case ALL_APPS: openPanel("apps", "Opening All Apps"); break;
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
                    addMessage(true, q);
                    input.setText("");
                    reactor.setState(ReactorView.State.WORKING);
                    label("SENDING…", Theme.PULSE_CYAN);
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
        p.setBackground(t.outlined(Theme.withAlpha(Theme.PANEL_NAVY, 0.75f), Theme.withAlpha(Theme.HOLO_BLUE, 0.35f), 1, Theme.R_PANEL));
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
