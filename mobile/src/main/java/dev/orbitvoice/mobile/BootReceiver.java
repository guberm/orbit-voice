package dev.orbitvoice.mobile;

import android.content.*;

/** Exempt restart path: Android allows a foreground service after boot or an app update,
 *  so the watch keeps working without the user opening the phone app again. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))InferenceService.arm(context);
    }
}
