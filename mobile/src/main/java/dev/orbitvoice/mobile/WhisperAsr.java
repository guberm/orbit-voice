package dev.orbitvoice.mobile;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CancellationException;
import dev.orbitvoice.core.AudioWire;

/** Phone-only offline decoder. Bundled model integrity is checked during the build. */
final class WhisperAsr implements AutoCloseable {
    static final class Result { final String text,language;Result(String text,String language){this.text=text;this.language=language;} }
    private final Context context;private long handle;
    static {System.loadLibrary("orbit_asr");}
    WhisperAsr(Context context){this.context=context.getApplicationContext();}
    synchronized Result transcribe(byte[] pcm,BooleanSupplier cancelled) throws IOException {
        if(cancelled.getAsBoolean())throw new CancellationException();
        if(pcm.length<3200||pcm.length>AudioWire.MAX_PCM||pcm.length%2!=0)throw new IOException("Invalid microphone recording");
        if(handle==0){
            long loading=System.currentTimeMillis();
            File model=asset("ggml-base.bin"),vad=asset("ggml-silero-v6.2.0.bin");
            handle=open(model.getAbsolutePath(),vad.getAbsolutePath());if(handle==0)throw new IOException("Could not load offline speech. Restart the app and try again.");
            Log.i("OrbitAsr","model loaded ms="+(System.currentTimeMillis()-loading));
        }
        if(cancelled.getAsBoolean())throw new CancellationException();
        float[] samples=new float[pcm.length/2];for(int i=0;i<samples.length;i++)samples[i]=(short)((pcm[i*2]&255)|(pcm[i*2+1]<<8))/32768f;
        int threads=Math.min(4,Runtime.getRuntime().availableProcessors());long started=System.currentTimeMillis();
        String[] result=decode(handle,samples,threads,cancelled);
        Log.i("OrbitAsr","decode samples="+samples.length+" threads="+threads+" ms="+(System.currentTimeMillis()-started));
        if(cancelled.getAsBoolean())throw new CancellationException();
        if(result==null||result.length!=2)throw new IOException("Offline recognition failed. Try speaking again.");
        return new Result(result[0].trim(),result[1]);
    }
    private File asset(String name) throws IOException {
        File directory=new File(context.getNoBackupFilesDir(),"speech-v1");if(!directory.exists()&&!directory.mkdirs())throw new IOException("Could not prepare offline speech storage");
        File target=new File(directory,name);long size;try(android.content.res.AssetFileDescriptor fd=context.getAssets().openFd("models/"+name)){size=fd.getLength();}
        if(target.isFile()&&target.length()==size)return target;
        File partial=new File(directory,name+".part");
        try(InputStream input=context.getAssets().open("models/"+name);FileOutputStream output=new FileOutputStream(partial)){
            byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);output.getFD().sync();
        }
        if(partial.length()!=size||!partial.renameTo(target))throw new IOException("Could not prepare offline speech model");return target;
    }
    private static native long open(String model,String vad);
    private static native String[] decode(long handle,float[] samples,int threads,BooleanSupplier cancelled);
    private static native void release(long handle);
    @Override public synchronized void close(){if(handle!=0){release(handle);handle=0;}}
}
