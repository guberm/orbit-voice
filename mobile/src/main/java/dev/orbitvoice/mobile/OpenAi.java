package dev.orbitvoice.mobile;

import com.google.gson.*;
import dev.orbitvoice.core.*;
import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class OpenAi {
    static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status){super(httpError(status));this.status=status;}
    }
    static final class SignInFailure extends IOException {
        SignInFailure(String stage,Exception cause){super(stage+" failed: "+safeReason(cause)+". Try signing in again.",cause);}
    }
    private static String safeReason(Exception failure){
        if(failure instanceof HttpFailure)return "HTTP "+((HttpFailure)failure).status;
        // Only fixed, application-owned messages may enter the user-visible diagnostic.
        String message=failure.getMessage();
        Set<String> safe=new HashSet<>(Arrays.asList("Invalid identity token","Unsupported identity signature","Unknown identity signing key","Weak identity signing key","Invalid identity signature","Identity claim validation failed","Missing authorized party","Invalid server response.","Empty server response.","Server response too large.","ChatGPT plan usage was not authorized. Reconnect and approve plan access.","Missing bearer access credential.","Invalid access-token expiry.","Cannot save local data"));
        return safe.contains(message)?message:failure.getClass().getSimpleName();
    }
    static final String TOKEN=PendingAuth.ISSUER+"/api/accounts/oauth/token";
    static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    final OkHttpClient client;
    private final Repository repo;
    OpenAi(Repository repo) { this(repo,new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).callTimeout(150,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).build()); }
    OpenAi(Repository repo,OkHttpClient client) { this.repo=repo;this.client=client; }
    JsonObject json(Request request) throws IOException {
        try(Response response=client.newCall(request).execute()) {
            if(response.body()==null) throw new IOException("Empty server response.");
            okio.BufferedSource source=response.body().source();source.request(1000001);
            if(source.getBuffer().size()>1000000) throw new IOException("Server response too large.");
            String body=source.readUtf8();
            if(!response.isSuccessful()) throw new HttpFailure(response.code());
            try { return JsonParser.parseString(body).getAsJsonObject(); } catch(RuntimeException e) { throw new IOException("Invalid server response."); }
        }
    }
    static String httpError(int status) {
        if(status==401) return "Authorization expired. Reconnect your ChatGPT account on the phone.";
        if(status==403) return "ChatGPT plan access was declined. Check app permissions and account eligibility in ChatGPT settings.";
        if(status==429) return "Usage limit reached. Check ChatGPT Settings > Usage, then try again later.";
        return "OpenAI request failed (HTTP "+status+"). Try again later.";
    }
    JsonObject form(String endpoint,FormBody body) throws IOException { return json(new Request.Builder().url(endpoint).post(body).build()); }
    Repository.Account exchange(PendingAuth pending,PendingAuth.Result code,Repository.Account returning,java.util.function.BooleanSupplier stillActive) throws Exception {
        JsonObject tokens,jwks,identity;
        try { tokens=form(TOKEN,new FormBody.Builder().add("grant_type","authorization_code").add("client_id",code.clientId).add("code",code.code).add("code_verifier",pending.verifier).add("redirect_uri",pending.redirect).add("resource",PendingAuth.RESOURCE).build()); }
        catch(Exception e){throw new SignInFailure("Token exchange",e);}
        try { jwks=json(new Request.Builder().url(PendingAuth.ISSUER+"/.well-known/jwks.json").build()); }
        catch(Exception e){throw new SignInFailure("Signing-key download",e);}
        try { identity=JwtVerifier.verify(JwtVerifier.value(tokens,"id_token"),jwks,code.clientId,pending.nonce,System.currentTimeMillis()/1000); }
        catch(Exception e){throw new SignInFailure("Identity verification",e);}
        String subject=JwtVerifier.value(identity,"sub");
        if(returning!=null && !returning.subject.equals(subject)) throw new IOException("The signed-in account does not match the selected registration.");
        Repository.Account account=returning==null?new Repository.Account():Repository.JSON.fromJson(Repository.JSON.toJson(returning),Repository.Account.class);
        account.clientId=code.clientId;account.subject=subject;account.email=JwtVerifier.value(identity,"email");if(account.email.isEmpty())account.email="ChatGPT account";
        account.key=PendingAuth.challenge(subject+"|"+code.clientId);account.idToken=JwtVerifier.value(tokens,"id_token");
        try { setTokens(account,tokens,false); }catch(Exception e){throw new SignInFailure("Plan permission",e);}
        if(!stillActive.getAsBoolean())throw new IOException("Sign-in cancelled.");
        if(returning!=null)repo.config.accounts.remove(returning);
        if(returning==null) {
            Repository.Account old=null;for(Repository.Account saved:repo.config.accounts) if(saved.key.equals(account.key))old=saved;
            if(old!=null) { account.current=old.current;account.model=old.model;repo.config.accounts.remove(old); }
        }
        repo.config.accounts.add(account);
        repo.config.selected=account.key;
        try {repo.save();}catch(Exception e){throw new SignInFailure("Local account storage",e);}return account;
    }
    private void setTokens(Repository.Account a,JsonObject tokens,boolean refresh) throws IOException {
        String scope=tokens.has("scope")?JwtVerifier.value(tokens,"scope"):(refresh?a.scope:"");
        Set<String> grants=new HashSet<>(Arrays.asList(scope.split(" +")));
        if(!grants.contains("chatgpt.tokens.use.direct")||!grants.contains("resource.invoke"))throw new IOException("ChatGPT plan usage was not authorized. Reconnect and approve plan access.");
        String access=JwtVerifier.value(tokens,"access_token");
        if(access.isEmpty() || !"Bearer".equalsIgnoreCase(JwtVerifier.value(tokens,"token_type")))throw new IOException("Missing bearer access credential.");
        long lifetime=tokens.has("expires_in")?tokens.get("expires_in").getAsLong():0;
        if(lifetime<=0 || lifetime>86400*30)throw new IOException("Invalid access-token expiry.");
        String replacement=JwtVerifier.value(tokens,"refresh_token");
        a.accessToken=access;a.scope=scope;a.expiresAt=System.currentTimeMillis()+lifetime*1000;
        if(!replacement.isEmpty())a.refreshToken=replacement;
    }
    synchronized String access(Repository.Account a) throws IOException {
        if(!a.connected())throw new IOException("Connect your ChatGPT account on the phone first.");
        if(a.expiresAt-System.currentTimeMillis()<90000) {
            if(a.refreshToken.isEmpty())throw new IOException("Reconnect your ChatGPT account to renew access.");
            JsonObject tokens=form(TOKEN,new FormBody.Builder().add("grant_type","refresh_token").add("client_id",a.clientId).add("refresh_token",a.refreshToken).add("resource",PendingAuth.RESOURCE).build());
            setTokens(a,tokens,true);repo.save();
        }
        return a.accessToken;
    }
    JsonArray models(Repository.Account a) throws IOException {
        JsonObject catalog=json(new Request.Builder().url(PendingAuth.RESOURCE+"/models").header("Authorization","Bearer "+access(a)).build());
        JsonArray list=new JsonArray();
        if(catalog.has("models"))for(JsonElement item:catalog.getAsJsonArray("models"))if("list".equals(JwtVerifier.value(item.getAsJsonObject(),"visibility")))list.add(item);
        if(list.size()==0)throw new IOException("No available models. Check ChatGPT plan access for this app.");
        return list;
    }
    Call response(Repository.Account a,JsonObject body) throws IOException {
        return client.newCall(new Request.Builder().url(PendingAuth.RESOURCE+"/responses").header("Authorization","Bearer "+access(a)).header("Accept","text/event-stream").post(RequestBody.create(body.toString(),JSON)).build());
    }
    boolean signOut(Repository.Account a) {
        boolean revoked=a.refreshToken.isEmpty();
        try {
            if(!revoked) {
                JsonObject discovery=json(new Request.Builder().url(PendingAuth.ISSUER+"/.well-known/openid-configuration").build());
                String endpoint=JwtVerifier.value(discovery,"revocation_endpoint");
                java.net.URI uri=java.net.URI.create(endpoint);
                if(!"https".equals(uri.getScheme()) || !"auth.openai.com".equals(uri.getHost()) || uri.getUserInfo()!=null)throw new IOException("Unexpected revocation endpoint");
                Request request=new Request.Builder().url(endpoint).post(new FormBody.Builder().add("token",a.refreshToken).add("token_type_hint","refresh_token").add("client_id",a.clientId).build()).build();
                for(int i=0;i<2&&!revoked;i++)try(Response response=client.newCall(request).execute()) { revoked=response.code()==200; }
            }
        } catch(Exception ignored) { revoked=false; }
        a.accessToken="";a.refreshToken="";a.idToken="";a.expiresAt=0;
        try { repo.save(); }catch(IOException ignored) { return false; }
        return revoked;
    }
}
