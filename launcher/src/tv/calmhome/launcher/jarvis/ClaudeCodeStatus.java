package tv.calmhome.launcher.jarvis;

import java.util.Map;

/** Only an explicit assertion from an authenticated gateway can establish provider availability. */
public enum ClaudeCodeStatus {
    UNKNOWN("Not reported"), CONFIGURED("Configured on PC"), AVAILABLE("Available"),
    UNAVAILABLE("Unavailable"), RATE_LIMITED("Rate limit reached"), APPROVAL_REQUIRED("Subscription relay disabled");

    public final String label;
    ClaudeCodeStatus(String label) { this.label = label; }

    static ClaudeCodeStatus fromPayload(Map<String, Object> payload) {
        Map<String, Object> provider = Json.obj(payload, "provider");
        if (!"claude-code".equals(Json.str(provider, "id"))) return UNKNOWN;
        String value = Json.str(provider, "state");
        if ("configured".equals(value)) return CONFIGURED;
        if ("available".equals(value)) return AVAILABLE;
        if ("unavailable".equals(value)) return UNAVAILABLE;
        if ("rate_limited".equals(value)) return RATE_LIMITED;
        if ("approval_required".equals(value)) return APPROVAL_REQUIRED;
        return UNKNOWN;
    }
}
