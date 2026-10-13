package dev.orbitvoice.mobile;

import dev.orbitvoice.core.PendingAuth;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Loopback OAuth listener. No tokens, codes, or callback URLs are logged. */
final class AuthFlow implements AutoCloseable {
    interface Listener { void done(Repository.Account account);void failed(String message); }
    final PendingAuth pending;
    private final ServerSocket server;
    private volatile boolean cancelled;
    AuthFlow(Repository repo,Repository.Account returning,OpenAi api,Listener listener) throws IOException {
        server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),4);server.setSoTimeout(1000);
        pending=new PendingAuth("http://127.0.0.1:"+server.getLocalPort()+"/auth/callback",repo.config.hostId,returning==null?null:returning.clientId);
        Thread worker=new Thread(()->{
            try {
                long deadline=System.currentTimeMillis()+300000;
                while(!cancelled && System.currentTimeMillis()<deadline) {
                    Socket incoming;
                    try { incoming=server.accept(); }catch(SocketTimeoutException e) { continue; }
                    PendingAuth.Result code=null;
                    try(Socket socket=incoming) {
                        socket.setSoTimeout(3000);
                        BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
                        String request=readLine(reader,8192);String[] parts=request.split(" ");
                        if(parts.length!=3 || !parts[0].equals("GET") || !parts[1].startsWith("/auth/callback?")) { reply(socket,404,"Page not found.");continue; }
                        try { code=pending.accept(pending.redirect+parts[1].substring("/auth/callback".length())); }
                        catch(IllegalArgumentException e) { reply(socket,400,"Sign-in could not be completed. Return to Orbit Voice and try again.");throw e; }
                        reply(socket,200,"Sign-in received. Return to Orbit Voice to finish connecting your account.");
                    }
                    if(code!=null && !cancelled) {
                        Repository.Account account=api.exchange(pending,code,returning,()->!cancelled);
                        if(!cancelled)listener.done(account);return;
                    }
                }
                if(!cancelled)listener.failed("Sign-in timed out. Return to the app and try again.");
            }catch(Exception e) { if(!cancelled)listener.failed(e instanceof IllegalArgumentException?e.getMessage():"Could not finish sign-in. Check your network and try again."); }
            finally { close(); }
        },"OrbitOAuth");worker.setDaemon(true);worker.start();
    }
    private String readLine(Reader reader,int maximum) throws IOException {
        StringBuilder s=new StringBuilder();int c;
        while((c=reader.read())!=-1 && c!='\n') { if(s.length()>=maximum)throw new IOException("Request line too long");if(c!='\r')s.append((char)c); }return s.toString();
    }
    private void reply(Socket socket,int status,String text) throws IOException {
        byte[] body=("<!doctype html><html lang=\"en\"><meta name=\"viewport\" content=\"width=device-width\"><title>Orbit Voice</title><body style=\"background:#061323;color:#f4faff;font:20px sans-serif;padding:40px\"><h1>Orbit Voice</h1><p>"+text+"</p></body></html>").getBytes(StandardCharsets.UTF_8);
        OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
    }
    @Override public void close() { cancelled=true;try { server.close(); }catch(IOException ignored) { } }
}
