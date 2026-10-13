package dev.orbitvoice.mobile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.SystemClock;
import android.util.Log;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class WhisperTest {
    private byte[] recording(String name) throws Exception {
        try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("speech/"+name)){
            byte[] wav=input.readAllBytes();java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN);int offset=12;
            while(offset+8<=wav.length){int size=b.getInt(offset+4);if(new String(wav,offset,4,java.nio.charset.StandardCharsets.US_ASCII).equals("data"))return java.util.Arrays.copyOfRange(wav,offset+8,offset+8+size);offset+=8+size+(size%2);}
            throw new IOException("Missing WAV data");
        }
    }
    @Test public void automaticallySwitchesRussianEnglishRussian() throws Exception {
        try(WhisperAsr asr=new WhisperAsr(InstrumentationRegistry.getInstrumentation().getTargetContext())){
            for(String language:new String[]{"ru","en","ru"}){
                byte[] pcm=recording(language.equals("ru")?"ru-russian.wav":"en-english.wav");long start=SystemClock.elapsedRealtime();
                WhisperAsr.Result result=asr.transcribe(pcm,()->false);Log.i("OrbitAsrTest",language+" ms="+(SystemClock.elapsedRealtime()-start)+" transcript="+result.text);
                assertEquals(language,result.language);assertFalse(result.text.isEmpty());
                if(language.equals("ru"))assertTrue(result.text.matches("(?s).*[А-Яа-яЁё].*"));
            }
        }
    }
    @Test public void silenceDoesNotBecomeAPaidQuestion() throws Exception {
        try(WhisperAsr asr=new WhisperAsr(InstrumentationRegistry.getInstrumentation().getTargetContext())){
            WhisperAsr.Result result=asr.transcribe(new byte[16000*2],()->false);assertEquals("",result.text);
        }
    }
    @Test public void cancelledRecordingNeverStartsDecoding() throws Exception {
        try(WhisperAsr asr=new WhisperAsr(InstrumentationRegistry.getInstrumentation().getTargetContext())){
            assertThrows(java.util.concurrent.CancellationException.class,()->asr.transcribe(new byte[32000],()->true));
        }
    }
}
