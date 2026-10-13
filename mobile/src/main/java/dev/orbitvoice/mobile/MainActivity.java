package dev.orbitvoice.mobile;

import android.app.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import android.content.*;
import android.os.*;
import android.widget.*;
import com.google.android.gms.wearable.*;
import com.google.gson.*;
import dev.orbitvoice.common.*;
import dev.orbitvoice.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends ComponentActivity {
    private Engine engine;private VoiceSession voice;private AuthFlow auth;private boolean atHome;
    private TextView status,transcript;private Button talkButton;private String turnId="",exportText="";private Wire.Gate gate;
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { engine=Engine.get(this); }catch(IOException e) { LinearLayout col=Ui.screen(this,false);Ui.title(col,"Local storage unavailable",false);Ui.text(col,e.getMessage(),16,Ui.MUTED);return; }
        voice=new VoiceSession(this,new VoiceSession.Listener() {
            public void heard(String question) { sendQuestion(question); }
            public void status(String text) { if(status!=null)status.setText(text);if(voice!=null&&voice.active())getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(talkButton!=null)talkButton.setText(voice!=null&&voice.active()?"Interrupt and talk":"Start talking"); }
            public void transcript(String text) { if(transcript!=null)transcript.setText(text); }
            public void cancel() { if(!turnId.isEmpty())engine.cancel(turnId);turnId="";gate=null; }
        });
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(atHome)finish();else home();}});
        home();
    }
    private void clearScreen() { atHome=false;if(voice!=null)voice.pause("Paused");status=null;transcript=null;talkButton=null; }
    private void home() {
        clearScreen();atHome=true;LinearLayout col=Ui.screen(this,false);Ui.icon(col,dev.orbitvoice.common.R.mipmap.ic_launcher,104);Ui.title(col,"Orbit Voice",false);Ui.text(col,"A conversation, just a wrist away.",16,Ui.MUTED);
        Repository.Account a=engine.repo.active();
        if(a==null || !a.connected()) {
            Ui.text(col,"YOUR CHATGPT ACCOUNT",12,Ui.MINT);
            Ui.text(col,"Sign in on your phone. Talk on your watch.\n\nConversations stay on your phone. They do not appear in ChatGPT history. An eligible ChatGPT plan and app usage permission are required.",16,Ui.MUTED);
            Ui.button(col,"Continue with ChatGPT",true,()->signIn(a)).setId(R.id.sign_in);
        }else {
            Ui.text(col,a.label(),14,Ui.MINT);Ui.text(col,"CONNECTED · LOCAL HISTORY",12,Ui.MUTED);
            Ui.button(col,"Talk on this phone",true,this::conversation);
            Ui.button(col,a.model.isEmpty()?"Choose a model":"Model: "+a.model,false,this::models);
            TextView paired=Ui.text(col,"Checking watch connection…",13,Ui.MUTED);
            Wearable.getCapabilityClient(this).getCapability("orbit_voice_watch",CapabilityClient.FILTER_REACHABLE).addOnSuccessListener(c->paired.setText(c.getNodes().isEmpty()?"No watch found. Install the watch APK and pair it in the Wear OS companion app.":"Watch connected · open Orbit Voice on your watch")).addOnFailureListener(e->paired.setText("Watch services unavailable on this device."));
            Ui.button(col,"ChatGPT usage & permissions",false,()->Ui.link(this,"https://chatgpt.com/settings/usage"));
        }
        if(auth!=null) { Ui.text(col,"Sign-in is waiting in your browser.",14,Ui.MINT);Ui.button(col,"Cancel sign-in",false,()->{auth.close();auth=null;home();}); }
        Ui.button(col,"Conversation history",false,this::histories).setId(R.id.history);
        Ui.button(col,"Accounts",false,this::accounts);Ui.button(col,"Speech language",false,this::language);
        Ui.button(col,"How it works",false,()->Ui.dialog(this,"How it works","1. Continue with ChatGPT and approve plan usage.\n2. Choose an available model.\n3. Install the matching watch APK and pair your watch.\n4. Tap Start talking. After each spoken answer, the microphone opens for your next turn.\n\nUse Stop conversation or leave the screen to stop the microphone. History stays encrypted on this phone, separated by account. You can resume, export or delete it here.\n\nThis is a turn-based voice assistant using system speech recognition and speech output, not ChatGPT's native Voice mode. It is an independent app.")).setId(R.id.about);
    }
    private void signIn(Repository.Account returning) {
        if(engine.busy()) { Ui.dialog(this,"Conversation in progress","Stop the current turn before connecting an account.");return; }
        if(auth!=null)auth.close();
        try {
            auth=new AuthFlow(engine.repo,returning,engine.api,new AuthFlow.Listener() {
                public void done(Repository.Account a) { main.post(()->{if(isDestroyed())return;auth=null;home();models();}); }
                public void failed(String text) { main.post(()->{if(isDestroyed())return;auth=null;home();Ui.dialog(MainActivity.this,"Sign-in not completed",text);}); }
            });
            startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(auth.pending.authorizationUrl())));home();
        }catch(Exception e) { if(auth!=null)auth.close();auth=null;Ui.dialog(this,"Could not open sign-in","Install a browser, check your connection, and try again."); }
    }
    private void models() {
        Repository.Account a=engine.repo.active();if(a==null||!a.connected())return;
        ProgressDialog progress=ProgressDialog.show(this,"Available models","Loading from your account…",true,false);
        engine.worker.execute(()->{
            try {
                JsonArray list=engine.api.models(a);String[] labels=new String[list.size()],slugs=new String[list.size()];
                for(int i=0;i<list.size();i++){JsonObject m=list.get(i).getAsJsonObject();labels[i]=JwtVerifier.value(m,"display_name");slugs[i]=JwtVerifier.value(m,"slug");if(labels[i].isEmpty())labels[i]=slugs[i];}
                main.post(()->{if(isDestroyed())return;progress.dismiss();new AlertDialog.Builder(this).setTitle("Choose a model").setItems(labels,(d,which)->{
                    try { if(engine.repo.active()!=a)throw new IOException("Account changed. Load models again.");a.model=slugs[which];engine.repo.save();home(); }catch(IOException e){Ui.dialog(this,"Could not save model",e.getMessage());}
                }).setNegativeButton("Cancel",null).show();});
            }catch(Exception e) { main.post(()->{if(isDestroyed())return;progress.dismiss();Ui.dialog(this,"Models unavailable",Engine.friendly(e));}); }
        });
    }
    private void accounts() {
        clearScreen();LinearLayout col=Ui.screen(this,false);Ui.title(col,"Accounts",false);
        for(Repository.Account a:new ArrayList<>(engine.repo.config.accounts))Ui.button(col,a.label()+(a==engine.repo.active()?" · selected":""),a==engine.repo.active(),()->{
            try { if(engine.busy())throw new IOException("Wait for the cancelled turn to finish, then switch accounts.");engine.repo.select(a.key);home(); }catch(IOException e){Ui.dialog(this,"Could not switch",e.getMessage());}
        });
        Repository.Account a=engine.repo.active();Ui.button(col,"Add ChatGPT account",true,()->signIn(null));
        if(a!=null) {
            Ui.button(col,"Reconnect selected account",false,()->signIn(a));
            if(a.connected())Ui.button(col,"Disconnect selected account",false,()->new AlertDialog.Builder(this).setTitle("Disconnect account?").setMessage("The local history stays on this phone. OpenAI access will be revoked when possible.").setNegativeButton("Cancel",null).setPositiveButton("Disconnect",(d,w)->{
                if(engine.busy()){Ui.dialog(this,"Turn is stopping","Try again after the current request stops.");return;}
                engine.worker.execute(()->{boolean revoked=engine.api.signOut(a);main.post(()->{if(isDestroyed())return;home();if(!revoked)Ui.dialog(this,"Disconnected locally","Remote revocation was not confirmed. Disconnect Orbit Voice in ChatGPT Settings > Usage as well.");});});
            }).show());
        }
        Ui.button(col,"Back",false,this::home);
    }
    private void language() {
        String[] labels={"System language","English","Russian","Spanish","French","German"},tags={"","en-US","ru-RU","es-ES","fr-FR","de-DE"};
        new AlertDialog.Builder(this).setTitle("Speech language").setItems(labels,(d,w)->{getPreferences(0).edit().putString("language",tags[w]).apply();voice.language(tags[w]);Ui.dialog(this,"Speech language",labels[w]+" selected. The installed speech services must support this language.");}).show();
    }
    private void conversation() {
        clearScreen();Repository.Account a=engine.repo.active();
        if(a==null||!a.connected()||a.model.isEmpty()) { home();Ui.dialog(this,"Finish setup","Connect a ChatGPT account and choose an available model first.");return; }
        LinearLayout col=Ui.screen(this,false);Ui.icon(col,dev.orbitvoice.common.R.mipmap.ic_launcher,88);Ui.title(col,"Let's talk",false);
        status=Ui.text(col,"Ready when you are",18,Ui.MINT);transcript=Ui.text(col,"Tap to speak. The next turn starts automatically after each answer.",16,Ui.INK);voice.language(getPreferences(0).getString("language",""));
        talkButton=Ui.button(col,"Start talking",true,()->voice.start());Ui.button(col,"Stop conversation",false,()->voice.pause("Conversation stopped"));
        Ui.button(col,"New conversation",false,()->{voice.pause("Paused");try{if(engine.busy())throw new IOException("Wait for the cancelled turn to stop, then try again.");engine.repo.newConversation(a);conversation();}catch(IOException e){Ui.dialog(this,"Could not start new conversation",e.getMessage());}});
        Ui.button(col,"Back",false,this::home);
    }
    private void sendQuestion(String question) {
        try {
            Repository.Account a=engine.repo.active();Conversation c=engine.repo.current(a);turnId=UUID.randomUUID().toString();gate=new Wire.Gate(turnId,a.key,c.id);
            if(!engine.ask(Wire.message("ask",turnId,a.key,c.id,0,question),event->main.post(()->{
                if(gate==null||!gate.accepts(event))return;String kind=JwtVerifier.value(event,"kind"),text=JwtVerifier.value(event,"text");if(kind.equals("error"))voice.fail(text);else voice.update(text,kind.equals("done"));
            }),()->{}))voice.fail("Another turn is still running. Tap to try again in a moment.");
        }catch(Exception e){voice.fail(Engine.friendly(e));}
    }
    private void histories() {
        clearScreen();LinearLayout col=Ui.screen(this,false);Ui.title(col,"Conversation history",false);Ui.text(col,"Encrypted on this phone · separate from ChatGPT",14,Ui.MUTED);Repository.Account a=engine.repo.active();
        try {
            List<Conversation> list=a==null?Collections.emptyList():engine.repo.histories(a);if(a!=null)list.removeIf(c->c.turns.isEmpty());
            if(list.isEmpty())Ui.text(col,"No conversations yet. Connect an account and start talking.",16,Ui.INK);
            for(Conversation c:list)Ui.button(col,c.title,false,()->detail(a,c));
        }catch(IOException e){Ui.text(col,e.getMessage(),16,Ui.INK);}
        Ui.button(col,"Back",false,this::home);
    }
    private void detail(Repository.Account account,Conversation c) {
        clearScreen();LinearLayout col=Ui.screen(this,false);Ui.title(col,c.title,false);
        Ui.button(col,"Resume conversation",true,()->{try{if(engine.busy())throw new IOException("Wait for the current request to stop.");engine.repo.resume(account,c.id);conversation();}catch(IOException e){Ui.dialog(this,"Could not resume",e.getMessage());}});
        Ui.button(col,"Export Markdown",false,()->{exportText=c.markdown();startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/markdown").putExtra(Intent.EXTRA_TITLE,"orbit-voice-"+c.id.substring(0,8)+".md"),805);});
        Ui.button(col,"Delete conversation",false,()->new AlertDialog.Builder(this).setTitle("Delete conversation?").setMessage("This removes the local copy. Existing exports remain.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{if(engine.busy())throw new IOException("Stop the current turn before deleting history.");engine.repo.delete(account,c.id);histories();}catch(IOException e){Ui.dialog(this,"Could not delete",e.getMessage());}}).show());
        for(Conversation.Turn t:c.turns){Ui.text(col,"YOU",12,Ui.MINT);Ui.text(col,t.question,16,Ui.INK);Ui.text(col,"ORBIT VOICE",12,Ui.MINT);Ui.text(col,t.answer,16,Ui.INK);}Ui.button(col,"Back",false,this::histories);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) { super.onRequestPermissionsResult(code,permissions,grants);if(voice!=null)voice.permission(code,grants); }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);if(voice!=null&&voice.activityResult(code,result,data))return;
        if(code==805&&result==RESULT_OK&&data!=null&&data.getData()!=null)try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException();out.write(exportText.getBytes(StandardCharsets.UTF_8));Ui.dialog(this,"Export saved","Conversation saved to the location you selected.");}catch(IOException e){Ui.dialog(this,"Export failed","Could not write the selected file. Try a different destination.");}finally{exportText="";}
    }
    @Override protected void onStop() { if(voice!=null&&!voice.inExternalRecognizer())voice.pause("Paused while app is in the background");super.onStop(); }
    @Override protected void onDestroy() { if(auth!=null)auth.close();if(voice!=null)voice.close();main.removeCallbacksAndMessages(null);super.onDestroy(); }
}
