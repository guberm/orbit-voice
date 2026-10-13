package dev.orbitvoice.common;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

public final class Ui {
    public static final int BG=Color.rgb(6,19,35), INK=Color.rgb(244,250,255), MUTED=Color.rgb(174,194,212), MINT=Color.rgb(101,249,213), PANEL=Color.rgb(16,36,55);
    public static int dp(Context c,float v) { return Math.round(v*c.getResources().getDisplayMetrics().density); }
    public static LinearLayout screen(Activity a,boolean watch) {
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad=dp(a,watch?26:28);col.setPadding(pad,dp(a,watch?24:40),pad,dp(a,watch?40:32));scroll.addView(col);a.setContentView(scroll);
        if (!watch) scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        return col;
    }
    public static TextView text(LinearLayout col,String text,int size,int color) {
        TextView v=new TextView(col.getContext());v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(col.getContext(),6),0,dp(col.getContext(),8));v.setGravity(Gravity.CENTER);col.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    public static void title(LinearLayout col,String title,boolean watch) { TextView v=text(col,title,watch?20:32,INK);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); }
    public static Button button(LinearLayout col,String label,boolean primary,Runnable click) {
        Button b=new Button(col.getContext());b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?BG:INK);b.setMinHeight(dp(col.getContext(),48));
        GradientDrawable shape=new GradientDrawable();shape.setColor(primary?MINT:PANEL);shape.setCornerRadius(dp(col.getContext(),28));b.setBackground(shape);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(col.getContext(),52));p.setMargins(0,dp(col.getContext(),8),0,dp(col.getContext(),4));col.addView(b,p);b.setOnClickListener(v->click.run());return b;
    }
    public static void icon(LinearLayout col,int res,int size) { ImageView image=new ImageView(col.getContext());image.setImageResource(res);image.setContentDescription("Orbit Voice icon");col.addView(image,new LinearLayout.LayoutParams(dp(col.getContext(),size),dp(col.getContext(),size))); }
    public static void dialog(Activity a,String title,String message) { new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("OK",null).show(); }
    public static void link(Activity a,String url) { try { a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))); } catch (ActivityNotFoundException e) { dialog(a,"Browser unavailable","Install a browser on your phone to open this link."); } }
    private Ui() { }
}
