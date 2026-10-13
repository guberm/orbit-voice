package dev.orbitvoice.core;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public class PendingAuth {
    public static final String ISSUER = "https://auth.openai.com";
    public static final String RESOURCE = "https://api.openai.com/v1";
    public final String state = random(), nonce = random(), verifier = random(), redirect;
    private final String hostId, clientId;
    private boolean used;
    public PendingAuth(String redirect, String hostId, String clientId) {
        URI uri = URI.create(redirect);
        if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || !"/auth/callback".equals(uri.getPath()) || uri.getPort() < 1 || uri.getQuery() != null) throw new IllegalArgumentException("Invalid loopback redirect");
        this.redirect = redirect; this.hostId = hostId; this.clientId = clientId;
    }
    public static String random() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String encode(String value) { try { return URLEncoder.encode(value, "UTF-8"); }catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);} }
    private static String decode(String value) { try { return URLDecoder.decode(value,"UTF-8"); }catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);} }
    public String authorizationUrl() {
        Map<String,String> q = new LinkedHashMap<>();
        q.put("client_id", clientId == null ? "dynamic_agent_client" : clientId);
        if (clientId == null) q.put("agent_name_hint", "Orbit Voice");
        q.put("ext_agent_host_id", hostId); q.put("response_type", "code"); q.put("redirect_uri", redirect);
        q.put("scope", "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct");
        q.put("resource", RESOURCE); q.put("state", state); q.put("nonce", nonce); q.put("code_challenge_method", "S256"); q.put("code_challenge", challenge(verifier));
        StringJoiner query = new StringJoiner("&"); q.forEach((k,v) -> query.add(k + "=" + encode(v)));
        return ISSUER + "/api/accounts/authorize?" + query;
    }
    public synchronized Result accept(String callback) {
        if (used) throw new IllegalArgumentException("Sign-in attempt already used");
        URI uri = URI.create(callback), expected = URI.create(redirect);
        if (!Objects.equals(uri.getScheme(),expected.getScheme()) || !Objects.equals(uri.getHost(),expected.getHost()) || uri.getPort()!=expected.getPort() || !Objects.equals(uri.getPath(),expected.getPath())) throw new IllegalArgumentException("Unexpected callback address");
        Map<String,String> q = new HashMap<>();
        if (uri.getRawQuery() != null) for (String part : uri.getRawQuery().split("&")) {
            String[] kv = part.split("=",2); String k = decode(kv[0]), v = kv.length==2 ? decode(kv[1]) : "";
            if (q.put(k,v) != null) throw new IllegalArgumentException("Duplicate callback parameter");
        }
        if (!Objects.equals(state,q.get("state"))) throw new IllegalArgumentException("Sign-in state mismatch");
        used = true;
        if (q.containsKey("error")) throw new IllegalArgumentException("Sign-in was declined. Enable ChatGPT plan usage when trying again.");
        String issued = q.getOrDefault("client_id",clientId);
        if (issued == null || !issued.startsWith("oaiapp_") || (clientId!=null && !clientId.equals(issued))) throw new IllegalArgumentException("Invalid account registration");
        if (q.getOrDefault("code","").isEmpty()) throw new IllegalArgumentException("Missing authorization code");
        return new Result(q.get("code"),issued);
    }
    public static String challenge(String verifier) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static class Result { public final String code, clientId; public Result(String code,String clientId) { this.code=code; this.clientId=clientId; } }
}
