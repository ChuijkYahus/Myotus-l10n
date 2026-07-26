package me.myogoo.myotus.mixin.ae2;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;

@Mixin(value = ScreenStyle.class, remap = false)
public interface ScreenStyleAccessor {
    @Accessor("widgets")
    Map<String, WidgetStyle> myotus$getWidgets();
}
