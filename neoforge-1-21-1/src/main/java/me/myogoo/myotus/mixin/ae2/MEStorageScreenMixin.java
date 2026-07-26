package me.myogoo.myotus.mixin.ae2;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.MEStorageMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import me.myogoo.myotus.api.annotation.mods.AE2WTLib;
import me.myogoo.myotus.client.KeyBindings;
import me.myogoo.myotus.client.integration.AE2WTLibClientCompat;
import me.myogoo.myotus.client.gui.config.TabbedTerminalSettingsScreen;
import me.myogoo.myotus.client.TerminalUpgradePanel;
import me.myogoo.myotus.client.gui.widgets.SidePanelToggleButton;
import me.myogoo.myotus.config.MyotusConfig;
import me.myogoo.myotus.util.mod.ModIntegrationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.WidgetStyle;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MEStorageScreen.class, remap = false)
public class MEStorageScreenMixin extends AEBaseScreen<AEBaseMenu> {
    @Shadow
    @Final
    private Scrollbar scrollbar;
    @Unique
    private static final String MYOTUS$AE2WTLIB_UPGRADES_ID = "scrollingUpgrades";
    @Unique
    private TerminalUpgradePanel myotus$floatingSubScreen;

    @Unique
    private SidePanelToggleButton myotus$toggleButton;

    public MEStorageScreenMixin(AEBaseMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void myotus$addFloatingSubScreen(CallbackInfo ci) {
        WidgetStyle customStyle = new WidgetStyle();
        customStyle.setRight(-3);
        ((ScreenStyleAccessor) (Object) style).myotus$getWidgets()
                .put(TerminalUpgradePanel.WIDGET_ID, customStyle);
        if (this.menu instanceof MEStorageMenu storageMenu) {
            boolean isAe2WtlibScreen = myotus$isAe2WtlibMenuHost(storageMenu.getHost());
            myotus$floatingSubScreen = new TerminalUpgradePanel(storageMenu, this.imageWidth, isAe2WtlibScreen);
            myotus$toggleButton = new SidePanelToggleButton(myotus$floatingSubScreen);
            myotus$updateToggleButtonVisibility();
            this.addToLeftToolbar(myotus$toggleButton);

            if (myotus$floatingSubScreen != null) {
                myotus$floatingSubScreen.setVisible(MyotusConfig.CLIENT.openSidePanel.get());
                Rect2i screenBounds = new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
                myotus$floatingSubScreen.populateScreen(this::addRenderableWidget, screenBounds, this);

                this.widgets.add(TerminalUpgradePanel.WIDGET_ID, myotus$floatingSubScreen);
                this.addRenderableWidget(myotus$toggleButton);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(method = "showSettings", at = @At(value = "INVOKE", target = "Lappeng/client/gui/me/common/MEStorageScreen;switchToScreen(Lappeng/client/gui/AEBaseScreen;)V"), remap = false)
    private void myotus$redirectToTabbedSettings(MEStorageScreen instance, AEBaseScreen screen,
                                                 Operation<Void> original) {
        if (screen instanceof TerminalSettingsScreen) {
            original.call(instance, new TabbedTerminalSettingsScreen(instance));
        } else {
            original.call(instance, screen);
        }
    }

    @WrapOperation(method = "onReturnFromSubScreen", constant = @Constant(classValue = TerminalSettingsScreen.class), remap = false)
    private boolean myotus$extendInstanceCheck(Object obj, Operation<Boolean> original) {
        if (obj instanceof TabbedTerminalSettingsScreen) {
            return true;
        }
        return original.call(obj);
    }

    @Inject(method = "keyPressed", at = @At(value = "HEAD"), cancellable = true)
    public void registerKeyHandler(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (KeyBindings.OPEN_TERMINAL_SETTING.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            if (Minecraft.getInstance().screen instanceof MEStorageScreen<?> storageScreen) {
                this.switchToScreen(new TabbedTerminalSettingsScreen<>(storageScreen));
                cir.setReturnValue(true);
            }
        }
        if (KeyBindings.TOGGLE_UPGRADE_TERMINAL_PANEL.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            if (Minecraft.getInstance().screen instanceof MEStorageScreen<?> storageScreen) {
                if (this.myotus$toggleButton != null) {
                    this.myotus$toggleButton.run();
                }
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "init", at = @At("HEAD"))
    protected void initPanel(CallbackInfo ci) {
        myotus$updateToggleButtonVisibility();
        if (myotus$floatingSubScreen != null) {
            myotus$floatingSubScreen.setVisible(MyotusConfig.CLIENT.openSidePanel.get());
        }
        WidgetStyle sidePanelStyle = ((ScreenStyleAccessor) (Object) style).myotus$getWidgets()
                .get(TerminalUpgradePanel.WIDGET_ID);
        ((ScreenStyleAccessor) (Object) style).myotus$getWidgets()
                .put(TerminalUpgradePanel.WIDGET_ID, sidePanelStyle);
    }

    @Inject(method = "init", at = @At("TAIL"), order = 0)
    protected void repositionPanel(CallbackInfo ci) {
        if (myotus$floatingSubScreen == null) {
            return;
        }

        var composites = ((WidgetContainerAccessor) this.widgets)
                .myotus$getCompositeWidgets();

        int panelX = this.imageWidth - 3;


        var upgrades = composites.get("upgrades");
        var scrollingUpgrades = composites.get("scrollingUpgrades");
        if (this.menu instanceof MEStorageMenu storageMenu) {
            if (myotus$isAe2WtlibMenuHost(storageMenu.getHost()) && upgrades != null && upgrades.isVisible()) {
                var bounds = upgrades.getBounds();
                panelX = bounds.getX() + bounds.getWidth();
            } if (scrollingUpgrades != null && scrollingUpgrades.isVisible()) {
                var bounds = scrollingUpgrades.getBounds();
                panelX = bounds.getX() + bounds.getWidth();
            }
        }
        myotus$floatingSubScreen.setPosition(new Point(panelX, 0));
    }

    @Unique
    private boolean myotus$isAe2WtlibMenuHost(Object host) {
        return ModIntegrationManager.isLoaded(AE2WTLib.class)
                && AE2WTLibClientCompat.isMenuHost(host);
    }

    @Unique
    private int myotus$getVisibleRowsForNextInit() {
        var terminalStyle = this.style.getTerminalStyle();
        if (terminalStyle == null) {
            return 2;
        }
        int availableHeight = this.height - 2 * this.config.getTerminalMargin();
        return Math.max(2, this.config.getTerminalStyle().getRows(terminalStyle.getPossibleRows(availableHeight)));
    }

    @Unique
    private void myotus$updateToggleButtonVisibility() {
        if (myotus$toggleButton != null) {
            myotus$toggleButton.visible = MyotusConfig.CLIENT.showUpgradePanelButton.get();
        }
    }
}
