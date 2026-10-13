package dev.orbitvoice.wear;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.widget.*;
import com.google.android.gms.wearable.*;
import com.google.gson.*;
import dev.orbitvoice.common.*;
import dev.orbitvoice.core.*;
import java.util.*;

public final class MainActivity extends Activity implements MessageClient.OnMessageReceivedListener {
    private String phone="",account="",conversation="",turnId="",controlId="";
    private Wire.Gate gate;private VoiceSession voice;private TextView status,transcript;private Button talk;
    private AudioTransfer audioTransfer;
    private boolean started,autoStart;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable timeout=()->{if(voice!=null)voice.fail("Phone did not respond. Check the connection, then refresh.");};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);autoStart=getIntent().getBooleanExtra("start_voice",false);
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);col.setBackgroundColor(Ui.BG);
        col.setPadding(Ui.dp(this,28),Ui.dp(this,20),Ui.dp(this,28),Ui.dp(this,18));setContentView(col);
        label(col,"ORBIT VOICE",11,Ui.MINT);
        status=label(col,"Connecting…",13,Ui.INK);status.setMaxLines(2);
        ScrollView body=new ScrollView(this);body.setFillViewport(true);col.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        transcript=new TextView(this);transcript.setTextSize(12);transcript.setTextColor(Ui.MUTED);transcript.setGravity(android.view.Gravity.CENTER);body.addView(transcript,new ScrollView.LayoutParams(-1,-2));
        voice=new VoiceSession(this,new VoiceSession.Listener() {
            public void heard(String question) { ask(question); }
            public void audio(byte[] pcm){askAudio(pcm);}
            public void status(String text) { status.setText(text);if(voice!=null&&voice.active())getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(talk!=null)talk.setText(voice!=null&&voice.active()?"Stop talking":"Start talking"); }
            public void transcript(String text) { transcript.setText(text); }
            public void cancel() {
                main.removeCallbacks(timeout);
                if(audioTransfer!=null){audioTransfer.close();audioTransfer=null;}
                if(!turnId.isEmpty())send(Wire.message("cancel",turnId,account,conversation,0,""),false);
                turnId="";gate=null;
            }
        });
        voice.language(getPreferences(0).getString("language",""));
        talk=Ui.button(col,"Start talking",true,()->{
            if(voice.active()){voice.pause("Conversation stopped");return;}
            if(account.isEmpty()||conversation.isEmpty()){status.setText("Phone setup needed");transcript.setText("Sign in and choose a model on your paired phone. Then select Options → Refresh connection.");}
            else voice.start();
        });
        LinearLayout.LayoutParams talkSize=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));talkSize.setMargins(0,Ui.dp(this,4),0,0);talk.setLayoutParams(talkSize);
        TextView options=label(col,"Options",12,Ui.MINT);options.setContentDescription("Conversation options");options.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,5));options.setOnClickListener(v->options());
    }
    private TextView label(LinearLayout col,String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setGravity(android.view.Gravity.CENTER);col.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private void options(){new AlertDialog.Builder(this).setTitle("Options").setItems(new String[]{"Interrupt and talk","Stop conversation","History","New conversation","Refresh connection","Speech language"},(d,w)->{
        switch(w){
            case 0:if(!account.isEmpty()&&!conversation.isEmpty())voice.start();break;
            case 1:voice.pause("Conversation stopped");break;
            case 2:control("history");break;
            case 3:control("new");break;
            case 4:discover();break;
            case 5:String[] labels={"Automatic (detect language)","System speech service","English speech service","Russian speech service","Spanish speech service","French speech service","German speech service"},tags={"","system","en-US","ru-RU","es-ES","fr-FR","de-DE"};
                new AlertDialog.Builder(this).setTitle("Speech language").setItems(labels,(dialog,index)->{voice.pause("Language changed");voice.language(tags[index]);getPreferences(0).edit().putString("language",tags[index]).apply();}).show();break;
        }
    }).show();}
    @Override protected void onStart() { super.onStart();started=true;Wearable.getMessageClient(this).addListener(this).addOnSuccessListener(v->{if(started&&!voice.active())discover();}).addOnFailureListener(e->status.setText("Wear OS services are unavailable.")); }
    private void discover() {
        voice.pause("Connecting to phone…");account="";conversation="";phone="";
        Wearable.getCapabilityClient(this).getCapability("orbit_voice_phone",CapabilityClient.FILTER_REACHABLE).addOnSuccessListener(info->{
            if(!started)return;
            for(Node node:info.getNodes())if(node.isNearby()){phone=node.getId();break;}
            if(phone.isEmpty()&&!info.getNodes().isEmpty())phone=info.getNodes().iterator().next().getId();
            if(phone.isEmpty()){status.setText("Phone not connected");transcript.setText("Install Orbit Voice on your paired Android phone, then refresh in Options.");}else control("state");
        }).addOnFailureListener(e->status.setText("Could not find your phone. Check Bluetooth and Wear OS pairing."));
    }
    private void control(String kind) {
        voice.pause("Loading…");
        if(phone.isEmpty()){status.setText("Phone unavailable. Refresh the connection.");return;}
        controlId=UUID.randomUUID().toString();send(Wire.message(kind,controlId,account,conversation,0,""),true);
        main.removeCallbacks(timeout);main.postDelayed(timeout,30000);
    }
    private void ask(String question) {
        if(phone.isEmpty()||account.isEmpty()||conversation.isEmpty()){voice.fail("Phone setup is incomplete. Refresh the connection.");return;}
        turnId=UUID.randomUUID().toString();gate=new Wire.Gate(turnId,account,conversation);send(Wire.message("ask",turnId,account,conversation,0,question),true);
        main.removeCallbacks(timeout);main.postDelayed(timeout,170000);
    }
    private void askAudio(byte[] pcm){
        if(phone.isEmpty()||account.isEmpty()||conversation.isEmpty()){voice.fail("Phone setup is incomplete. Refresh the connection.");return;}
        if(audioTransfer!=null)audioTransfer.close();turnId=UUID.randomUUID().toString();String expected=turnId;
        gate=new Wire.Gate(turnId,account,conversation);JsonObject request=Wire.message("audio",turnId,account,conversation,0,"");
        audioTransfer=new AudioTransfer(this,phone,request,pcm,()->main.post(()->{if(started&&expected.equals(turnId))voice.fail("Audio transfer failed. Check your phone connection and try again.");}));
        main.removeCallbacks(timeout);main.postDelayed(timeout,170000);
    }
    private void send(JsonObject m,boolean reportFailure) {
        if(phone.isEmpty())return;
        try { Wearable.getMessageClient(this).sendMessage(phone,Wire.REQUEST_PATH,Wire.bytes(m)).addOnFailureListener(e->{if(started&&reportFailure)voice.fail("Phone disconnected. Refresh the connection and try again.");}); }
        catch(RuntimeException e){if(reportFailure)voice.fail("Could not send this turn. Try a shorter question.");}
    }
    @Override public void onMessageReceived(MessageEvent event) {
        if(!event.getPath().equals(Wire.EVENT_PATH)||!event.getSourceNodeId().equals(phone))return;
        JsonObject m;try{m=Wire.parse(event.getData());}catch(RuntimeException e){return;}
        main.post(()->{
            if(!started)return;String id=JwtVerifier.value(m,"id"),kind=JwtVerifier.value(m,"kind"),text=JwtVerifier.value(m,"text");
            if(id.equals(controlId)) {
                main.removeCallbacks(timeout);controlId="";
                if(kind.equals("error")){account="";conversation="";status.setText(text);return;}
                if(!kind.equals("state")&&!kind.equals("history"))return;
                account=JwtVerifier.value(m,"account");conversation=JwtVerifier.value(m,"conversation");
                if(kind.equals("history"))Ui.dialog(this,"Current conversation",text);else transcript.setText(text);
                status.setText("Ready when you are");
                if(autoStart){autoStart=false;voice.start();}return;
            }
            if(gate==null||!gate.accepts(m))return;
            if(kind.equals("error")){main.removeCallbacks(timeout);voice.fail(text);return;}
            if(kind.equals("transcript")){voice.recognized(text,JwtVerifier.value(m,"language"));if(text.isEmpty()){main.removeCallbacks(timeout);turnId="";gate=null;}return;}
            if(kind.equals("delta")||kind.equals("done")){if(kind.equals("done"))main.removeCallbacks(timeout);voice.update(text,kind.equals("done"));}
        });
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(!intent.getBooleanExtra("start_voice",false))return;
        // The tile and the watch-face shortcut relaunch this task; only the first launch arrives in onCreate.
        if(account.isEmpty()||conversation.isEmpty()){autoStart=true;if(started)discover();}
        else if(!voice.active())voice.start();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);voice.permission(code,grants);}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);voice.activityResult(code,result,data);}
    @Override protected void onStop(){started=false;Wearable.getMessageClient(this).removeListener(this);if(!voice.inExternalRecognizer())voice.pause("Paused");super.onStop();}
    @Override protected void onDestroy(){voice.close();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
