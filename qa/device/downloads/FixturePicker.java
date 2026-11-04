package tv.calmhome.downloadqa;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

/** Disposable QA file selector. The actual launcher receives the OS activity result and URI grant. */
public final class FixturePicker extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!FixtureProvider.TARGET.equals(getCallingPackage()) ||
                !Intent.ACTION_OPEN_DOCUMENT.equals(getIntent().getAction())) {
            finish();
            return;
        }
        Button pick = new Button(this);
        pick.setText("Choose gated reboot fixture");
        pick.setOnClickListener(v -> {
            Intent result = new Intent().setData(FixtureProvider.SLOW)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            result.setClipData(ClipData.newRawUri("QA fixture", FixtureProvider.SLOW));
            setResult(RESULT_OK, result);
            finish();
        });
        setContentView(pick);
        pick.requestFocus();
    }
}
