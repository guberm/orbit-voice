package dev.orbitvoice.wear;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import androidx.wear.watchface.complications.data.*;
import androidx.wear.watchface.complications.datasource.*;

/** Watch-face shortcut: put Orbit Voice on the dial while the clock is shown;
 *  one tap opens the conversation screen already listening. */
public final class VoiceShortcutService extends ComplicationDataSourceService {
    @Override public void onComplicationRequest(ComplicationRequest request,ComplicationRequestListener listener){
        try{listener.onComplicationData(data(request.getComplicationType()));}catch(Exception ignored){}
    }
    @Override public ComplicationData getPreviewData(ComplicationType type){return data(type);}
    private ComplicationData data(ComplicationType type){
        if(type==ComplicationType.EMPTY)return new EmptyComplicationData();
        if(type==ComplicationType.NO_DATA||type==ComplicationType.NO_PERMISSION||type==ComplicationType.NOT_CONFIGURED)return new NoDataComplicationData();
        ComplicationText label=new PlainComplicationText.Builder("Orbit Voice").build();
        PendingIntent tap=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("start_voice",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        if(type==ComplicationType.SHORT_TEXT)return new ShortTextComplicationData.Builder(label,label).setTapAction(tap).build();
        if(type==ComplicationType.LONG_TEXT)return new LongTextComplicationData.Builder(label,label).setTapAction(tap).build();
        if(type==ComplicationType.MONOCHROMATIC_IMAGE)return new MonochromaticImageComplicationData.Builder(monochrome(),label).setTapAction(tap).build();
        return new SmallImageComplicationData.Builder(new SmallImage.Builder(icon(),SmallImageType.ICON).build(),label).setTapAction(tap).build();
    }
    private Icon icon(){return Icon.createWithResource(this,dev.orbitvoice.common.R.drawable.orbit_monochrome);}
    private MonochromaticImage monochrome(){return new MonochromaticImage.Builder(icon()).build();}
}
