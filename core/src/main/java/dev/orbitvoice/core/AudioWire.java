package dev.orbitvoice.core;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class AudioWire {
    public static final int MAX_PCM=16000*2*30;
    public static final String PATH="/orbit/v1/audio";
    public static final class Turn { public final JsonObject request;public final byte[] pcm;Turn(JsonObject r,byte[] p){request=r;pcm=p;} }
    private static final int MAGIC=0x4f564131,END=0x454e4431;
    private static void validate(JsonObject request,int size){
        if(!"audio".equals(JwtVerifier.value(request,"kind"))||JwtVerifier.value(request,"account").isEmpty()||JwtVerifier.value(request,"conversation").isEmpty()||size<3200||size>MAX_PCM||size%2!=0)throw new IllegalArgumentException("Invalid audio turn");
    }
    public static void write(OutputStream out,JsonObject request,byte[] pcm) throws IOException {
        validate(request,pcm.length);byte[] header=Wire.bytes(request);DataOutputStream stream=new DataOutputStream(out);
        stream.writeInt(MAGIC);stream.writeInt(header.length);stream.write(header);stream.writeInt(pcm.length);stream.write(pcm);stream.writeInt(END);stream.flush();
    }
    public static Turn read(InputStream in) throws IOException {
        try {
            DataInputStream stream=new DataInputStream(in);if(stream.readInt()!=MAGIC)throw new IOException("Unsupported audio format");
            int length=stream.readInt();if(length<1||length>60000)throw new IOException("Invalid audio header");
            byte[] header=new byte[length];stream.readFully(header);JsonObject request=Wire.parse(header);int size=stream.readInt();validate(request,size);
            byte[] pcm=new byte[size];stream.readFully(pcm);if(stream.readInt()!=END)throw new IOException("Incomplete audio turn");return new Turn(request,pcm);
        }catch(IllegalArgumentException e){throw new IOException("Invalid audio turn",e);}
    }
}
