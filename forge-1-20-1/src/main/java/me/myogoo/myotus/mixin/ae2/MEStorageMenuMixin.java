package me.myogoo.myotus.mixin.ae2;

import java.util.IdentityHashMap;
import java.util.Map;

import appeng.api.storage.ITerminalHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;
import me.myogoo.myotus.api.ITerminalUpgradeCard;
import me.myogoo.myotus.menu.MyoSlotSemantics;
import me.myogoo.myotus.menu.PlayerUpgradeContainer;
import me.myogoo.myotus.menu.TerminalUpgradeHost;
import me.myogoo.myotus.menu.TerminalUpgradeSlotFilter;
import me.myogoo.myotus.menu.TerminalUpgradeStorageKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class MEStorageMenuMixin extends AEBaseMenu {

    // Tracks the last observed upgrade stack per slot to dispatch insert/remove lifecycle callbacks.
    @Unique
    private final Map<Slot, ItemStack> myotus$prevUpgradeItems = new IdentityHashMap<>();

    @Unique
    private long myotus$lastUpgradeTick = Long.MIN_VALUE;

    @Unique
    private boolean myotus$upgradeLifecycleStarted;

    private MEStorageMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/api/storage/ITerminalHost;Z)V", at = @At("TAIL"))
    private void myotus$ensureSidePanelSlots(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host,
            boolean bindInventory, CallbackInfo ci) {
        if (this.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT).isEmpty()) {
            myotus$addCustomUpgradeSlots(host);
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"), remap = true)
    private void myotus$onBroadcastChanges(CallbackInfo ci) {
        if (this.getPlayer().level().isClientSide()) {
            return;
        }

        if (!myotus$upgradeLifecycleStarted) {
            myotus$upgradeLifecycleStarted = true;
            myotus$dispatchUpgradeOpen();
        } else {
            myotus$checkUpgradeSlotChanges();
        }

        long gameTime = this.getPlayer().level().getGameTime();
        if (myotus$lastUpgradeTick == gameTime) {
            return;
        }
        myotus$lastUpgradeTick = gameTime;
        myotus$dispatchUpgradeTick();
    }

    @Unique
    private void myotus$checkUpgradeSlotChanges() {
        MEStorageMenu menu = (MEStorageMenu) (Object) this;
        for (Slot slot : this.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT)) {
            ItemStack prevStack = myotus$prevUpgradeItems.getOrDefault(slot, ItemStack.EMPTY);
            ItemStack nowStack = slot.getItem();

            if (ItemStack.isSameItem(prevStack, nowStack)) {
                myotus$prevUpgradeItems.put(slot, nowStack.copy());
                continue;
            }

            if (!prevStack.isEmpty() && prevStack.getItem() instanceof ITerminalUpgradeCard oldCard) {
                oldCard.onTerminalClose(menu, prevStack.copy());
            }
            if (!nowStack.isEmpty() && nowStack.getItem() instanceof ITerminalUpgradeCard newCard) {
                ItemStack before = nowStack.copy();
                newCard.onTerminalOpen(menu, nowStack);
                myotus$persistCallbackChanges(slot, before);
                continue;
            }

            myotus$prevUpgradeItems.put(slot, nowStack.copy());
        }
    }

    @Unique
    private void myotus$addCustomUpgradeSlots(ITerminalHost host) {
        // Placed terminal parts keep upgrades in the part NBT so AE2 can drop them when removed.
        // Item-backed terminals keep the previous per-player storage.
        AppEngInternalInventory upgradeInv;
        if (host instanceof TerminalUpgradeHost upgradeHost) {
            upgradeInv = upgradeHost.myotus$getUpgradeInventory();
        } else {
            upgradeInv = (this.getPlayer() instanceof ServerPlayer serverPlayer)
                    ? new PlayerUpgradeContainer(serverPlayer, TerminalUpgradeStorageKey.of(host))
                    : new AppEngInternalInventory(null, PlayerUpgradeContainer.SIZE, 1,
                            TerminalUpgradeSlotFilter.INSTANCE);
        }

        for (int i = 0; i < PlayerUpgradeContainer.SIZE; i++) {
            var slot = new AppEngSlot(upgradeInv, i);
            slot.setIcon(RestrictedInputSlot.PlacableItemType.UPGRADES.icon);
            slot.setNotDraggable();
            this.addSlot(slot, MyoSlotSemantics.MYO_UPGRADE_SLOT);
        }
    }

    @Unique
    private void myotus$dispatchUpgradeOpen() {
        MEStorageMenu menu = (MEStorageMenu) (Object) this;
        for (Slot slot : this.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT)) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof ITerminalUpgradeCard card) {
                ItemStack before = stack.copy();
                card.onTerminalOpen(menu, stack);
                myotus$persistCallbackChanges(slot, before);
            } else {
                myotus$prevUpgradeItems.put(slot, stack.copy());
            }
        }
    }

    @Unique
    private void myotus$dispatchUpgradeTick() {
        MEStorageMenu menu = (MEStorageMenu) (Object) this;
        for (Slot slot : this.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT)) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof ITerminalUpgradeCard card) {
                ItemStack before = stack.copy();
                card.onTerminalTick(menu, stack);
                myotus$persistCallbackChanges(slot, before);
            }
        }
    }

    @Unique
    private void myotus$persistCallbackChanges(Slot slot, ItemStack before) {
        ItemStack current = slot.getItem();
        if (!ItemStack.matches(before, current)) {
            slot.set(current);
        }
        myotus$prevUpgradeItems.put(slot, current.copy());
    }
}
