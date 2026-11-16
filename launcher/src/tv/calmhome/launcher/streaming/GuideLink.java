package tv.calmhome.launcher.streaming;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tv.calmhome.launcher.core.Guide;

/**
 * Links the user's playlist channels to guide channels for one screen (built once, then read many times).
 * Unmatched channels simply have no guide line.
 */
final class GuideLink {

    final GuideData data;
    final Guide.Index index;
    private final Map<Long, String> guideIdByChannel = new HashMap<>();
    private final Map<String, StreamingStore.Channel> channelByGuideId = new HashMap<>();
    private final Map<Long, String> nowCache = new HashMap<>();
    int matched;

    GuideLink(StreamingStore store, List<StreamingStore.Channel> channels) {
        data = new GuideData(store);
        index = data.index();
        if (index.isEmpty()) return;
        for (StreamingStore.Channel ch : channels) {
            String id = index.match(ch.tvgId, ch.tvgName, ch.name);
            if (id == null) continue;
            guideIdByChannel.put(ch.id, id);
            if (!channelByGuideId.containsKey(id)) channelByGuideId.put(id, ch);
            matched++;
        }
    }

    String guideId(StreamingStore.Channel ch) {
        return guideIdByChannel.get(ch.id);
    }

    /** First playlist channel showing this guide channel, or null when it isn't in the user's playlists. */
    StreamingStore.Channel channelFor(String guideId) {
        return channelByGuideId.get(guideId);
    }

    /** "Now: Title" for a channel card, or null without guide data. */
    String nowLine(StreamingStore.Channel ch, long now) {
        if (nowCache.containsKey(ch.id)) return nowCache.get(ch.id);
        String id = guideIdByChannel.get(ch.id), line = null;
        if (id != null) {
            List<GuideData.Programme> p = data.programmes(id, now, now + 1, 1);
            if (!p.isEmpty()) line = "Now: " + p.get(0).title;
        }
        nowCache.put(ch.id, line);
        return line;
    }
}
