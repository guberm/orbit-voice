package dev.orbitvoice.mobile;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import dev.orbitvoice.core.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** HTTPS fixture only. No real accounts, tokens or OpenAI requests are used. */
@RunWith(AndroidJUnit4.class)
public final class PipelineTest {
    private MockWebServer server;private Repository repo;private Repository.Account account;private OpenAi api;private Engine engine;private Context testContext;
    @Before public void setup() throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();File isolated=new File(target.getCacheDir(),"pipeline-test-"+UUID.randomUUID());assertTrue(isolated.mkdirs());
        Context c=new ContextWrapper(target){@Override public File getFilesDir(){return isolated;}@Override public Context getApplicationContext(){return this;}};
        testContext=c;repo=new Repository(c);account=new Repository.Account();account.key="synthetic";account.subject="synthetic";account.email="synthetic@example.invalid";account.clientId="oaiapp_synthetic";account.accessToken="synthetic-access";account.scope="resource.invoke chatgpt.tokens.use.direct";account.model="synthetic-model";account.expiresAt=System.currentTimeMillis()+3600000;repo.config.accounts.add(account);repo.select(account.key);
        HeldCertificate cert=new HeldCertificate.Builder().addSubjectAlternativeName("localhost").commonName("localhost").build();
        HandshakeCertificates serverTls=new HandshakeCertificates.Builder().heldCertificate(cert).build(),clientTls=new HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate()).build();
        server=new MockWebServer();server.useHttps(serverTls.sslSocketFactory(),false);server.start();
        OkHttpClient client=new OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager()).addInterceptor(chain->{Request r=chain.request();return chain.proceed(r.newBuilder().url(r.url().newBuilder().scheme("https").host("localhost").port(server.getPort()).build()).build());}).build();
        api=new OpenAi(repo,client);engine=new Engine(repo,api);
    }
    @After public void cleanup() throws Exception { engine.worker.shutdownNow();api.client.dispatcher().cancelAll();server.shutdown(); }
    private String event(String json){return "data: "+json+"\n\n";}
    private String success(String answer){return event("{\"type\":\"response.output_text.delta\",\"delta\":\""+answer+"\"}")+event("{\"type\":\"response.completed\",\"response\":{\"output\":[]}}");}
    private List<JsonObject> ask(String question) throws Exception {
        List<JsonObject> events=new CopyOnWriteArrayList<>();CountDownLatch done=new CountDownLatch(1);String id=UUID.randomUUID().toString(),conv=repo.current(account).id;
        assertTrue(engine.ask(Wire.message("ask",id,account.key,conv,0,question),events::add,done::countDown));assertTrue(done.await(12,TimeUnit.SECONDS));return events;
    }
    @Test public void modelCatalogReadsFiniteJsonBody() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"models\":[{\"slug\":\"visible\",\"display_name\":\"Visible\",\"visibility\":\"list\"},{\"slug\":\"hidden\",\"visibility\":\"hidden\"}]}"));
        JsonArray models=api.models(account);assertEquals(1,models.size());assertEquals("visible",models.get(0).getAsJsonObject().get("slug").getAsString());assertEquals("/v1/models",server.takeRequest().getPath());
    }
    @Test public void twoStreamedTurnsPersistAndReplayContext() throws Exception {
        server.enqueue(new MockResponse().setBody(success("Hello Mike.")));assertEquals("done",JwtVerifier.value(ask("My name is Mike.").get(1),"kind"));
        server.enqueue(new MockResponse().setBody(success("Your name is Mike.")));ask("What is my name?");
        server.takeRequest();RecordedRequest next=server.takeRequest();JsonObject body=JsonParser.parseString(next.getBody().readUtf8()).getAsJsonObject();assertEquals(3,body.getAsJsonArray("input").size());assertFalse(body.get("store").getAsBoolean());assertTrue(body.get("stream").getAsBoolean());assertEquals(2,new Repository(testContext).histories(account).get(0).turns.size());
    }
    @Test public void interruptedStreamDoesNotSavePartialTurn() throws Exception {
        server.enqueue(new MockResponse().setBody(event("{\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}")));
        List<JsonObject> events=ask("Question");assertEquals("error",JwtVerifier.value(events.get(events.size()-1),"kind"));assertTrue(repo.current(account).turns.isEmpty());
    }
    @Test public void lateUsageFailureDoesNotSavePartialTurn() throws Exception {
        server.enqueue(new MockResponse().setBody(event("{\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}")+event("{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}")));
        List<JsonObject> events=ask("Question");assertEquals("error",JwtVerifier.value(events.get(events.size()-1),"kind"));assertTrue(repo.current(account).turns.isEmpty());
    }
    @Test public void accountChangeRejectsTurnBeforeNetwork() throws Exception {
        CountDownLatch done=new CountDownLatch(1);List<JsonObject> events=new CopyOnWriteArrayList<>();assertTrue(engine.ask(Wire.message("ask",UUID.randomUUID().toString(),"other-account",repo.current(account).id,0,"Question"),events::add,done::countDown));assertTrue(done.await(5,TimeUnit.SECONDS));assertEquals("error",JwtVerifier.value(events.get(0),"kind"));assertEquals(0,server.getRequestCount());
    }
    @Test public void cancellationDoesNotCommitDelayedResponse() throws Exception {
        server.enqueue(new MockResponse().setBody(success("Answer")).setBodyDelay(3,TimeUnit.SECONDS));String id=UUID.randomUUID().toString();CountDownLatch done=new CountDownLatch(1);
        assertTrue(engine.ask(Wire.message("ask",id,account.key,repo.current(account).id,0,"Question"),e->{},done::countDown));assertNotNull(server.takeRequest(5,TimeUnit.SECONDS));engine.cancel(id);assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue(repo.current(account).turns.isEmpty());
    }
}
