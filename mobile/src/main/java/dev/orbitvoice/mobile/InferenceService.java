package dev.orbitvoice.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import com.google.gson.*;
import dev.orbitvoice.core.*;

/** Keeps a watch-initiated network turn alive when the phone UI is closed. */
public final class InferenceService extends Service {
    private Engine engine;private String id="";private boolean running;
    @Override public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("conversation","Voice conversations",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"conversation").setSmallIcon(dev.orbitvoice.common.R.drawable.orbit_monochrome).setContentTitle("Orbit Voice").setContentText("Answering your watch. Stop the conversation on your watch to cancel.").setContentIntent(open).setOngoing(true).build();
        startForeground(41,notification);
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        try {
            engine=Engine.get(this);JsonObject request=Wire.parse(intent.getStringExtra("request").getBytes(java.nio.charset.StandardCharsets.UTF_8));String node=intent.getStringExtra("node");
            if(running) { PhoneBridge.send(this,node,Wire.message("error",JwtVerifier.value(request,"id"),JwtVerifier.value(request,"account"),JwtVerifier.value(request,"conversation"),0,"Another turn is still running."));return START_NOT_STICKY; }
            id=JwtVerifier.value(request,"id");running=true;
            if(!engine.ask(request,event->PhoneBridge.send(this,node,event),()->{running=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();})) {
                running=false;PhoneBridge.send(this,node,Wire.message("error",id,JwtVerifier.value(request,"account"),JwtVerifier.value(request,"conversation"),0,"Another turn is still running."));stopSelf();
            }
        }catch(Exception e){if(!running)stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId,int type){if(engine!=null)engine.cancel(id);stopSelf();}
    @Override public void onDestroy(){if(running&&engine!=null)engine.cancel(id);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
