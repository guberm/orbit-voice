package dev.orbitvoice.core;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class WireTest {
    private final String id=UUID.randomUUID().toString(), conv=UUID.randomUUID().toString();
    @Test public void rejectsUnknownVersion() { assertThrows(IllegalArgumentException.class,()->Wire.parse("{\"v\":2}".getBytes(StandardCharsets.UTF_8))); }
    @Test public void rejectsOversizedMessage() { assertThrows(IllegalArgumentException.class,()->Wire.parse(new byte[60001])); }
    @Test public void preservesUnicodeRoundTrip() { JsonObject m=Wire.message("delta",id,"account",conv,1,"Привет 🌍"); assertEquals("Привет 🌍",Wire.parse(Wire.bytes(m)).get("text").getAsString()); }
    @Test public void staleResponseCannotEnterNewConversation() {
        Wire.Gate gate=new Wire.Gate(id,"account",conv);
        assertFalse(gate.accepts(Wire.message("delta",UUID.randomUUID().toString(),"account",conv,1,"Old")));
        assertFalse(gate.accepts(Wire.message("delta",id,"different-account",conv,2,"Old")));
        assertTrue(gate.accepts(Wire.message("delta",id,"account",conv,1,"New")));
    }
    @Test public void outOfOrderSnapshotsCannotRepeatSpeech() {
        Wire.Gate gate=new Wire.Gate(id,"account",conv);
        assertTrue(gate.accepts(Wire.message("delta",id,"account",conv,3,"Three")));
        assertFalse(gate.accepts(Wire.message("delta",id,"account",conv,2,"Two")));
        assertFalse(gate.accepts(Wire.message("delta",id,"account",conv,3,"Three")));
    }
    @Test public void finalResponseRejectsFurtherUpdates() {
        Wire.Gate gate=new Wire.Gate(id,"account",conv);
        assertTrue(gate.accepts(Wire.message("done",id,"account",conv,1,"Done")));
        assertFalse(gate.accepts(Wire.message("delta",id,"account",conv,2,"Late")));
    }
}
