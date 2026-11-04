package tv.calmhome.launcher.assistant;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowTextToSpeech;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34})
public class AssistantVoiceTest {
    public static class Host extends Activity {
        Intent launched;
        int request, finished = -1;
        boolean missing;
        @Override public void startActivityForResult(Intent intent, int code) {
            if (missing) throw new ActivityNotFoundException();
            launched = intent;
            request = code;
        }
        @Override public void finishActivity(int code) { finished = code; }
    }
    private Host host;
    private AssistantVoice control;
    private final ArrayList<String> drafts = new ArrayList<>();
    private AssistantVoice newControl() {
        return new AssistantVoice(host, new AssistantVoice.Listener() {
            @Override public void onVoiceChanged() { }
            @Override public void onTranscript(String text) { drafts.add(text); }
        });
    }
    @Before public void create() {
        host = Robolectric.buildActivity(Host.class).setup().get();
        control = newControl();
        control.refresh();
    }
    @After public void close() { control.close(); }

    @SuppressWarnings("deprecation")
    private void recognizer() {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "qa.android";
        info.activityInfo.name = "VoiceInput";
        info.isDefault = true;
        shadowOf(host.getPackageManager()).addResolveInfoForIntent(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), info);
        control.refresh();
    }
    @SuppressWarnings("deprecation")
    private void synthesis() {
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = "qa.android";
        info.serviceInfo.name = "SpeechEngine";
        shadowOf(host.getPackageManager()).addResolveInfoForIntent(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), info);
        control.refresh();
    }
    private Intent words(String... text) {
        return new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, new ArrayList<>(Arrays.asList(text)));
    }

    @Test public void absentServicesLeaveTypedModeAvailable() {
        assertFalse(control.hasRecognizer());
        control.startRecognition();
        assertFalse(control.recognizing());
        assertNull(host.launched);
        assertTrue(control.status().contains("Type"));
        control.setSpeakEnabled(true);
        assertFalse(control.speakEnabled());
        assertFalse(control.speaking());
    }
    @Test public void systemResultBecomesOneDraftAcrossRecreation() {
        recognizer();
        control.startRecognition();
        assertTrue(control.recognizing());
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, host.launched.getAction());
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, host.launched.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL));
        Bundle state = new Bundle();
        control.save(state);
        control.close();
        control = newControl();
        control.restore(state);
        assertTrue(control.onResult(host.request, Activity.RESULT_OK, words("", " Open Apps ", "Open Maps")));
        assertEquals(Arrays.asList("Open Apps"), drafts);
        assertFalse(control.recognizing());
        control.onResult(host.request, Activity.RESULT_OK, words("replay"));
        assertEquals(1, drafts.size());
        assertFalse(control.onResult(72, Activity.RESULT_OK, words("unrelated")));
    }
    @Test public void canceledAndFailedSessionsDoNotReplaceDraft() {
        recognizer();
        control.startRecognition();
        control.cancelRecognition();
        assertEquals(host.request, host.finished);
        control.onResult(host.request, Activity.RESULT_OK, words("late canceled result"));
        assertTrue(drafts.isEmpty());
        control.startRecognition();
        control.onResult(host.request, RecognizerIntent.RESULT_AUDIO_ERROR, null);
        assertTrue(control.status().contains("Microphone"));
        assertFalse(control.recognizing());
        assertTrue(drafts.isEmpty());
    }
    @Test public void disappearingRecognizerFallsBackAtLaunch() {
        recognizer();
        host.missing = true;
        control.startRecognition();
        assertFalse(control.recognizing());
        assertTrue(control.status().contains("could not open"));
    }
    private TextToSpeech enableSpeech() {
        synthesis();
        ShadowTextToSpeech.addLanguageAvailability(Locale.getDefault());
        control.setSpeakEnabled(true);
        assertTrue(control.speakEnabled());
        TextToSpeech engine = ShadowTextToSpeech.getLastTextToSpeechInstance();
        assertNotNull(engine);
        shadowOf(engine).getOnInitListener().onInit(TextToSpeech.SUCCESS);
        shadowOf(Looper.getMainLooper()).idle();
        return engine;
    }
    @Test public void speechIsOptInAndStopsWhenBackgrounded() {
        control.speakReply("Off by default");
        assertFalse(control.speaking());
        TextToSpeech engine = enableSpeech();
        control.speakReply("A confirmed final reply");
        assertEquals("A confirmed final reply", shadowOf(engine).getLastSpokenText());
        control.onBackground();
        assertFalse(control.speaking());
        assertNotNull(shadowOf(host.getSystemService(AudioManager.class)).getLastAbandonedAudioFocusRequest());
        control.speakReply("Background response");
        assertEquals("A confirmed final reply", shadowOf(engine).getLastSpokenText());
        control.refresh();
        control.setSpeakEnabled(false);
        assertFalse(control.speakEnabled());
    }
    @Test public void deniedAudioFocusKeepsText() {
        enableSpeech();
        shadowOf(host.getSystemService(AudioManager.class)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
        control.speakReply("Do not play over another app");
        assertFalse(control.speaking());
        assertTrue(control.status().contains("Audio focus"));
    }
    @Test public void previewUsesTheRealSpeechQueueOnlyWhenEnabled() {
        control.preview();
        assertFalse(control.speaking());
        TextToSpeech engine = enableSpeech();
        control.preview();
        assertEquals("This is a local ZeroPulse voice preview.", shadowOf(engine).getLastSpokenText());
        assertTrue(drafts.isEmpty());
        control.stopSpeaking();
        assertFalse(control.speaking());
    }
}
