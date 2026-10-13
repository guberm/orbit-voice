package dev.orbitvoice.mobile;

import dev.orbitvoice.core.PendingAuth;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Loopback OAuth listener. No tokens, codes, or callback URLs are logged. */
final class AuthFlow implements AutoCloseable {
    interface Listener { default void received(){} void done(Repository.Account account);void failed(String message); }
    final PendingAuth pending;
    private final ServerSocket server;
    private final Repository repo;
    private final Repository.Account returning;
    private final OpenAi api;
    private final Listener listener;
    private final String appPackage;
    private volatile PendingAuth.Result receivedCode;
    private boolean completing;
    private volatile boolean cancelled;
    AuthFlow(Repository repo,Repository.Account returning,OpenAi api,Listener listener) throws IOException {
        this(repo.config.hostId,repo,returning,api,"dev.orbitvoice.app",listener);
    }
    AuthFlow(String hostId,Repository repo,Repository.Account returning,OpenAi api,String appPackage,Listener listener) throws IOException {
        this.repo=repo;this.returning=returning;this.api=api;this.listener=listener;this.appPackage=appPackage;
        if(!appPackage.matches("[A-Za-z0-9_.]+"))throw new IllegalArgumentException("Invalid app package");
        server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),4);server.setSoTimeout(1000);
        pending=new PendingAuth("http://127.0.0.1:"+server.getLocalPort()+"/auth/callback",hostId,returning==null?null:returning.clientId);
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
                        receivedCode=code;
                        reply(socket,200,"Sign-in received. Open Orbit Voice below to finish connecting your account.");
                    }
                    if(code!=null && !cancelled) {
                        listener.received();return;
                    }
                }
                if(!cancelled)listener.failed("Sign-in timed out. Return to the app and try again.");
            }catch(Exception e) { if(!cancelled)listener.failed(e instanceof OpenAi.SignInFailure?e.getMessage():e instanceof IllegalArgumentException?"Authorization callback could not be validated. Start sign-in again.":"Could not finish sign-in. Check your network and try again."); }
            finally { closeServer(); }
        },"OrbitOAuth");worker.setDaemon(true);worker.start();
    }
    synchronized void complete(){
        if(cancelled||completing||receivedCode==null)return;
        completing=true;PendingAuth.Result code=receivedCode;
        Thread exchange=new Thread(()->{
            try{
                Repository.Account account=api.exchange(pending,code,returning,()->!cancelled);
                if(!cancelled)listener.done(account);
            }catch(Exception e){if(!cancelled)listener.failed(e instanceof OpenAi.SignInFailure?e.getMessage():"Could not finish sign-in. Check your network and try again.");}
            finally{close();}
        },"OrbitOAuthExchange");exchange.setDaemon(true);exchange.start();
    }
    private String readLine(Reader reader,int maximum) throws IOException {
        StringBuilder s=new StringBuilder();int c;
        while((c=reader.read())!=-1 && c!='\n') { if(s.length()>=maximum)throw new IOException("Request line too long");if(c!='\r')s.append((char)c); }return s.toString();
    }
    private void reply(Socket socket,int status,String text) throws IOException {
        String link=status==200?"<p><a style=\"display:block;background:#65f9d5;color:#061323;padding:18px;border-radius:32px;text-align:center;text-decoration:none\" href=\"intent://auth-complete#Intent;scheme=orbitvoice;package="+appPackage+";end\">Open Orbit Voice</a></p>":"";
        byte[] body=("<!doctype html><html lang=\"en\"><meta name=\"viewport\" content=\"width=device-width\"><title>Orbit Voice</title><body style=\"background:#061323;color:#f4faff;font:20px sans-serif;padding:40px\"><h1>Orbit Voice</h1><p>"+text+"</p>"+link+"</body></html>").getBytes(StandardCharsets.UTF_8);
        OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
    }
    private void closeServer(){try{server.close();}catch(IOException ignored){}}
    @Override public void close() { cancelled=true;receivedCode=null;closeServer(); }
}
