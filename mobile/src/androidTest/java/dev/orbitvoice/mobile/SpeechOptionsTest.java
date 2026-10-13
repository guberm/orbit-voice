package dev.orbitvoice.mobile;

import android.content.Intent;
import android.speech.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dev.orbitvoice.common.SpeechOptions;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SpeechOptionsTest {
    @Test public void automaticRequestAllowsRussianAndEnglishOnEnglishPhone(){
        Intent request=SpeechOptions.recognitionIntent("",34);
        assertTrue(request.getBooleanExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,false));
        assertEquals(RecognizerIntent.LANGUAGE_SWITCH_BALANCED,request.getStringExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH));
        ArrayList<String> allowed=request.getStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES);
        assertNotNull(allowed);assertTrue(allowed.contains("ru-RU"));assertTrue(allowed.contains("en-US"));
    }
    @Test public void explicitRussianRemainsAvailableForOlderRecognizers(){
        Intent request=SpeechOptions.recognitionIntent("ru-RU",30);
        assertEquals("ru-RU",request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
        assertFalse(request.hasExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH));
    }
    @Test public void russianAnswerUsesRussianSpeechOnEnglishPhone(){
        assertEquals("ru",SpeechOptions.outputLanguage("Привет! Как дела?",Locale.US).getLanguage());
        assertEquals("en",SpeechOptions.outputLanguage("Hello, how are you?",Locale.forLanguageTag("ru-RU")).getLanguage());
    }
    @Test public void silenceKeepsConversationOpenButPermissionFailureDoesNot(){
        assertTrue(SpeechOptions.isSilence(SpeechRecognizer.ERROR_NO_MATCH));
        assertTrue(SpeechOptions.isSilence(SpeechRecognizer.ERROR_SPEECH_TIMEOUT));
        assertFalse(SpeechOptions.isSilence(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS));
        assertFalse(SpeechOptions.isSilence(SpeechRecognizer.ERROR_NETWORK));
    }
}
