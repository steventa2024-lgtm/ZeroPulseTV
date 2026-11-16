package tv.calmhome.launcher.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.core.M3uParser;
import tv.calmhome.launcher.data.Prefs;

@RunWith(RobolectricTestRunner.class)
public class StreamingScreenTest {

    private final Context c = RuntimeEnvironment.getApplication();

    private static String allText(View v) {
        StringBuilder b = new StringBuilder();
        if (v instanceof TextView) b.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) b.append(allText(((ViewGroup) v).getChildAt(i)));
        return b.toString();
    }

    private static View find(View root, String description) {
        ArrayList<View> out = new ArrayList<>();
        root.findViewsWithText(out, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        return out.isEmpty() ? null : out.get(0);
    }

    private static <T extends View> T first(View v, Class<T> type) {
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            T t = first(((ViewGroup) v).getChildAt(i), type);
            if (t != null) return t;
        }
        return null;
    }

    private StreamingActivity withChannels() {
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        StreamingStore s = new StreamingStore(c);
        long src = s.addSource(StreamingStore.KIND_URL, "https://lists.example.org/tv.m3u", "lists.example.org");
        java.util.List<M3uParser.Entry> e = M3uParser.parse("#EXTM3U\n"
                + "#EXTINF:-1 group-title=\"News\",Alpha\nhttps://s.example.org/a.m3u8\n"
                + "#EXTINF:-1 group-title=\"Music\",Beta\nhttps://s.example.org/b.m3u8\n"
                + "#EXTINF:-1 group-title=\"News\",Gamma\nhttps://s.example.org/c.m3u8\n").entries;
        s.replaceChannels(src, e, "3 streams");
        s.close();
        return Robolectric.buildActivity(StreamingActivity.class).setup().get();
    }

    @Test public void liveTvGridListsImportedStreamsAndOpensThePlayer() {
        StreamingActivity a = withChannels();
        View root = a.getWindow().getDecorView();
        android.widget.GridView grid = first(root, android.widget.GridView.class);
        assertEquals(3, grid.getAdapter().getCount());
        assertTrue(allText(root).contains("3 of 3"));
        grid.performItemClick(null, 1, grid.getAdapter().getItemId(1));
        assertEquals(PlayerActivity.class.getName(), shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }

    @Test public void groupFiltersSearchAndFavourites() {
        StreamingActivity a = withChannels();
        View root = a.getWindow().getDecorView();
        android.widget.GridView grid = first(root, android.widget.GridView.class);
        // Filters come from the playlist's own groups.
        find(root, "News").performClick();
        assertEquals(2, grid.getAdapter().getCount());
        find(root, "All channels").performClick();
        first(root, android.widget.EditText.class).setText("gam");
        assertEquals(1, grid.getAdapter().getCount());
        first(root, android.widget.EditText.class).setText("");
        // Holding OK on Beta adds it to Favourites, and the Favourites filter appears.
        grid.getOnItemLongClickListener().onItemLongClick(grid, null, 1, grid.getAdapter().getItemId(1));
        StreamingStore s = new StreamingStore(c);
        assertTrue(s.isFavourite("https://s.example.org/b.m3u8"));
        s.close();
        find(root, "Favourites").performClick();
        assertEquals(1, grid.getAdapter().getCount());
    }

    /** Up/Down along the rail changes pages; focus landing on the rail any other way doesn't; OK/click does. */
    @Test public void railFocusRules() {
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        StreamingActivity a = Robolectric.buildActivity(StreamingActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        assertTrue(allText(root).contains("No channels yet"));
        find(root, "Guide").requestFocus();                 // one step down from Live TV
        assertTrue(allText(root).contains("No guide yet"));
        find(root, "Sources").requestFocus();               // a jump, as Android does when a focused view is removed
        assertTrue(allText(root).contains("No guide yet"));
        assertTrue(find(root, "Guide").isFocused());
        find(root, "Sources").performClick();               // OK or a mouse click
        assertTrue(allText(root).contains("Add this link") || find(root, "Add this link") != null);
    }

    /** Found on the TCL: the document "picker" is Android TV's stub, which closes at once. No dead button. */
    @SuppressWarnings("deprecation")   // addResolveInfoForIntent: simplest way to register one stub handler
    @Test public void androidTvStubIsNotOfferedAsAFilePicker() {
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        android.content.pm.ResolveInfo stub = new android.content.pm.ResolveInfo();
        stub.activityInfo = new android.content.pm.ActivityInfo();
        stub.activityInfo.packageName = "com.android.tv.frameworkpackagestubs";
        stub.activityInfo.name = "com.android.tv.frameworkpackagestubs.Stubs$DocumentsStub";
        android.content.Intent pick = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*");
        shadowOf(c.getPackageManager()).addResolveInfoForIntent(pick, stub);
        assertTrue(new android.content.Intent(pick).resolveActivity(c.getPackageManager()) != null);   // it does resolve...
        StreamingActivity a = Robolectric.buildActivity(StreamingActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        find(root, "Sources").performClick();
        assertFalse("...but isn't offered", find(root, "Choose a playlist file on this TV") != null);
        assertTrue(allText(root).contains("no file picker"));
    }

    @Test public void emptyLiveTvExplainsHowToAddASource() {
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        StreamingActivity a = Robolectric.buildActivity(StreamingActivity.class).setup().get();
        String text = allText(a.getWindow().getDecorView());
        assertTrue(text.contains("No channels yet"));
        assertTrue(text.contains("doesn't come with a channel list"));
    }

    @Test public void sourcesPageOffersHttpsLinkAndExplainsAMissingPicker() {
        new Prefs(c).setEnabled(Features.Module.STREAMING, true);
        StreamingActivity a = Robolectric.buildActivity(StreamingActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        find(root, "Sources").performClick();
        String text = allText(root);
        assertTrue(text.contains("HTTPS only"));
        assertTrue(find(root, "Add this link") != null);
        // Robolectric has no document picker, like many TVs: no dead button, an explanation instead.
        assertTrue(text.contains("no file picker"));
        assertFalse(find(root, "Choose a playlist file on this TV") != null);
    }
}
