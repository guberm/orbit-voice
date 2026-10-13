package dev.orbitvoice.mobile;

import android.content.Context;
import android.util.Log;
import com.google.gson.*;
import dev.orbitvoice.core.*;
import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** One inference at a time, account-pinned context, cancellation and completion-only commit. */
final class Engine {
    interface Decoder {WhisperAsr.Result decode(byte[] pcm,BooleanSupplier cancelled) throws IOException;}
    private Decoder decoder;
    private final LinkedHashSet<String> cancelledTurns=new LinkedHashSet<>();
    private static Engine instance;
    final Repository repo;final OpenAi api;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile Call call;private volatile String requestId="";private volatile boolean busy;
    private Engine(Context c) throws IOException { repo=new Repository(c);api=new OpenAi(repo);decoder=new WhisperAsr(c)::transcribe; }
    Engine(Repository repo,OpenAi api) { this.repo=repo;this.api=api; }
    Engine(Repository repo,OpenAi api,Decoder decoder){this(repo,api);this.decoder=decoder;}
    synchronized boolean askAudio(JsonObject request,byte[] pcm,Consumer<JsonObject> events,Runnable finished){return submit(request,pcm,events,finished);}
    static synchronized Engine get(Context c) throws IOException { if(instance==null)instance=new Engine(c.getApplicationContext());return instance; }
    boolean busy() { return busy; }
    synchronized void cancel(String id) {
        if(id==null||id.isEmpty())return;cancelledTurns.add(id);while(cancelledTurns.size()>128)cancelledTurns.remove(cancelledTurns.iterator().next());
        if(requestId.equals(id)) { requestId="";if(call!=null)call.cancel(); }
    }
    synchronized boolean ask(JsonObject request,Consumer<JsonObject> events,Runnable finished) {
        return submit(request,null,events,finished);
    }
    private synchronized boolean submit(JsonObject request,byte[] pcm,Consumer<JsonObject> events,Runnable finished){
        String id=JwtVerifier.value(request,"id"),key=JwtVerifier.value(request,"account"),conversation=JwtVerifier.value(request,"conversation");
        if(cancelledTurns.contains(id)){finished.run();return true;}
        if(busy)return false;busy=true;
        requestId=id;
        worker.execute(()->{
            int[] sequence={0};long[] last={0};
            Log.i("OrbitEngine","turn start id="+id+" pcm="+(pcm==null?0:pcm.length));
            try {
                Repository.Account a=repo.active();
                if(a==null || !a.key.equals(key))throw new IOException("Account changed. Refresh the watch and reconnect if needed.");
                Conversation c=repo.current(a);
                if(!c.id.equals(conversation))throw new IOException("Conversation changed. Refresh before speaking again.");
                if(!requestId.equals(id))throw new CancellationException();
                final String question;
                if(pcm!=null){
                    if(decoder==null)throw new IOException("Offline speech is unavailable.");
                    WhisperAsr.Result result=decoder.decode(pcm,()->!requestId.equals(id));if(!requestId.equals(id))throw new CancellationException();
                    Log.i("OrbitEngine","decoded id="+id+" lang="+result.language+" chars="+result.text.length());question=result.text;JsonObject transcript=Wire.message("transcript",id,key,conversation,++sequence[0],question);transcript.addProperty("language",result.language);events.accept(transcript);
                    if(question.isEmpty())return;
                }else question=JwtVerifier.value(request,"text");
                Call pending=api.response(a,c.request(a.model,question));long httpStart=System.currentTimeMillis();final boolean[] firstToken={false};
                synchronized(this) { if(!requestId.equals(id))throw new IOException("Cancelled");call=pending; }
                try(Response response=pending.execute()) {
                    if(!response.isSuccessful())throw new IOException(OpenAi.httpError(response.code()));
                    if(response.body()==null)throw new IOException("Empty response stream.");
                    StreamReader.read(response.body().charStream(),update->{
                        if(!requestId.equals(id))throw new CancellationException();
                        if(!firstToken[0]){firstToken[0]=true;Log.i("OrbitEngine","http first token ms="+(System.currentTimeMillis()-httpStart));}
                        if(update.kind.equals("done")) {
                            synchronized(this) {
                                if(!requestId.equals(id))throw new CancellationException();
                                c.commit(question,update.text,update.output);
                                try { repo.saveConversation(a,c); }catch(IOException e) { throw new UncheckedIOException(e); }
                                Log.i("OrbitEngine","history saved ms="+(System.currentTimeMillis()-httpStart));
                            }
                            Log.i("OrbitEngine","http done ms="+(System.currentTimeMillis()-httpStart));
                            long eventAt=System.currentTimeMillis();
                            events.accept(Wire.message("done",id,key,conversation,++sequence[0],update.text));
                            Log.i("OrbitEngine","done event sent ms="+(System.currentTimeMillis()-eventAt));
                        }else if(System.currentTimeMillis()-last[0]>=150) {
                            last[0]=System.currentTimeMillis();events.accept(Wire.message("delta",id,key,conversation,++sequence[0],update.text));
                        }
                    });
                }
            }catch(Exception e) {
                Log.w("OrbitEngine","turn failed id="+id,e);
                if(requestId.equals(id))events.accept(Wire.message("error",id,key,conversation,++sequence[0],friendly(e)));
            }finally {
                synchronized(this) { call=null;requestId="";busy=false; }
                finished.run();
            }
        });return true;
    }
    static String friendly(Exception e) {
        String message=e.getMessage()==null?"":e.getMessage();
        if(message.contains("subscription_sharing_usage"))return "ChatGPT plan usage is unavailable or its limit was reached. Check ChatGPT Settings > Usage.";
        if(message.contains("response_too_long"))return "The answer was too long for a voice turn. Ask for a shorter answer.";
        if(message.contains("Stream interrupted"))return "Connection interrupted. This turn was not saved. Tap to try again.";
        if(e instanceof java.net.SocketTimeoutException)return "The request timed out. Check your connection and try again.";
        if(e instanceof IOException || e instanceof IllegalArgumentException)return message;
        return "Could not complete this turn. Check your connection and try again.";
    }
    JsonObject state(String id,String kind) throws IOException {
        Repository.Account a=repo.active();
        if(a==null || !a.connected())return Wire.message("error",id,"","",0,"Open Orbit Voice on your phone and connect a ChatGPT account.");
        Conversation c=repo.current(a);
        String text=kind.equals("history")?c.markdown():a.email+"\n"+(a.model.isEmpty()?"Choose a model on your phone.":a.model);
        if(text.length()>10000)text="Earlier turns are on your phone.\n\n"+text.substring(text.length()-9900);
        return Wire.message(kind.equals("history")?"history":"state",id,a.key,c.id,0,text);
    }
}
