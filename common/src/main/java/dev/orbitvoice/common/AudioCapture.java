package dev.orbitvoice.common;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.util.Log;
import dev.orbitvoice.core.SpeechEndpoint;
import java.io.ByteArrayOutputStream;

/** Bounded 16 kHz PCM capture. Quiet waiting retains only a short pre-roll. */
final class AudioCapture implements AutoCloseable {
    interface Listener {void complete(byte[] pcm);void failed(String message);}
    private static final int PREROLL=16000*3*2;   // keep three seconds before the gate opens
    private static final int WARMUP=6;   // 120 ms of microphone settling, discarded
    private static final long TRAIL=5600; // 350 ms of quiet kept after the last speech frame
    private static final int TARGET=12000;  // normalize the speech peak to about -9 dBFS for the decoder
    private static final int GAIN_CAP=6;
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
            long frames=0,lastLoud=0;double peak=0;
            // A freshly opened AudioRecord pops and settles for a moment. That transient used to pass the
            // onset gate, so every listen ended as a quiet clip the phone rejected - and the next listen
            // repeated it. Drop the warm-up frames before the endpoint sees anything.
            for(int i=0;i<WARMUP;i++){int skipped=input.read(frame,0,frame.length,AudioRecord.READ_BLOCKING);if(stopped)return;if(skipped<=0)throw new IllegalStateException();}
            while(!stopped){
                int count=input.read(frame,0,frame.length,AudioRecord.READ_BLOCKING);if(stopped)return;if(count<=0)throw new IllegalStateException();
                for(int i=0;i<count;i++){bytes[i*2]=(byte)frame[i];bytes[i*2+1]=(byte)(frame[i]>>8);}
                audio.write(bytes,0,count*2);boolean done=endpoint.accept(frame,count);frames++;if(endpoint.level()>peak)peak=endpoint.level();
                if(endpoint.started()&&endpoint.level()>endpoint.release())lastLoud=endpoint.total();
                if(frames%100==0)Log.i("OrbitMic","level rms="+Math.round(endpoint.level())+" speech="+endpoint.started()+" frames="+frames);
                if(!endpoint.started()&&audio.size()>PREROLL){byte[] recent=audio.toByteArray();audio.reset();audio.write(recent,recent.length-PREROLL/2,PREROLL/2);}
                if(done){byte[] captured=audio.toByteArray();
                // Only the leading silence is dropped: speech cannot start before the onset that opened the
                // gate, so 400 ms before it is safe. Cutting the tail by any counter dropped real words.
                int from=(int)Math.max(0,endpoint.onset()-6400)*2;
                byte[] kept=(captured.length-from>=3200)?java.util.Arrays.copyOfRange(captured,from,captured.length):captured;
                int trim=captured.length-kept.length;
                int top=0;for(int i=0;i+1<kept.length;i+=2){int s=(kept[i]&255)|(kept[i+1]<<8);int a=s<0?-s:s;if(a>top)top=a;}
                int gain=top>0?Math.min(GAIN_CAP,Math.max(1,TARGET/top)):1;
                if(gain>1){for(int i=0;i+1<kept.length;i+=2){int s=(kept[i]&255)|(kept[i+1]<<8);s=Math.max(-32768,Math.min(32767,s*gain));kept[i]=(byte)s;kept[i+1]=(byte)(s>>8);}}
                final byte[] pcm=kept;
                                Log.i("OrbitMic","capture frames="+frames+" peakRms="+Math.round(peak)+" speech="+endpoint.started()+" floor="+Math.round(endpoint.floor())+" trimmed="+trim+" gain="+gain+" bytes="+pcm.length);
                main.post(()->{if(!stopped)listener.complete(pcm);});return;}
            }
            Log.i("OrbitMic","capture ended frames="+frames+" peakRms="+Math.round(peak)+" speech="+endpoint.started());
        }catch(RuntimeException e){Log.w("OrbitMic","capture failed",e);main.post(()->{if(!stopped)listener.failed("Could not record audio. Check microphone access and try again.");});}
        finally{recorder=null;if(input!=null){try{input.stop();}catch(IllegalStateException ignored){}input.release();}}
    }
    @Override public void close(){stopped=true;AudioRecord input=recorder;if(input!=null)try{input.stop();}catch(IllegalStateException ignored){}}
}
