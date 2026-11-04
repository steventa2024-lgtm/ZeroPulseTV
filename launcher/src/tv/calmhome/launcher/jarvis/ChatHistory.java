package tv.calmhome.launcher.jarvis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The last text turns displayed on this TV. Link credentials never enter this representation. */
public final class ChatHistory {
    public static final int LIMIT = 30;
    private final List<String[]> entries = new ArrayList<>();
    private boolean hidePreviousGatewayHistory;

    public void append(String role, String text) {
        if (!("user".equals(role) || "assistant".equals(role)) || text == null || text.trim().isEmpty()) return;
        int max = "user".equals(role) ? 2000 : 8000;
        if (entries.size() == LIMIT) entries.remove(0);
        entries.add(new String[]{role, text.substring(0, Math.min(text.length(), max))});
    }

    public List<String[]> entries() {
        List<String[]> copy = new ArrayList<>();
        for (String[] message : entries) copy.add(message.clone());
        return copy;
    }

    /** A local deletion also prevents a reconnect from re-importing the deleted PC transcript. */
    public void clearOnTv() {
        entries.clear();
        hidePreviousGatewayHistory = true;
    }

    public boolean syncFromGateway(List<String[]> messages) {
        if (hidePreviousGatewayHistory && !messages.isEmpty()) return false;
        hidePreviousGatewayHistory = false;
        entries.clear();
        for (String[] message : messages) {
            if (message != null && message.length == 2) append(message[0], "assistant".equals(message[0])
                    ? TvSuggestion.clean(message[1]) : message[1]);
        }
        return true;
    }

    public String encode() {
        List<Object> textTurns = new ArrayList<>();
        for (String[] m : entries) textTurns.add(Json.map("role", m[0], "text", m[1]));
        return Json.write(Json.map("hidden", hidePreviousGatewayHistory, "turns", textTurns));
    }

    public static ChatHistory read(String saved) {
        ChatHistory result = new ChatHistory();
        if (saved == null || saved.length() > 2_000_000) return result;
        try {
            Map<String, Object> doc = Json.obj(Json.parse(saved));
            if (doc == null) return result;
            result.hidePreviousGatewayHistory = Json.bool(doc, "hidden");
            for (Object value : Json.list(doc, "turns")) {
                Map<String, Object> turn = Json.obj(value);
                if (turn != null) result.append(Json.str(turn,"role"), Json.str(turn,"text"));
            }
        } catch (IllegalArgumentException ignored) { return new ChatHistory(); }
        return result;
    }
}
