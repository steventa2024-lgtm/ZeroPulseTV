package tv.calmhome.launcher.downloads;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Foreground handoff for the system-supplied confirmation Intent. Never exported. */
public final class ApprovalActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent wrapper = getIntent();
            long id = Long.parseLong(wrapper.getData().getPathSegments().get(0));
            int session = Integer.parseInt(wrapper.getData().getLastPathSegment());
            DownloadRecord r = Downloads.get(this).find(id);
            @SuppressWarnings("deprecation")
            Intent system = wrapper.getParcelableExtra(Intent.EXTRA_INTENT);
            if (state == null && system != null && r != null &&
                    r.state == DownloadRecord.State.AWAITING_APPROVAL && r.sessionId == session) startActivity(system);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not open Android approval. Return to the download to retry.", Toast.LENGTH_LONG).show();
        } finally { finish(); }
    }
}
