package dev.orbitvoice.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.Log;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import com.google.gson.*;
import dev.orbitvoice.core.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Resident companion service: the watch is answered while the phone UI is closed.
 *  It is armed once from a visible activity or an exempt broadcast, then stays in the
 *  foreground, so a watch turn never has to start a service from the background. */
public final class InferenceService extends Service {
    private static final String CHANNEL="conversation";private static final int NOTIFICATION=41;
    private static final String READY="Ready for your watch. Start talking on your watch at any time.";
    private static final String WORKING="Recognizing and answering your watch. Use Stop on your watch to cancel.";
    private static volatile InferenceService live;
    private Engine engine;private volatile String id="";private volatile boolean working,destroyed;private int turn;
    private volatile ChannelClient.Channel channel;private final Handler main=new Handler(Looper.getMainLooper());

    /** Starts the service from a user-visible activity or an exempt broadcast. */
    static boolean arm(Context context){
        try{context.startForegroundService(new Intent(context,InferenceService.class));return true;}
        catch(RuntimeException e){Log.w("OrbitService","arm refused from a background context",e);return false;}
    }
    static boolean resident(){return live!=null;}
    private Engine engine() throws IOException { if(engine==null)engine=Engine.get(this);return engine; }
    static boolean deliverRequest(JsonObject request,String node){InferenceService service=live;return service!=null&&service.beginRequest(request,node);}
    static boolean deliverChannel(ChannelClient.Channel opened){InferenceService service=live;return service!=null&&service.beginChannel(opened);}
    private Notification.Builder notification(){return new Notification.Builder(this,CHANNEL).setSmallIcon(dev.orbitvoice.common.R.drawable.orbit_monochrome).setContentTitle("Orbit Voice").setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));}
    private void foreground(String text){startForeground(NOTIFICATION,notification().setContentText(text).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);}
    @Override public void onCreate(){
        super.onCreate();live=this;Log.i("OrbitService","created");
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Voice conversations",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){Log.i("OrbitService","start flags="+flags+" intent="+(intent!=null));foreground(working?WORKING:READY);return START_STICKY;}
    private boolean beginRequest(JsonObject request,String node){
        if(working){Log.w("OrbitService","request refused, a turn is running");return false;}
        int expected=++turn;working=true;id=JwtVerifier.value(request,"id");foreground(WORKING);Log.i("OrbitService","text turn id="+id);
        try{
            engine();
            if(!engine.ask(request,event->PhoneBridge.send(this,node,event),()->finish(expected))){Log.w("OrbitService","engine refused id="+id);working=false;foreground(READY);return false;}
        }catch(Exception e){Log.w("OrbitService","ask failed id="+id,e);working=false;foreground(READY);return false;}
        return true;
    }
    private boolean beginChannel(ChannelClient.Channel opened){
        if(working){Log.w("OrbitService","channel refused, a turn is running");return false;}
        int expected=++turn;working=true;channel=opened;id=opened.getPath().substring(AudioWire.PATH.length()+1);Log.i("OrbitService","audio channel id="+id);
        try{engine();}catch(IOException e){Log.w("OrbitService","engine unavailable for audio",e);working=false;channel=null;foreground(READY);return false;}
        foreground(WORKING);
        new Thread(()->readAudio(opened,expected),"orbit-watch-audio").start();
        return true;
    }
    private void readAudio(ChannelClient.Channel opened,int expected){
        ChannelClient client=Wearable.getChannelClient(this);Runnable deadline=()->client.close(opened);main.postDelayed(deadline,45000);
        long accepted=System.currentTimeMillis();
        try(InputStream input=Tasks.await(client.getInputStream(opened),8,TimeUnit.SECONDS)){
            AudioWire.Turn audio=AudioWire.read(input);
            Log.i("OrbitService","audio read id="+JwtVerifier.value(audio.request,"id")+" bytes="+audio.pcm.length+" transferMs="+(System.currentTimeMillis()-accepted));
            if(!id.equals(JwtVerifier.value(audio.request,"id")))throw new IOException("Audio turn changed");
            Engine active=engine();
            if(destroyed||expected!=turn){active.cancel(id);return;}
            if(!active.askAudio(audio.request,audio.pcm,event->PhoneBridge.send(this,opened.getNodeId(),event),()->finish(expected)))reject(audio.request,opened.getNodeId(),expected);
        }catch(Exception e){Log.w("OrbitService","audio turn failed id="+id,e);finish(expected);}
        finally{main.removeCallbacks(deadline);client.close(opened);channel=null;}
    }
    private void reject(JsonObject request,String node,int expected){
        try{PhoneBridge.send(this,node,Wire.message("error",JwtVerifier.value(request,"id"),JwtVerifier.value(request,"account"),JwtVerifier.value(request,"conversation"),0,"Another turn is still running. Try again in a moment."));}finally{finish(expected);}
    }
    private void finish(int expected){main.post(()->{if(expected!=turn||destroyed)return;working=false;id="";foreground(READY);});}
    @Override public void onTimeout(int startId,int type){if(engine!=null)engine.cancel(id);finish(turn);}
    @Override public void onDestroy(){
        destroyed=true;live=null;Log.i("OrbitService","destroyed");if(working&&engine!=null)engine.cancel(id);
        ChannelClient.Channel opened=channel;if(opened!=null)Wearable.getChannelClient(this).close(opened);
        main.removeCallbacksAndMessages(null);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
