package dev.orbitvoice.mobile;

import dev.orbitvoice.core.PendingAuth;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import java.io.IOException;
import static org.junit.Assert.*;

/** Synthetic credentials only; never contacts OpenAI. */
public final class SignInTest {
    private MockWebServer server;
    private OpenAi api;
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
            Request request=chain.request();
            return chain.proceed(request.newBuilder().url(server.url(request.url().encodedPath())).build());
        }).build();
        api=new OpenAi(null,client);
    }
    @After public void close() throws Exception { server.shutdown(); }
    private void exchange() throws Exception {
        PendingAuth pending=new PendingAuth("http://127.0.0.1:1455/auth/callback","urn:uuid:synthetic",null);
        api.exchange(pending,new PendingAuth.Result("synthetic-code","oaiapp_synthetic"),null,()->true);
    }
    @Test public void rejectedGrantIdentifiesStageWithoutServerBody() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("private-server-detail synthetic-code"));
        IOException failure=assertThrows(IOException.class,this::exchange);
        assertTrue(failure.getMessage(),failure.getMessage().contains("Token exchange"));
        assertTrue(failure.getMessage(),failure.getMessage().contains("HTTP 403"));
        assertFalse(failure.getMessage().contains("private-server-detail"));
        assertFalse(failure.getMessage().contains("synthetic-code"));
    }
    @Test public void invalidIdentityIdentifiesStageWithoutToken() {
        server.enqueue(new MockResponse().setBody("{\"id_token\":\"synthetic-private-token\"}"));
        server.enqueue(new MockResponse().setBody("{\"keys\":[]}"));
        IOException failure=assertThrows(IOException.class,this::exchange);
        assertTrue(failure.getMessage(),failure.getMessage().contains("Identity verification"));
        assertFalse(failure.getMessage().contains("synthetic-private-token"));
    }
}
