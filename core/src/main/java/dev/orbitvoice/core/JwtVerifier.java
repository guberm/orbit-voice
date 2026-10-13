package dev.orbitvoice.core;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.util.*;

public class JwtVerifier {
    public static JsonObject verify(String jwt, JsonObject jwks, String client, String nonce, long nowSeconds) throws Exception {
        if (jwt == null || jwt.length()>32000) throw new GeneralSecurityException("Invalid identity token");
        String[] parts = jwt.split("\\.");
        if (parts.length!=3) throw new GeneralSecurityException("Invalid identity token");
        Base64.Decoder decoder = Base64.getUrlDecoder();
        JsonObject header = JsonParser.parseString(new String(decoder.decode(parts[0]),StandardCharsets.UTF_8)).getAsJsonObject();
        if (!"RS256".equals(value(header,"alg"))) throw new GeneralSecurityException("Unsupported identity signature");
        JsonObject selected = null;
        for (JsonElement item : jwks.getAsJsonArray("keys")) {
            JsonObject key = item.getAsJsonObject();
            if (value(header,"kid").equals(value(key,"kid")) && "RSA".equals(value(key,"kty"))) { selected=key; break; }
        }
        if (selected == null) throw new GeneralSecurityException("Unknown identity signing key");
        BigInteger modulus = new BigInteger(1,decoder.decode(value(selected,"n")));
        if (modulus.bitLength()<2048) throw new GeneralSecurityException("Weak identity signing key");
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,new BigInteger(1,decoder.decode(value(selected,"e")))));
        Signature signature = Signature.getInstance("SHA256withRSA"); signature.initVerify(key); signature.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(decoder.decode(parts[2]))) throw new GeneralSecurityException("Invalid identity signature");
        JsonObject claims = JsonParser.parseString(new String(decoder.decode(parts[1]),StandardCharsets.UTF_8)).getAsJsonObject();
        boolean audience = false;
        JsonElement aud = claims.get("aud");
        if (aud!=null && aud.isJsonArray()) for (JsonElement a : aud.getAsJsonArray()) audience |= client.equals(a.getAsString());
        else if (aud!=null) audience = client.equals(aud.getAsString());
        if (!audience || !PendingAuth.ISSUER.equals(value(claims,"iss")) || !nonce.equals(value(claims,"nonce")) || value(claims,"sub").isEmpty() || !claims.has("exp") || claims.get("exp").getAsLong()<=nowSeconds-30 || (claims.has("nbf") && claims.get("nbf").getAsLong()>nowSeconds+30) || (claims.has("azp") && !client.equals(value(claims,"azp")))) throw new GeneralSecurityException("Identity claim validation failed");
        if (aud.isJsonArray() && aud.getAsJsonArray().size()>1 && !client.equals(value(claims,"azp"))) throw new GeneralSecurityException("Missing authorized party");
        return claims;
    }
    public static String value(JsonObject object,String name) { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : ""; }
}
