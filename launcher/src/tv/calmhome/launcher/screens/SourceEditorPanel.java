package tv.calmhome.launcher.screens;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import tv.calmhome.launcher.core.SourceEntry;
import tv.calmhome.launcher.data.SourceCatalog;
import tv.calmhome.launcher.ui.Theme;

final class SourceEditorPanel extends DownloadPage {
    private final SourceEntry.Category category;
    SourceEditorPanel(Host host, SourceEntry.Category category) { super(host); this.category = category; }
    private EditText addField(String hint, int type, int limit) {
        EditText edit = field(hint, type, limit);
        body.addView(edit, spaced(5));
        return edit;
    }
    @Override protected View build() {
        body.addView(text(category.title + "   ·   Use a link published by the app’s developer.", Theme.T_BODY, false));
        EditText name = addField("Source name", InputType.TYPE_CLASS_TEXT, 100);
        first = name;
        EditText link = addField("Developer HTTPS link", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 8192);
        EditText code = addField("Optional ZeroPulse numeric shortcut", InputType.TYPE_CLASS_NUMBER, 16);
        EditText checksum = addField("Optional SHA-256 from the publisher", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 64);
        TextView error = text("", Theme.T_BODY, false);
        body.addView(error);
        body.addView(action("Save source", () -> {
            try {
                SourceEntry s = new SourceEntry(category, name.getText().toString(), link.getText().toString(),
                        code.getText().toString(), checksum.getText().toString());
                new SourceCatalog(c).add(s);
                host.close(this);
                host.toast("Source saved");
            } catch (IllegalArgumentException e) { showError(error, e); }
        }), spaced(6));
        return screen("Add a trusted source");
    }
}
