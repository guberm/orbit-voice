package dev.orbitvoice.common;

import android.media.*;
import android.os.*;
import android.util.Log;
import dev.orbitvoice.core.SpeechEndpoint;
import java.io.ByteArrayOutputStream;

/** Bounded 16 kHz PCM capture. Quiet waiting retains only a short pre-roll. */
final class AudioCapture implements AutoCloseable {
    interface Listener {void complete(byte[] pcm);void failed(String message);}
    private volatile boolean stopped;private volatile AudioRecord recorder;private final Handler main=new Handler(Looper.getMainLooper());
    AudioCapture(Listener listener){new Thread(()->capture(listener),"orbit-microphone").start();}
    private void capture(Listener listener){
        AudioRecord input=null;
        try {
            int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(minimum<=0)throw new IllegalStateException();
            input=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum,6400));
            recorder=input;if(stopped)return;if(input.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException();input.startRecording();
            short[] frame=new short[320];byte[] bytes=new byte[640];ByteArrayOutputStream audio=new ByteArrayOutputStream();SpeechEndpoint endpoint=new SpeechEndpoint();
            long frames=0;double peak=0;
            while(!stopped){
                int count=input.read(frame,0,frame.length,AudioRecord.READ_BLOCKING);if(stopped)return;if(count<=0)throw new IllegalStateException();
                for(int i=0;i<count;i++){bytes[i*2]=(byte)frame[i];bytes[i*2+1]=(byte)(frame[i]>>8);}
                audio.write(bytes,0,count*2);boolean done=endpoint.accept(frame,count);frames++;if(endpoint.level()>peak)peak=endpoint.level();
                if(!endpoint.started()&&audio.size()>16000){byte[] recent=audio.toByteArray();audio.reset();audio.write(recent,recent.length-9600,9600);}
                if(done){Log.i("OrbitMic","capture frames="+frames+" peakRms="+Math.round(peak)+" speech="+endpoint.started()+" bytes="+audio.size());byte[] pcm=audio.toByteArray();main.post(()->{if(!stopped)listener.complete(pcm);});return;}
            }
            Log.i("OrbitMic","capture ended frames="+frames+" peakRms="+Math.round(peak)+" speech="+endpoint.started());
        }catch(RuntimeException e){Log.w("OrbitMic","capture failed",e);main.post(()->{if(!stopped)listener.failed("Could not record audio. Check microphone access and try again.");});}
        finally{recorder=null;if(input!=null){try{input.stop();}catch(IllegalStateException ignored){}input.release();}}
    }
    @Override public void close(){stopped=true;AudioRecord input=recorder;if(input!=null)try{input.stop();}catch(IllegalStateException ignored){}}
}
