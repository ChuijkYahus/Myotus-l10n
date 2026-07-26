package me.myogoo.myotus.impl;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public enum CreativeTabManager {
    INSTANCE;

    private final List<Supplier<? extends ItemLike>> items = new ArrayList<>();
    private final List<Supplier<ItemStack>> stacks = new ArrayList<>();

    public synchronized void registerItem(Supplier<? extends ItemLike> item) {
        items.add(Objects.requireNonNull(item, "item"));
    }

    public synchronized void registerStack(Supplier<ItemStack> stack) {
        stacks.add(Objects.requireNonNull(stack, "stack"));
    }

    public void populate(CreativeModeTab.Output output) {
        List<Supplier<? extends ItemLike>> itemSnapshot;
        List<Supplier<ItemStack>> stackSnapshot;
        synchronized (this) {
            itemSnapshot = List.copyOf(items);
            stackSnapshot = List.copyOf(stacks);
        }

        for (var item : itemSnapshot) {
            output.accept(Objects.requireNonNull(item.get(), "creative tab item supplier returned null"));
        }

        for (var stack : stackSnapshot) {
            output.accept(Objects.requireNonNull(stack.get(), "creative tab stack supplier returned null"));
        }
    }
}
