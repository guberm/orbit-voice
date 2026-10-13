package dev.orbitvoice.mobile;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import static org.junit.Assert.*;

public final class AuthFlowTest {
    private MockWebServer server;private AuthFlow flow;private CountDownLatch received,failed;
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();received=new CountDownLatch(1);failed=new CountDownLatch(1);
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(c->c.proceed(c.request().newBuilder().url(server.url(c.request().url().encodedPath())).build())).build();
        flow=new AuthFlow("urn:uuid:synthetic",null,null,new OpenAi(null,client),"dev.orbitvoice.app",new AuthFlow.Listener(){
            public void received(){received.countDown();}
            public void done(Repository.Account account){}
            public void failed(String message){failed.countDown();}
        });
        server.enqueue(new MockResponse().setResponseCode(403));
    }
    @After public void close() throws Exception {flow.close();server.shutdown();}
    private String callback() throws Exception {
        URI redirect=URI.create(flow.pending.redirect);
        try(Socket socket=new Socket(redirect.getHost(),redirect.getPort())){
            socket.setSoTimeout(4000);
            String path=redirect.getPath()+"?state="+flow.pending.state+"&code=synthetic-code&client_id=oaiapp_synthetic";
            socket.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    @Test public void callbackWaitsForForegroundCompletionAndExchangesOnlyOnce() throws Exception {
        String page=callback();assertTrue(received.await(2,TimeUnit.SECONDS));
        assertEquals("Browser callback must not start background credential exchange",0,server.getRequestCount());
        assertTrue(page.contains("orbitvoice"));assertFalse(page.contains("synthetic-code"));
        flow.complete();flow.complete();assertTrue(failed.await(5,TimeUnit.SECONDS));assertEquals(1,server.getRequestCount());
    }
    @Test public void cancelledCallbackCannotExchangeOnLaterResume() throws Exception {
        callback();assertTrue(received.await(2,TimeUnit.SECONDS));flow.close();flow.complete();
        assertNull(server.takeRequest(300,TimeUnit.MILLISECONDS));
    }
}
