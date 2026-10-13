package dev.orbitvoice.mobile;

import android.content.*;
import android.util.Log;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import com.google.gson.*;
import dev.orbitvoice.core.*;
import java.util.concurrent.TimeUnit;

/** Watch entry point. It never starts a foreground service from the background: the resident
 *  InferenceService owns every audio and text turn, so the phone UI may stay closed. */
public final class PhoneBridge extends WearableListenerService {
    @Override public void onChannelOpened(ChannelClient.Channel channel){
        if(!channel.getPath().startsWith(AudioWire.PATH+"/"))return;
        try{
            java.util.UUID.fromString(channel.getPath().substring(AudioWire.PATH.length()+1));
            if(!InferenceService.deliverChannel(channel)){Log.w("OrbitBridge","channel refused, resident="+InferenceService.resident());Wearable.getChannelClient(this).close(channel);}
        }catch(RuntimeException e){Log.w("OrbitBridge","channel rejected",e);Wearable.getChannelClient(this).close(channel);}
    }
    static void send(Context c,String node,JsonObject message) {
        try { Tasks.await(Wearable.getMessageClient(c).sendMessage(node,Wire.EVENT_PATH,Wire.bytes(message)),8,TimeUnit.SECONDS); }
        catch(Exception e) { throw new IllegalStateException("Watch disconnected"); }
    }
    @Override public void onMessageReceived(MessageEvent event) {
        if(!event.getPath().equals(Wire.REQUEST_PATH))return;
        JsonObject m;try{m=Wire.parse(event.getData());}catch(RuntimeException e){return;}
        String kind=JwtVerifier.value(m,"kind"),id=JwtVerifier.value(m,"id"),node=event.getSourceNodeId();
        if(!"state".equals(kind))Log.i("OrbitBridge","message "+kind+" id="+id+" resident="+InferenceService.resident());
        try {
            Engine engine=Engine.get(this);
            if(kind.equals("cancel")){engine.cancel(id);return;}
            if(kind.equals("ask")) {
                if(engine.busy()) { send(this,node,Wire.message("error",id,JwtVerifier.value(m,"account"),JwtVerifier.value(m,"conversation"),0,"Another turn is in progress. Try again in a moment."));return; }
                if(InferenceService.deliverRequest(m,node))return;
                send(this,node,Wire.message("error",id,JwtVerifier.value(m,"account"),JwtVerifier.value(m,"conversation"),0,InferenceService.arm(this)?"Orbit Voice is starting on your phone. Tap again in a moment.":"Open Orbit Voice on your phone once, then retry from the watch."));return;
            }
            if(!kind.equals("state")&&!kind.equals("history")&&!kind.equals("new")&&!kind.equals("resume"))return;
            engine.worker.execute(()->{
                try {
                    Repository.Account a=engine.repo.active();
                    if(kind.equals("new") || kind.equals("resume")) {
                        if(a==null||!a.key.equals(JwtVerifier.value(m,"account")))throw new IllegalArgumentException("Account changed. Refresh the watch.");
                        if(kind.equals("new"))engine.repo.newConversation(a);else engine.repo.resume(a,JwtVerifier.value(m,"conversation"));
                    }
                    send(this,node,engine.state(id,kind));
                }catch(Exception e){Log.w("OrbitBridge","state failed",e);try{send(this,node,Wire.message("error",id,"","",0,"Could not read the current conversation. Open Orbit Voice on your phone."));}catch(RuntimeException ignored){}}
            });
        }catch(Exception e){Log.w("OrbitBridge","request failed",e);}
    }
}
