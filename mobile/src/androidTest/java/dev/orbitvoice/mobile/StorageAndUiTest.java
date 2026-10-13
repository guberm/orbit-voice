package dev.orbitvoice.mobile;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import dev.orbitvoice.common.Vault;
import dev.orbitvoice.core.Conversation;
import com.google.gson.JsonArray;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Runs on a dedicated emulator. Storage tests use isolated files, never account credentials. */
@RunWith(AndroidJUnit4.class)
public final class StorageAndUiTest {
    private Context context;
    @Before public void isolatedStorage() {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File isolated=new File(target.getCacheDir(),"storage-test-"+UUID.randomUUID());assertTrue(isolated.mkdirs());
        context=new ContextWrapper(target){@Override public File getFilesDir(){return isolated;}@Override public Context getApplicationContext(){return this;}};
    }
    private Repository.Account account(String key) { Repository.Account a=new Repository.Account();a.key=key;a.subject=key;a.clientId="oaiapp_"+key;a.email=key+"@example.invalid";return a; }
    @Test public void encryptedFileRoundTripHidesPlaintext() throws Exception {
        Vault vault=new Vault(context);String text="Private conversation Привет 🌍";vault.write("sample",text);assertEquals(text,vault.read("sample",""));
        String disk=new String(Files.readAllBytes(new File(context.getFilesDir(),"sample.enc").toPath()),StandardCharsets.UTF_8);assertFalse(disk.contains("Private conversation"));
    }
    @Test public void tamperingCannotSilentlyResetHistory() throws Exception {
        Vault vault=new Vault(context);vault.write("sample","sensitive");File file=new File(context.getFilesDir(),"sample.enc");byte[] bytes=Files.readAllBytes(file.toPath());bytes[bytes.length-1]^=1;Files.write(file.toPath(),bytes);
        assertThrows(IOException.class,()->vault.read("sample","empty"));
    }
    @Test public void swappingEncryptedAccountFilesIsRejected() throws Exception {
        Vault vault=new Vault(context);vault.write("first","private");Files.copy(new File(context.getFilesDir(),"first.enc").toPath(),new File(context.getFilesDir(),"second.enc").toPath());assertThrows(IOException.class,()->vault.read("second",""));
    }
    @Test public void accountHistoriesRemainSeparatedAfterRestart() throws Exception {
        Repository repo=new Repository(context);Repository.Account first=account("first"),second=account("second");repo.config.accounts.add(first);repo.config.accounts.add(second);repo.select(first.key);
        Conversation c=repo.current(first);c.commit("Remember Mike","Hello Mike",new JsonArray());repo.saveConversation(first,c);
        assertTrue(repo.current(second).turns.isEmpty());
        Repository restored=new Repository(context);assertEquals("Remember Mike",restored.histories(first).get(0).turns.get(0).question);assertTrue(restored.histories(second).get(0).turns.isEmpty());
    }
    @Test public void resumeRestoresPriorContextAndDeleteRemovesIt() throws Exception {
        Repository repo=new Repository(context);Repository.Account a=account("first");repo.config.accounts.add(a);repo.select(a.key);Conversation c=repo.current(a);c.commit("My name is Mike","Hi Mike",new JsonArray());repo.saveConversation(a,c);
        repo.newConversation(a);repo.resume(a,c.id);assertEquals(3,repo.current(a).request("model","Who am I?").getAsJsonArray("input").size());repo.delete(a,c.id);assertFalse(repo.histories(a).stream().anyMatch(v->v.id.equals(c.id)));
    }
    private UiDevice launch() {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();target.startActivity(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());assertTrue(device.wait(Until.hasObject(By.text("Continue with ChatGPT")),8000));return device;
    }
    private UiObject2 visible(UiDevice device,String text) throws Exception {
        for(int i=0;i<5;i++) { UiObject2 element=device.findObject(By.text(text));if(element!=null)return element;device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,device.getDisplayWidth()/2,device.getDisplayHeight()/3,20); }
        throw new AssertionError("Missing UI: "+text);
    }
    @Test public void unsignedHistoryShowsActualEmptyState() throws Exception {
        UiDevice d=launch();visible(d,"Conversation history").click();assertTrue(d.wait(Until.hasObject(By.textContains("No conversations yet")),4000));d.pressBack();assertTrue(d.wait(Until.hasObject(By.text("Continue with ChatGPT")),4000));
    }
    @Test public void aboutExplainsVoiceAndLocalHistory() throws Exception {
        UiDevice d=launch();visible(d,"How it works").click();assertTrue(d.wait(Until.hasObject(By.textContains("History stays encrypted")),4000));visible(d,"OK").click();
    }
}
