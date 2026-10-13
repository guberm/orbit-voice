package dev.orbitvoice.wear;

import androidx.wear.tiles.TileService;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.protolayout.*;
import com.google.common.util.concurrent.*;

/** Static tile: it opens the foreground voice screen and never records in the background. */
public final class VoiceTileService extends TileService {
    @Override protected ListenableFuture<TileBuilders.Tile> onTileRequest(RequestBuilders.TileRequest request) {
        ActionBuilders.AndroidActivity activity=new ActionBuilders.AndroidActivity.Builder().setPackageName(getPackageName()).setClassName(MainActivity.class.getName()).addKeyToExtraMapping("start_voice",new ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build()).build();
        ModifiersBuilders.Clickable click=new ModifiersBuilders.Clickable.Builder().setId("start").setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build()).build();
        LayoutElementBuilders.Column content=new LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.wrap()).setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(new ModifiersBuilders.Modifiers.Builder().setClickable(click).setBackground(new ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0xFF061323)).build()).setSemantics(new ModifiersBuilders.Semantics.Builder().setContentDescription("Start a voice conversation with Orbit Voice").build()).build())
            .addContent(new LayoutElementBuilders.Text.Builder().setText("ORBIT VOICE").setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(14)).setColor(ColorBuilders.argb(0xFF65F9D5)).build()).build())
            .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(18)).build())
            .addContent(new LayoutElementBuilders.Text.Builder().setText("Tap to talk").setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(24)).setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).setColor(ColorBuilders.argb(0xFFF4FAFF)).build()).build())
            .addContent(new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(10)).build())
            .addContent(new LayoutElementBuilders.Text.Builder().setText("Your next conversation").setFontStyle(new LayoutElementBuilders.FontStyle.Builder().setSize(DimensionBuilders.sp(12)).setColor(ColorBuilders.argb(0xFFAEC2D4)).build()).build()).build();
        LayoutElementBuilders.Box root=new LayoutElementBuilders.Box.Builder().setWidth(DimensionBuilders.expand()).setHeight(DimensionBuilders.expand()).setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER).setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER).setModifiers(new ModifiersBuilders.Modifiers.Builder().setClickable(click).setBackground(new ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0xFF061323)).build()).build()).addContent(content).build();
        return Futures.immediateFuture(new TileBuilders.Tile.Builder().setResourcesVersion("1").setTileTimeline(new TimelineBuilders.Timeline.Builder().addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder().setLayout(new LayoutElementBuilders.Layout.Builder().setRoot(root).build()).build()).build()).build());
    }
    @Override protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(RequestBuilders.ResourcesRequest request){return Futures.immediateFuture(new ResourceBuilders.Resources.Builder().setVersion("1").build());}
}
