package me.myogoo.myotus.impl;

import appeng.menu.me.common.MEStorageMenu;
import me.myogoo.myotus.api.config.MyoConfigTab;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public enum ConfigManager {
    INSTANCE;

    private final Map<ResourceLocation, MyoConfigTab> tabs = new LinkedHashMap<>();

    public synchronized void registerTab(MyoConfigTab tab) {
        Objects.requireNonNull(tab, "tab");
        var previous = tabs.putIfAbsent(tab.id(), tab);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate Myotus config tab id: " + tab.id());
        }
    }

    public synchronized List<MyoConfigTab> getTabs() {
        return List.copyOf(tabs.values());
    }

    public List<MyoConfigTab> getVisibleTabs(MEStorageMenu menu) {
        return getTabs().stream()
                .filter(tab -> tab.isVisible(menu))
                .toList();
    }
}
