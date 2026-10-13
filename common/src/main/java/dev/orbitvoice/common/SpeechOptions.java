package dev.orbitvoice.common;

import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.*;

/** Speech request policy, separate from microphone and playback lifecycle. */
public final class SpeechOptions {
    public static Intent recognitionIntent(String preference,int sdk){
        Intent request=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        if(automatic(preference)){
            if(sdk>=34){
                LinkedHashSet<String> tags=new LinkedHashSet<>(Arrays.asList(Locale.getDefault().stripExtensions().toLanguageTag(),"en-US","ru-RU"));
                ArrayList<String> allowed=new ArrayList<>(tags);
                request.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,true)
                    .putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                    .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,allowed)
                    .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,allowed);
            }
        }else request.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"system".equals(preference)?Locale.getDefault().stripExtensions().toLanguageTag():preference);
        return request;
    }
    public static boolean automatic(String preference){return preference==null||preference.isEmpty()||"auto".equals(preference);}
    public static Locale outputLanguage(String text,Locale fallback){
        boolean cyrillic=false,latin=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);if(Character.isLetter(c)){
                if(Character.UnicodeBlock.of(c)==Character.UnicodeBlock.CYRILLIC)cyrillic=true;
                if(c>='a'&&c<='z'||c>='A'&&c<='Z')latin=true;
            }
        }
        if(cyrillic)return Locale.forLanguageTag("ru-RU");
        if(latin&&"ru".equals(fallback.getLanguage()))return Locale.US;
        return fallback;
    }
    public static boolean isSilence(int error){return error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT;}
    private SpeechOptions(){}
}
