package dev.orbitvoice.core;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class Wire {
    public static final String REQUEST_PATH="/orbit/v1/request", EVENT_PATH="/orbit/v1/event";
    public static final Set<String> KINDS=new HashSet<>(Arrays.asList("ask","cancel","state","new","history","resume","delta","done","error"));
    public static JsonObject parse(byte[] bytes) {
        try {
            if (bytes.length>60000) throw new IllegalArgumentException("Message too large");
            JsonObject m=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if (m.get("v").getAsInt()!=1 || !KINDS.contains(JwtVerifier.value(m,"kind"))) throw new IllegalArgumentException("Unsupported message");
            UUID.fromString(JwtVerifier.value(m,"id"));
            String conv=JwtVerifier.value(m,"conversation"); if (!conv.isEmpty()) UUID.fromString(conv);
            if (JwtVerifier.value(m,"text").length()>StreamReader.MAX_TEXT || JwtVerifier.value(m,"account").length()>128 || m.get("seq").getAsInt()<0) throw new IllegalArgumentException("Invalid message fields");
            return m;
        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid Orbit Voice message",e); }
    }
    public static JsonObject message(String kind,String id,String account,String conversation,int seq,String text) {
        JsonObject m=new JsonObject();m.addProperty("v",1);m.addProperty("kind",kind);m.addProperty("id",id);m.addProperty("account",account);m.addProperty("conversation",conversation);m.addProperty("seq",seq);m.addProperty("text",text);return m;
    }
    public static byte[] bytes(JsonObject m) { byte[] result=m.toString().getBytes(StandardCharsets.UTF_8);parse(result);return result; }
    public static class Gate {
        private final String id,account,conversation; private int seq=-1;private boolean terminal;
        public Gate(String id,String account,String conversation) { this.id=id;this.account=account;this.conversation=conversation; }
        public boolean accepts(JsonObject m) {
            if (terminal || !id.equals(JwtVerifier.value(m,"id")) || !account.equals(JwtVerifier.value(m,"account")) || !conversation.equals(JwtVerifier.value(m,"conversation")) || m.get("seq").getAsInt()<=seq) return false;
            seq=m.get("seq").getAsInt();String kind=JwtVerifier.value(m,"kind"); terminal=kind.equals("done")||kind.equals("error");return true;
        }
    }
}
