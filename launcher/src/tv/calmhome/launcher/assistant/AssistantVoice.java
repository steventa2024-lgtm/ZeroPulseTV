package tv.calmhome.launcher.assistant;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.List;
import java.util.Locale;

import tv.calmhome.launcher.core.SpeechText;

/** Activity-scoped voice controls. Recognition belongs to Android; only reviewed text enters chat. */
final class AssistantVoice {
    interface Listener {
        void onVoiceChanged();
        void onTranscript(String text);
    }

    private static final int REQUEST_SPEECH = 6404;
    private static final String WAITING = "assistant.recognizerPending";
    private static final String CANCELED = "assistant.recognizerCanceled";
    private final Activity host;
    private final Listener listener;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_ASSISTANT).build();
    private AudioFocusRequest focus;
    private TextToSpeech engine;
    private boolean foreground, closed, hasRecognizer, hasEngine, waiting, canceled, initialized;
    private String note = "", pendingReply, lastUtterance;
    private int session, utterance;

    AssistantVoice(Activity host, Listener listener) {
        this.host = host;
        this.listener = listener;
        prefs = host.getSharedPreferences("assistant_voice", Context.MODE_PRIVATE);
        audio = host.getSystemService(AudioManager.class);
    }

    void refresh() {
        if (closed) return;
        foreground = true;
        PackageManager pm = host.getPackageManager();
        boolean recognizer = !pm.queryIntentActivities(recognitionIntent(), PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
        boolean synthesis = !pm.queryIntentServices(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isEmpty();
        if (hasRecognizer != recognizer || hasEngine != synthesis) {
            hasRecognizer = recognizer;
            hasEngine = synthesis;
            changed();
        }
    }

    void save(Bundle out) {
        out.putBoolean(WAITING, waiting);
        out.putBoolean(CANCELED, canceled);
    }
    void restore(Bundle saved) {
        if (saved != null) {
            waiting = saved.getBoolean(WAITING);
            canceled = saved.getBoolean(CANCELED);
        }
    }

    boolean hasRecognizer() { return hasRecognizer; }
    boolean recognizing() { return waiting && !canceled; }
    boolean speakEnabled() { return prefs.getBoolean("speakReplies", false); }
    boolean speaking() { return pendingReply != null || lastUtterance != null; }
    String status() {
        if (recognizing()) return "Android voice input is open. Press Back to cancel.";
        if (!note.isEmpty()) return note;
        if (!hasRecognizer) return "No speech input service on this TV. Type with the remote.";
        if (!host.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
            return "Android voice input may use a compatible remote microphone. Typing is always available.";
        return "Push to talk with Android. Its speech service may process audio online.";
    }

    private Intent recognitionIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message. Review it before sending.");
    }

    void startRecognition() {
        if (closed || waiting) return;
        refresh();
        if (!hasRecognizer) { report("No speech input service on this TV. Type with the remote."); return; }
        silence();
        waiting = true;
        canceled = false;
        try {
            host.startActivityForResult(recognitionIntent(), REQUEST_SPEECH);
            changed();
        } catch (ActivityNotFoundException | SecurityException e) {
            waiting = false;
            report("Android could not open voice input. Type with the remote.");
        }
    }

    void cancelRecognition() {
        if (!waiting || canceled) return;
        canceled = true;
        host.finishActivity(REQUEST_SPEECH);
        report("Voice input canceled. You can still type.");
    }

    boolean onResult(int request, int result, Intent data) {
        if (request != REQUEST_SPEECH) return false;
        boolean accept = waiting && !canceled && !closed;
        waiting = false;
        canceled = false;
        if (!accept) { changed(); return true; }
        if (result == Activity.RESULT_OK) {
            String text = SpeechText.transcript(data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
            if (!text.isEmpty()) {
                listener.onTranscript(text);
                report("Transcription received. Edit your message, then choose Send.");
            } else report("No words were returned. Try again or type.");
        } else {
            switch (result) {
                case Activity.RESULT_CANCELED: report("Voice input canceled. Your draft was kept."); break;
                case RecognizerIntent.RESULT_NO_MATCH: report("No words were recognized. Try again or type."); break;
                case RecognizerIntent.RESULT_AUDIO_ERROR: report("Microphone input failed. Check the remote or type."); break;
                case RecognizerIntent.RESULT_NETWORK_ERROR: report("The speech service could not connect. Try again or type."); break;
                default: report("Speech recognition failed. Try again or type.");
            }
        }
        return true;
    }

    void setSpeakEnabled(boolean enabled) {
        if (closed) return;
        if (enabled && !hasEngine) { report("No speech engine on this TV. Replies remain available as text."); return; }
        prefs.edit().putBoolean("speakReplies", enabled).apply();
        if (enabled) {
            report("Spoken replies enabled.");
            ensureEngine();
        } else {
            silence();
            report("Spoken replies off.");
        }
    }

    void speakReply(String text) {
        if (closed || !foreground || waiting || !speakEnabled() || text == null || text.trim().isEmpty()) return;
        if (!hasEngine) { report("Speech engine unavailable. The reply is shown as text."); return; }
        silence();
        pendingReply = text;
        if (initialized) playPending(); else ensureEngine();
    }

    private void ensureEngine() {
        if (closed || engine != null || !foreground) return;
        final int generation = ++session;
        try {
            engine = new TextToSpeech(host.getApplicationContext(),
                    result -> main.post(() -> onEngineInit(generation, result)));
        } catch (RuntimeException unavailable) { engineFailed(); }
    }

    private void onEngineInit(int generation, int result) {
        if (closed || session != generation || engine == null) return;
        if (result != TextToSpeech.SUCCESS || engine.setLanguage(Locale.getDefault()) < 0 ||
                engine.setAudioAttributes(attributes) != TextToSpeech.SUCCESS) {
            engineFailed();
            return;
        }
        initialized = true;
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                main.post(() -> {
                    if (!closed && lastUtterance != null && id != null && id.startsWith(utterance + "-"))
                        report("Speaking reply.");
                });
            }
            @Override public void onDone(String id) { main.post(() -> finished(id, false)); }
            @Override public void onStop(String id, boolean interrupted) { main.post(() -> finished(id, true)); }
            @Override @SuppressWarnings("deprecation") public void onError(String id) { main.post(() -> finished(id, true)); }
        });
        playPending();
    }

    private void engineFailed() {
        silence();
        if (engine != null) engine.shutdown();
        engine = null;
        initialized = false;
        report("Speech engine or language unavailable. Read the reply on screen.");
    }

    private void playPending() {
        if (pendingReply == null || !initialized || !speakEnabled() || !foreground || closed) return;
        List<String> parts = SpeechText.utterances(pendingReply, TextToSpeech.getMaxSpeechInputLength());
        pendingReply = null;
        if (parts.isEmpty()) return;
        String prefix = (++utterance) + "-";
        AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0 && lastUtterance != null && lastUtterance.startsWith(prefix)) stopSpeaking();
                }, main).build();
        if (audio == null || audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            report("Audio focus is unavailable. The reply is shown as text.");
            return;
        }
        focus = request;
        lastUtterance = prefix + (parts.size() - 1);
        for (int i = 0; i < parts.size(); i++) {
            int queue = i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            if (engine.speak(parts.get(i), queue, new Bundle(), prefix + i) != TextToSpeech.SUCCESS) {
                silence();
                report("Could not speak the reply. Read it on screen.");
                return;
            }
        }
        report("Reply queued for speech.");
    }

    private void finished(String id, boolean failed) {
        if (closed || lastUtterance == null || id == null) return;
        if (failed && id.startsWith(utterance + "-")) {
            silence();
            report("Speech playback failed. Read the reply on screen.");
        } else if (id.equals(lastUtterance)) {
            lastUtterance = null;
            releaseFocus();
            report("Spoken reply finished.");
        }
    }

    void stopSpeaking() {
        silence();
        report("Speech stopped.");
    }
    private void silence() {
        pendingReply = null;
        lastUtterance = null;
        if (engine != null && initialized) engine.stop();
        releaseFocus();
    }
    private void releaseFocus() {
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        focus = null;
    }

    void onBackground() {
        foreground = false;
        if (speaking()) stopSpeaking();
    }
    void close() {
        closed = true;
        foreground = false;
        silence();
        main.removeCallbacksAndMessages(null);
        if (engine != null) engine.shutdown();
        engine = null;
    }
    private void report(String text) { note = text; changed(); }
    private void changed() { if (!closed) listener.onVoiceChanged(); }
}
