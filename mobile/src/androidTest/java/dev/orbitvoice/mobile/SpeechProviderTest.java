package dev.orbitvoice.mobile;

import android.os.Build;
import android.speech.*;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.orbitvoice.common.SpeechOptions;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SpeechProviderTest {
    @Test public void inspectAutomaticSupport() throws Exception {inspect("");}
    @Test public void inspectRussianSupport() throws Exception {inspect("ru-RU");}
    private void inspect(String preference) throws Exception {
        if(Build.VERSION.SDK_INT<34)return;
        var instrumentation=InstrumentationRegistry.getInstrumentation();var context=instrumentation.getTargetContext();
        AtomicReference<SpeechRecognizer> recognizer=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
        instrumentation.runOnMainSync(()->{
            SpeechRecognizer r=SpeechRecognizer.createSpeechRecognizer(context);recognizer.set(r);
            r.checkRecognitionSupport(SpeechOptions.recognitionIntent(preference,Build.VERSION.SDK_INT),context.getMainExecutor(),new RecognitionSupportCallback(){
                public void onSupportResult(RecognitionSupport support){Log.i("OrbitSpeechTest","mode="+preference+" installed="+support.getInstalledOnDeviceLanguages()+" online="+support.getOnlineLanguages()+" downloadable="+support.getSupportedOnDeviceLanguages());done.countDown();}
                public void onError(int error){Log.i("OrbitSpeechTest","mode="+preference+" support_error="+error);done.countDown();}
            });
        });
        try{assertTrue("Speech capability query timed out",done.await(10,TimeUnit.SECONDS));}
        finally{instrumentation.runOnMainSync(()->recognizer.get().destroy());}
    }
}
