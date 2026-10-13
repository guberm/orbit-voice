package dev.orbitvoice.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import dev.orbitvoice.common.*;

/** Debug-only microphone lifecycle harness. It never forwards speech to a network API. */
public final class VoiceHarnessActivity extends Activity {
    public VoiceSession session;
    public String lastStatus="Ready";
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout col=Ui.screen(this,false);TextView status=Ui.text(col,"Ready",16,Ui.INK);
        session=new VoiceSession(this,new VoiceSession.Listener(){
            public void heard(String text){session.update("OK.",true);}
            public void status(String text){lastStatus=text;status.setText(text);}
            public void transcript(String text){}
            public void cancel(){}
        });
        session.language("en-US");
        Ui.button(col,"Start test listening",true,()->session.start());
        Ui.button(col,"Stop test listening",false,()->session.pause("Stopped"));
    }
    @Override protected void onStop(){session.pause("Stopped");super.onStop();}
    @Override protected void onDestroy(){session.close();super.onDestroy();}
}
