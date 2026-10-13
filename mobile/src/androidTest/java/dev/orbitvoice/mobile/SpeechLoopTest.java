package dev.orbitvoice.mobile;

import android.Manifest;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SpeechLoopTest {
    @Test public void silenceRetriesStayActiveAndStopCancelsPendingRetry() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation().grantRuntimePermission(instrumentation.getTargetContext().getPackageName(),Manifest.permission.RECORD_AUDIO);
        UiDevice device=UiDevice.getInstance(instrumentation);
        try(ActivityScenario<VoiceHarnessActivity> scenario=ActivityScenario.launch(VoiceHarnessActivity.class)){
            AtomicBoolean active=new AtomicBoolean();long readyUntil=SystemClock.elapsedRealtime()+8000;
            do {
                device.wait(Until.findObject(By.text("Start test listening")),2000).click();
                scenario.onActivity(a->active.set(a.session.active()));
                if(!active.get())SystemClock.sleep(200);
            }while(!active.get()&&SystemClock.elapsedRealtime()<readyUntil);
            assertTrue("Speech service did not start",active.get());
            long until=SystemClock.elapsedRealtime()+14000;
            AtomicReference<String> lastStatus=new AtomicReference<>();
            while(SystemClock.elapsedRealtime()<until){
                scenario.onActivity(a->{active.set(a.session.active());lastStatus.set(a.lastStatus);});
                assertTrue("Conversation ended: "+lastStatus.get(),active.get());
                SystemClock.sleep(300);
            }
            device.findObject(By.text("Stop test listening")).click();
            assertTrue(device.wait(Until.hasObject(By.text("Stopped")),3000));
            SystemClock.sleep(3500);
            scenario.onActivity(a->active.set(a.session.active()));assertFalse("A pending retry restarted the microphone",active.get());
        }
    }
}
