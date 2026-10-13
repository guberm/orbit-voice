package dev.orbitvoice.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import com.google.gson.*;
import dev.orbitvoice.core.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Owns a bounded watch audio transfer and its inference while the phone UI is closed. */
public final class InferenceService extends Service {
    private Engine engine;private volatile String id="";private volatile boolean running,destroyed;private int activeStart;
    private volatile ChannelClient.Channel channel;private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("conversation","Voice conversations",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"conversation").setSmallIcon(dev.orbitvoice.common.R.drawable.orbit_monochrome).setContentTitle("Orbit Voice").setContentText("Recognizing and answering your watch. Use Stop on your watch to cancel.").setContentIntent(open).setOngoing(true).build();
        startForeground(41,notification);
        if(intent==null){if(!running)stopSelf();return START_NOT_STICKY;}
        ChannelClient.Channel incoming=intent.getParcelableExtra("channel");
        if(running){if(incoming!=null)Wearable.getChannelClient(this).close(incoming);return START_NOT_STICKY;}
        activeStart=startId;running=true;
        try{
            engine=Engine.get(this);
            if(incoming!=null){
                channel=incoming;id=incoming.getPath().substring(AudioWire.PATH.length()+1);
                new Thread(()->readAudio(incoming,startId),"orbit-watch-audio").start();
            }else{
                JsonObject request=Wire.parse(intent.getStringExtra("request").getBytes(java.nio.charset.StandardCharsets.UTF_8));id=JwtVerifier.value(request,"id");String node=intent.getStringExtra("node");
                if(!engine.ask(request,event->PhoneBridge.send(this,node,event),()->finish(startId)))reject(request,node,startId);
            }
        }catch(Exception e){finish(startId);}
        return START_NOT_STICKY;
    }
    private void readAudio(ChannelClient.Channel opened,int startId){
        ChannelClient client=Wearable.getChannelClient(this);Runnable deadline=()->client.close(opened);main.postDelayed(deadline,45000);
        try(InputStream input=Tasks.await(client.getInputStream(opened),8,TimeUnit.SECONDS)){
            AudioWire.Turn turn=AudioWire.read(input);if(!id.equals(JwtVerifier.value(turn.request,"id")))throw new IOException("Audio turn changed");
            if(destroyed){engine.cancel(id);return;}
            if(!engine.askAudio(turn.request,turn.pcm,event->PhoneBridge.send(this,opened.getNodeId(),event),()->finish(startId)))reject(turn.request,opened.getNodeId(),startId);
        }catch(Exception e){finish(startId);}
        finally{main.removeCallbacks(deadline);client.close(opened);channel=null;}
    }
    private void reject(JsonObject request,String node,int startId){
        try{PhoneBridge.send(this,node,Wire.message("error",JwtVerifier.value(request,"id"),JwtVerifier.value(request,"account"),JwtVerifier.value(request,"conversation"),0,"Another turn is still running. Try again in a moment."));}finally{finish(startId);}
    }
    private void finish(int startId){main.post(()->{if(activeStart!=startId)return;running=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}
    @Override public void onTimeout(int startId,int type){if(engine!=null)engine.cancel(id);stopSelf();}
    @Override public void onDestroy(){destroyed=true;if(running&&engine!=null)engine.cancel(id);ChannelClient.Channel opened=channel;if(opened!=null)Wearable.getChannelClient(this).close(opened);main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
