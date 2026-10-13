package dev.orbitvoice.common;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import android.speech.*;
import android.speech.tts.*;
import dev.orbitvoice.core.SpeechBuffer;
import java.util.*;

/** Foreground, turn-based voice conversation with sentence streaming and echo avoidance. */
public final class VoiceSession implements AutoCloseable {
    public interface Listener { void heard(String text);void status(String text);void transcript(String text);void cancel(); }
    public static final int PERMISSION=803, RECOGNITION=804;
    private final Activity activity;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());
    private final TextToSpeech tts;private final AudioManager audio;
    private SpeechRecognizer recognizer;private SpeechBuffer buffer=new SpeechBuffer();private AudioFocusRequest focus;
    private boolean ttsReady,active,external,thinking,listening,closed;
    private String generation=UUID.randomUUID().toString(),externalGeneration="";private Locale locale=Locale.getDefault();private int silence;private long deadline;
    public VoiceSession(Activity activity,Listener listener) {
        this.activity=activity;this.listener=listener;audio=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
        tts=new TextToSpeech(activity,status->{ttsReady=status==TextToSpeech.SUCCESS;});
        tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { main.post(()->{
                if(active && id.equals(generation+":done")) { abandonFocus();thinking=false;listener.status("Your turn");String expected=generation;main.postDelayed(()->{if(active&&generation.equals(expected))listen();},500); }
            }); }
            @Override public void onError(String id) { main.post(()->{if(id.startsWith(generation))fail("Speech playback failed. Check the selected voice and audio output.");}); }
        });
    }
    public void language(String tag) { locale=tag==null||tag.isEmpty()?Locale.getDefault():Locale.forLanguageTag(tag); }
    public boolean active() { return active; }
    public boolean inExternalRecognizer() { return external; }
    public void start() {
        if(closed)return;
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);return; }
        if(!ttsReady) { listener.status("Speech output is not ready. Install or enable a Text-to-Speech engine, then try again.");return; }
        int available=tts.setLanguage(locale);
        if(available==TextToSpeech.LANG_MISSING_DATA || available==TextToSpeech.LANG_NOT_SUPPORTED) { listener.status("This speech language is unavailable. Install its voice or choose another language.");return; }
        if(active)interrupt();else { active=true;silence=0;deadline=SystemClock.elapsedRealtime()+600000;generation=UUID.randomUUID().toString();main.postDelayed(()->{if(active)pause("Session paused after 10 minutes. Tap to continue.");},600000);listen(); }
    }
    private void listen() {
        if(!active||closed)return;
        if(deadline>0&&SystemClock.elapsedRealtime()>=deadline){pause("Session paused after 10 minutes. Tap to continue.");return;}
        thinking=false;listening=true;listener.status("Listening…");String listeningGeneration=generation;
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,locale.toLanguageTag()).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        if(!SpeechRecognizer.isRecognitionAvailable(activity)) {
            try { external=true;externalGeneration=generation;activity.startActivityForResult(intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to Orbit Voice"),RECOGNITION); }
            catch(ActivityNotFoundException e) { external=false;fail("No speech recognizer is installed. Enable voice input on this device."); }return;
        }
        if(recognizer!=null)recognizer.destroy();
        recognizer=SpeechRecognizer.createSpeechRecognizer(activity);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { if(active)listener.status("Recognizing…"); }
            @Override public void onError(int error) {
                if(!active||thinking||!generation.equals(listeningGeneration))return;listening=false;
                if((error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT)&&silence++<1) { String expected=generation;main.postDelayed(()->{if(active&&generation.equals(expected))listen();},700); }
                else fail(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Microphone permission is required. Enable it in app settings.":"Voice input stopped. Check the microphone, network and speech language, then tap to retry.");
            }
            @Override public void onResults(Bundle results) { if(generation.equals(listeningGeneration))result(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)); }
            @Override public void onPartialResults(Bundle results) { ArrayList<String> words=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(active&&generation.equals(listeningGeneration)&&words!=null&&!words.isEmpty())listener.transcript(words.get(0)); }
            @Override public void onEvent(int t,Bundle p) { }
        });
        try { recognizer.startListening(intent); }catch(RuntimeException e) { fail("Could not start voice input. Enable a speech service and retry."); }
    }
    private void result(ArrayList<String> words) {
        listening=false;if(!active)return;
        if(words==null||words.isEmpty()||words.get(0).trim().isEmpty()) { fail("No speech was recognized. Tap to try again.");return; }
        silence=0;thinking=true;buffer=new SpeechBuffer();generation=UUID.randomUUID().toString();listener.transcript(words.get(0));listener.status("Thinking…");listener.heard(words.get(0));
    }
    public void update(String text,boolean completed) {
        if(!active||!thinking)return;
        listener.transcript(text);
        try {
            for(String sentence:buffer.update(text,completed)) {
                if(focus==null) {
                    focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)main.post(()->pause("Audio interrupted. Tap to continue."));}).build();
                    if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { fail("Audio output is busy. Try again when it is available.");return; }
                }
                listener.status("Speaking…");
                if(tts.speak(sentence,TextToSpeech.QUEUE_ADD,null,generation+":"+UUID.randomUUID())==TextToSpeech.ERROR) { fail("Speech playback is unavailable.");return; }
            }
            if(completed && tts.playSilentUtterance(100,TextToSpeech.QUEUE_ADD,generation+":done")==TextToSpeech.ERROR)fail("Speech playback did not complete.");
        }catch(IllegalArgumentException e) { fail("Response changed unexpectedly. Tap to try again."); }
    }
    public void fail(String message) { pause(message); }
    public void interrupt() { pause("Ready");active=true;silence=0;deadline=SystemClock.elapsedRealtime()+600000;generation=UUID.randomUUID().toString();listen(); }
    public void pause(String message) {
        active=false;thinking=false;listening=false;generation=UUID.randomUUID().toString();main.removeCallbacksAndMessages(null);
        if(recognizer!=null) { recognizer.cancel();recognizer.destroy();recognizer=null; }
        tts.stop();abandonFocus();listener.cancel();listener.status(message);
    }
    private void abandonFocus() { if(focus!=null){audio.abandonAudioFocusRequest(focus);focus=null;} }
    public boolean permission(int code,int[] results) {
        if(code!=PERMISSION)return false;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)start();else listener.status("Microphone access is off. Enable it in app settings to talk.");return true;
    }
    public boolean activityResult(int code,int result,Intent data) {
        if(code!=RECOGNITION)return false;external=false;if(!generation.equals(externalGeneration))return true;
        if(result==Activity.RESULT_OK && data!=null)result(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));else pause("Voice input cancelled.");return true;
    }
    @Override public void close() { if(closed)return;pause("Paused");closed=true;tts.shutdown(); }
}
