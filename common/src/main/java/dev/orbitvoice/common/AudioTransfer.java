package dev.orbitvoice.common;

import android.content.Context;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import com.google.gson.JsonObject;
import dev.orbitvoice.core.AudioWire;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** One ephemeral audio channel per utterance; never writes recordings to disk. */
public final class AudioTransfer implements AutoCloseable {
    private final ChannelClient client;private volatile ChannelClient.Channel channel;private volatile boolean stopped;
    public AudioTransfer(Context context,String node,JsonObject request,byte[] pcm,Runnable failed){
        client=Wearable.getChannelClient(context);
        new Thread(()->{
            try {
                ChannelClient.Channel opened=Tasks.await(client.openChannel(node,AudioWire.PATH+"/"+request.get("id").getAsString()),8,TimeUnit.SECONDS);channel=opened;
                if(stopped)return;
                try(OutputStream output=Tasks.await(client.getOutputStream(opened),8,TimeUnit.SECONDS)){if(!stopped)AudioWire.write(output,request,pcm);}
            }catch(Exception e){if(!stopped)failed.run();}
            finally{ChannelClient.Channel opened=channel;if(opened!=null)client.close(opened);}
        },"orbit-audio-transfer").start();
    }
    @Override public void close(){stopped=true;ChannelClient.Channel opened=channel;if(opened!=null)client.close(opened);}
}
