package tv.calmhome.launcher;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.widget.Toast;

import tv.calmhome.launcher.core.Features;
import tv.calmhome.launcher.data.Prefs;
import tv.calmhome.launcher.ui.Theme;
import tv.calmhome.launcher.ui.Widgets;

/**
 * Base of the optional module screens (ZeroPulse Streaming, ZeroPulse Web). The screen refuses to open, and
 * closes itself when it comes back to the front, while its module is switched off in Settings → Features.
 * Subclasses build their UI only after {@link #onModuleCreate} is called, so nothing of a switched-off module
 * (player, WebView, network) is ever created.
 */
public abstract class ModuleActivity extends Activity {

    protected Prefs prefs;
    protected Theme theme;
    protected Widgets w;

    protected abstract Features.Module module();

    /** Called once from onCreate when the module is on. */
    protected abstract void onModuleCreate(Bundle state);

    @Override
    protected final void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        if (!refuseIfOff()) return;
        theme = new Theme(this, prefs);
        w = new Widgets(this, theme);
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.DEEP_BLACK));
        onModuleCreate(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing()) refuseIfOff();
    }

    /** Returns true when the module may run; otherwise explains and finishes. */
    private boolean refuseIfOff() {
        if (Features.mayOpen(prefs, module())) return true;
        Toast.makeText(this, module().title + " is turned off in Settings → Features", Toast.LENGTH_LONG).show();
        finish();
        return false;
    }
}
