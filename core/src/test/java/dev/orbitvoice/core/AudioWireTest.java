package dev.orbitvoice.core;
import com.google.gson.JsonObject;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class AudioWireTest {
    private JsonObject request(){return Wire.message("audio",UUID.randomUUID().toString(),"account",UUID.randomUUID().toString(),0,"");}
    @Test public void microphoneFrameRoundTrip() throws Exception {
        byte[] pcm=new byte[16000];new Random(4).nextBytes(pcm);
        ByteArrayOutputStream out=new ByteArrayOutputStream();JsonObject request=request();AudioWire.write(out,request,pcm);
        AudioWire.Turn turn=AudioWire.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(request,turn.request);assertArrayEquals(pcm,turn.pcm);
    }
    @Test public void interruptedAudioNeverBecomesAQuestion() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();AudioWire.write(out,request(),new byte[16000]);
        byte[] truncated=Arrays.copyOf(out.toByteArray(),out.size()-1);
        assertThrows(IOException.class,()->AudioWire.read(new ByteArrayInputStream(truncated)));
    }
    @Test public void refusesUnboundedOrMisalignedAudio() {
        assertThrows(IllegalArgumentException.class,()->AudioWire.write(new ByteArrayOutputStream(),request(),new byte[AudioWire.MAX_PCM+2]));
        assertThrows(IllegalArgumentException.class,()->AudioWire.write(new ByteArrayOutputStream(),request(),new byte[1]));
    }
    @Test public void transcriptSharesTurnGateAndPreservesLanguage() {
        JsonObject request=request();JsonObject result=Wire.message("transcript",request.get("id").getAsString(),"account",request.get("conversation").getAsString(),1,"Меня зовут Михаил");result.addProperty("language","ru");
        assertEquals("ru",Wire.parse(Wire.bytes(result)).get("language").getAsString());
        Wire.Gate gate=new Wire.Gate(request.get("id").getAsString(),"account",request.get("conversation").getAsString());assertTrue(gate.accepts(result));assertFalse(gate.accepts(result));
    }
}
