package dev.orbitvoice.mobile;

import android.content.Context;
import com.google.gson.*;
import dev.orbitvoice.common.Vault;
import dev.orbitvoice.core.*;
import java.io.IOException;
import java.util.*;

final class Repository {
    static final Gson JSON=new Gson();
    static final class Account {
        String key,clientId,subject,email,accessToken="",refreshToken="",idToken="",scope="",model="",current="";
        long expiresAt;
        boolean connected() { return !accessToken.isEmpty() && scope.contains("chatgpt.tokens.use.direct"); }
        String label() { return email+" · "+clientId.substring(Math.max(0,clientId.length()-6)); }
    }
    static final class Config { String hostId="urn:uuid:"+UUID.randomUUID(),selected=""; List<Account> accounts=new ArrayList<>(); }
    private final Vault vault;
    final Config config;
    Repository(Context context) throws IOException {
        vault=new Vault(context);config=JSON.fromJson(vault.read("accounts",JSON.toJson(new Config())),Config.class);save();
    }
    synchronized void save() throws IOException { vault.write("accounts",JSON.toJson(config)); }
    synchronized Account active() { for(Account a:config.accounts) if(a.key.equals(config.selected)) return a;return null; }
    synchronized void select(String key) throws IOException { config.selected=key;save(); }
    synchronized List<Conversation> histories(Account a) throws IOException {
        Conversation[] list=JSON.fromJson(vault.read("history_"+a.key,"[]"),Conversation[].class);return new ArrayList<>(Arrays.asList(list));
    }
    synchronized Conversation current(Account a) throws IOException {
        for(Conversation c:histories(a)) if(c.id.equals(a.current)) return c;
        Conversation c=new Conversation();a.current=c.id;saveConversation(a,c);save();return c;
    }
    synchronized void saveConversation(Account a,Conversation c) throws IOException {
        List<Conversation> list=histories(a);list.removeIf(old->old.id.equals(c.id));list.add(0,c);vault.write("history_"+a.key,JSON.toJson(list));
    }
    synchronized Conversation newConversation(Account a) throws IOException { a.current="";return current(a); }
    synchronized void resume(Account a,String id) throws IOException {
        for(Conversation c:histories(a)) if(c.id.equals(id)) { a.current=id;save();return; }
        throw new IOException("Conversation no longer exists.");
    }
    synchronized void delete(Account a,String id) throws IOException {
        List<Conversation> list=histories(a);list.removeIf(c->c.id.equals(id));vault.write("history_"+a.key,JSON.toJson(list));
        if(a.current.equals(id)) { a.current="";save(); }
    }
}
