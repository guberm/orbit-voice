package dev.orbitvoice.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeechEndpointTest {
    private short[] frame(int amplitude){short[] samples=new short[320];for(int i=0;i<samples.length;i++)samples[i]=(short)(i%2==0?amplitude:-amplitude);return samples;}
    @Test public void silenceNeverStartsSpeechAndGivesUpAfterThirtySeconds(){
        SpeechEndpoint e=new SpeechEndpoint();int gaveUp=-1;
        for(int i=0;i<2000;i++){if(e.accept(frame(70),320)&&gaveUp<0)gaveUp=i;assertFalse(e.started());}
        assertEquals(1499,gaveUp);
    }
    @Test public void pauseInsideSpeechDoesNotCutOffTheQuestion(){SpeechEndpoint e=new SpeechEndpoint();for(int i=0;i<20;i++)assertFalse(e.accept(frame(1400),320));assertTrue(e.started());for(int i=0;i<20;i++)assertFalse(e.accept(frame(0),320));for(int i=0;i<20;i++)assertFalse(e.accept(frame(1400),320));boolean ended=false;for(int i=0;i<50;i++)ended|=e.accept(frame(0),320);assertTrue(ended);}
    @Test public void briefTapIsNotAnUtterance(){SpeechEndpoint e=new SpeechEndpoint();e.accept(frame(5000),320);for(int i=0;i<50;i++)assertFalse(e.accept(frame(50),320));assertFalse(e.started());}
    @Test public void measuredWatchFloorStillDetectsSpeech(){
        SpeechEndpoint e=new SpeechEndpoint();
        for(int i=0;i<100;i++)assertFalse(e.accept(frame(184),320));   // 2 s of the measured -45 dBFS microphone floor
        for(int i=0;i<10;i++)e.accept(frame(400),320);                  // speech a few dB above that floor
        assertTrue(e.started());
    }
    @Test public void speechEndsAfterEightHundredMillisecondsOfQuiet(){
        SpeechEndpoint e=new SpeechEndpoint();for(int i=0;i<10;i++)e.accept(frame(900),320);assertTrue(e.started());
        int frames=0;while(!e.accept(frame(120),320))frames++;
        assertEquals(39,frames);
    }
}
