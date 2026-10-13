package dev.orbitvoice.core;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

public class CoreTest {
    private final String redirect = "http://127.0.0.1:1455/auth/callback";
    @Test public void pkceUsesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", PendingAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }
    @Test public void authorizationUsesOwnIdentityAndStableHost() {
        PendingAuth p = new PendingAuth(redirect, "urn:uuid:host", null);
        assertTrue(p.authorizationUrl().contains("client_id=dynamic_agent_client"));
        assertTrue(p.authorizationUrl().contains("agent_name_hint=Orbit+Voice"));
        assertTrue(p.authorizationUrl().contains("ext_agent_host_id=urn%3Auuid%3Ahost"));
        assertTrue(p.verifier.length() >= 43);
        assertNotEquals(p.state, new PendingAuth(redirect, "urn:uuid:host", null).state);
    }
    @Test public void callbackUsesIssuedClientAndIsSingleUse() {
        PendingAuth p = new PendingAuth(redirect, "urn:uuid:host", null);
        String url = redirect + "?state=" + p.state + "&code=test-code&client_id=oaiapp_orbit";
        assertEquals("oaiapp_orbit", p.accept(url).clientId);
        assertThrows(IllegalArgumentException.class, () -> p.accept(url));
    }
    @Test public void callbackRejectsStateMismatch() {
        PendingAuth p = new PendingAuth(redirect, "urn:uuid:host", null);
        assertThrows(IllegalArgumentException.class, () -> p.accept(redirect + "?state=wrong&code=c&client_id=oaiapp_x"));
    }
    @Test public void callbackRejectsDuplicateParameters() {
        PendingAuth p = new PendingAuth(redirect, "urn:uuid:host", null);
        assertThrows(IllegalArgumentException.class, () -> p.accept(redirect + "?state=" + p.state + "&state=x&code=c&client_id=oaiapp_x"));
    }
    @Test public void returningCallbackCannotReplaceClient() {
        PendingAuth p = new PendingAuth(redirect, "urn:uuid:host", "oaiapp_saved");
        assertThrows(IllegalArgumentException.class, () -> p.accept(redirect + "?state=" + p.state + "&code=c&client_id=oaiapp_other"));
    }
    @Test public void callbackCannotRedirectToAnotherPath() {
        PendingAuth p = new PendingAuth(redirect, "urn:uuid:host", null);
        assertThrows(IllegalArgumentException.class, () -> p.accept("http://127.0.0.1:1455/other?state=" + p.state + "&code=c&client_id=oaiapp_x"));
    }
    @Test public void consentDenialDoesNotExchangeCode() {
        PendingAuth p = new PendingAuth(redirect, "urn:uuid:host", null);
        assertThrows(IllegalArgumentException.class, () -> p.accept(redirect + "?state=" + p.state + "&error=access_denied"));
    }
    private String event(String json) { return "data: " + json + "\n\n"; }
    @Test public void streamAccumulatesDeltasAndRequiresCompletion() throws Exception {
        List<StreamReader.Update> updates = new ArrayList<>();
        String body = ": keepalive\r\n\r\n" + event("{\"type\":\"response.output_text.delta\",\"delta\":\"Привет \"}") + event("{\"type\":\"response.output_text.delta\",\"delta\":\"world.\"}") + event("{\"type\":\"response.completed\",\"response\":{\"output\":[]}}");
        StreamReader.read(new StringReader(body), updates::add);
        assertEquals("done", updates.get(updates.size()-1).kind);
        assertEquals("Привет world.", updates.get(updates.size()-1).text);
    }
    @Test public void interruptedStreamIsNotSuccess() {
        assertThrows(IOException.class, () -> StreamReader.read(new StringReader(event("{\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}")), u -> {}));
    }
    @Test public void lateUsageLimitIsFailure() {
        String body = event("{\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}") + event("{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}");
        IOException e = assertThrows(IOException.class, () -> StreamReader.read(new StringReader(body), u -> {}));
        assertTrue(e.getMessage().contains("subscription_sharing_usage_limit_exceeded"));
    }
    @Test public void incompleteResponseIsFailure() {
        assertThrows(IOException.class, () -> StreamReader.read(new StringReader(event("{\"type\":\"response.incomplete\"}")), u -> {}));
    }
    @Test public void malformedStreamIsFailure() {
        assertThrows(IOException.class, () -> StreamReader.read(new StringReader(event("broken json")), u -> {}));
    }
    @Test public void conversationKeepsContextAndOmitUnsupportedFields() {
        Conversation c = new Conversation();
        c.commit("My name is Mike.", "Hello Mike.", new JsonArray());
        JsonObject request = c.request("account-model", "What is my name?");
        assertFalse(request.get("store").getAsBoolean());
        assertTrue(request.get("stream").getAsBoolean());
        assertEquals(3, request.getAsJsonArray("input").size());
        assertFalse(request.has("previous_response_id"));
        assertFalse(request.has("conversation"));
        assertFalse(request.has("temperature"));
        assertTrue(c.markdown().contains("My name is Mike."));
    }
    @Test public void conversationRetainsReasoningItemsForContinuation() {
        Conversation c = new Conversation();
        JsonArray output = JsonParser.parseString("[{\"type\":\"reasoning\",\"encrypted_content\":\"opaque\"},{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Answer\"}]}]").getAsJsonArray();
        c.commit("Question", "Answer", output);
        assertEquals("reasoning", c.request("m", "Next").getAsJsonArray("input").get(1).getAsJsonObject().get("type").getAsString());
    }
    @Test public void speechWaitsForSentenceAndFlushesTail() {
        SpeechBuffer b = new SpeechBuffer();
        assertTrue(b.update("Hello", false).isEmpty());
        assertEquals(Arrays.asList("Hello there."), b.update("Hello there. Next", false));
        assertEquals(Arrays.asList("Next"), b.update("Hello there. Next", true));
    }
    @Test public void speechDoesNotRepeatCumulativeDeltas() {
        SpeechBuffer b = new SpeechBuffer();
        b.update("One. Two", false);
        assertTrue(b.update("One. Two", false).isEmpty());
        assertEquals(Arrays.asList("Two."), b.update("One. Two.", true));
    }
    @Test public void speechChunksUnpunctuatedLongAnswers() {
        SpeechBuffer b = new SpeechBuffer();
        List<String> chunks = b.update(String.join(" ", Collections.nCopies(1000, "word")), true);
        assertTrue(chunks.size() > 5);
        assertTrue(chunks.stream().allMatch(s -> s.length() <= 400));
    }
    private String b64(byte[] v) { return Base64.getUrlEncoder().withoutPadding().encodeToString(v); }
    private byte[] unsigned(java.math.BigInteger n) { byte[] bytes = n.toByteArray(); return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes; }
    private KeyPair keys() throws Exception { KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); return gen.generateKeyPair(); }
    private JsonObject jwks(KeyPair keys) {
        RSAPublicKey key = (RSAPublicKey) keys.getPublic();
        JsonObject j = new JsonObject(); j.addProperty("kty", "RSA"); j.addProperty("kid", "test"); j.addProperty("alg", "RS256"); j.addProperty("n", b64(unsigned(key.getModulus()))); j.addProperty("e", b64(unsigned(key.getPublicExponent())));
        JsonArray a = new JsonArray(); a.add(j); JsonObject set = new JsonObject(); set.add("keys", a); return set;
    }
    private String jwt(KeyPair keys, String aud, String nonce, long exp) throws Exception {
        String header = b64("{\"alg\":\"RS256\",\"kid\":\"test\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"iss\":\"https://auth.openai.com\",\"aud\":\"" + aud + "\",\"sub\":\"account-A\",\"nonce\":\"" + nonce + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withRSA"); sig.initSign(keys.getPrivate()); sig.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII)); return header + "." + payload + "." + b64(sig.sign());
    }
    @Test public void validatesSignedIdentity() throws Exception { KeyPair k = keys(); assertEquals("account-A", JwtVerifier.verify(jwt(k, "oaiapp_app", "nonce", 2000), jwks(k), "oaiapp_app", "nonce", 1000).get("sub").getAsString()); }
    @Test public void rejectsWrongAudience() throws Exception { KeyPair k = keys(); assertThrows(Exception.class, () -> JwtVerifier.verify(jwt(k,"other","nonce",2000),jwks(k),"oaiapp_app","nonce",1000)); }
    @Test public void rejectsWrongNonce() throws Exception { KeyPair k = keys(); assertThrows(Exception.class, () -> JwtVerifier.verify(jwt(k,"oaiapp_app","wrong",2000),jwks(k),"oaiapp_app","nonce",1000)); }
    @Test public void rejectsExpiredIdentity() throws Exception { KeyPair k = keys(); assertThrows(Exception.class, () -> JwtVerifier.verify(jwt(k,"oaiapp_app","nonce",100),jwks(k),"oaiapp_app","nonce",1000)); }
    @Test public void rejectsWrongSignature() throws Exception { KeyPair k = keys(), other = keys(); assertThrows(Exception.class, () -> JwtVerifier.verify(jwt(k,"oaiapp_app","nonce",2000),jwks(other),"oaiapp_app","nonce",1000)); }
}
