package me.myogoo.myotus.client;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import appeng.client.gui.Icon;
import appeng.menu.slot.AppEngSlot;
import me.myogoo.myotus.menu.MyoSlotSemantics;
import me.myogoo.myotus.menu.TerminalUpgradeHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.style.Blitter;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;

/**
 * MEStorageScreen 위에 떠 있는(floating) 드래그 가능한 서브스크린 패널.
 * Mekanism의 GuiWindow 패턴을 참고하여 구현.
 * ViewCell 5개를 세로로 표시합니다.
 */
public class TerminalUpgradePanel implements ICompositeWidget {

    public static final String WIDGET_ID = "myotus_terminal_upgrade_panel";

    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 7;
    private static final int SLOT_COUNT = 5;

    private static final int PANEL_WIDTH = PADDING + SLOT_SIZE + PADDING;
    private static final int PANEL_HEIGHT = PADDING + (SLOT_SIZE * SLOT_COUNT) + PADDING;
    private static final int HIDDEN_SLOT_X = -9999;
    private static final int HIDDEN_SLOT_Y = -9999;

    private static final Blitter BACKGROUND = Blitter.texture("guis/extra_panels.png", 128, 128);

    // 패널 위치 (screen-relative 좌표, guiLeft/guiTop을 기준)
    private int x;
    private int y;

    // 스크린 원점 (window 좌표)
    private Point screenOrigin = Point.ZERO;

    // 가시성
    private boolean visible = false;
    private final List<Slot> viewCellSlots;
    private final List<Slot> upgradeSlots;
    private final Map<Slot, SlotState> hiddenSlots = new IdentityHashMap<>();

    private final MEStorageMenu menu;

    public TerminalUpgradePanel(MEStorageMenu menu, int terminalScreenWidth) {
        this.menu = menu;
        this.viewCellSlots = menu.getSlots(SlotSemantics.VIEW_CELL);
        this.upgradeSlots = menu.getSlots(MyoSlotSemantics.MYO_UPGRADE_SLOT);
        setPosition(new Point(terminalScreenWidth, 0));
        configureUpgradeSlots(menu);
        hideSlots();
    }

    private void configureUpgradeSlots(MEStorageMenu menu) {
        for (Slot slot : upgradeSlots) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setIcon(Icon.BACKGROUND_UPGRADE);
                appEngSlot.setEmptyTooltip(() -> TerminalUpgradeHelper.getAvailableUpgradeTooltip(menu));
            }
        }
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            hideSlots(); // 닫힐 때 슬롯 숨김
        }
    }

    private void hideSlots() {
        hideViewCellSlots();
        hideSlotList(upgradeSlots);
    }

    private void hideViewCellSlots() {
        hideSlotList(viewCellSlots);
    }

    private void hideSlotList(List<Slot> slots) {
        for (Slot slot : slots) {
            hideSlot(slot);
        }
    }

    private void hideSlot(Slot slot) {
        hiddenSlots.computeIfAbsent(slot, SlotState::capture);
        if (slot instanceof AppEngSlot appEngSlot) {
            appEngSlot.setActive(false);
        }
        slot.x = HIDDEN_SLOT_X;
        slot.y = HIDDEN_SLOT_Y;
    }

    private void showSlotAt(Slot slot, int x, int y) {
        SlotState state = hiddenSlots.remove(slot);
        if (state != null) {
            state.restore(slot);
        }
        slot.x = x;
        slot.y = y;
    }

    @Override
    public void setPosition(Point position) {
        this.x = position.getX();
        this.y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
        // 크기는 고정
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x, y, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
        this.screenOrigin = Point.fromTopLeft(bounds);
    }

    @Override
    public void updateBeforeRender() {
        if (!visible) {
            hideSlots();
            return;
        }
        hideViewCellSlots();

        // 패널 내 첫 번째 슬롯 좌표 (screen-relative: guiLeft/top 기준)
        int slotStartX = this.x;
        int slotStartY = this.y + PADDING;

        for (int i = 0; i < Math.min(SLOT_COUNT, upgradeSlots.size()); i++) {
            Slot slot = upgradeSlots.get(i);
            showSlotAt(slot, slotStartX + 8, slotStartY + i * SLOT_SIZE + 1);
        }
    }

    /**
     * 최상단 렌더링을 위해 외부에서 직접 호출.
     * z-index를 높여서 다른 UI 요소 위에 렌더링합니다.
     */
    @Override
    public void drawBackgroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        if (!visible) {
            return;
        }

        guiGraphics.pose().pushPose();
        //guiGraphics.pose().translate(0, 0, 1);

        // extra_panels.png를 이용하여 바탕화면과 본 스크린이 자연스럽게 이어지도록 그립니다.
        // 드래그 핸들이나 Icon.SLOT_BACKGROUND는 그리지 않아서 슬롯 내장 배경(아이콘)이 보이게 합니다.
        int slotOriginX = screenOrigin.getX() + this.x;
        int slotOriginY = screenOrigin.getY() + this.y + PADDING;

        for (int i = 0; i < SLOT_COUNT; i++) {
            boolean borderTop = (i == 0);
            boolean borderBottom = (i == SLOT_COUNT - 1);
            drawSlotPart(guiGraphics, slotOriginX, slotOriginY + i * SLOT_SIZE, borderTop, borderBottom);
        }
        guiGraphics.pose().popPose();
    }


    private static void drawSlotPart(GuiGraphics guiGraphics, int x, int y,
            boolean borderTop, boolean borderBottom) {
        int srcX = 0;
        int srcY = PADDING;
        int srcWidth = 32;
        int srcHeight = SLOT_SIZE;

//        if (borderLeft) {
//            x -= PADDING;
//            srcX = 0;
//            // srcWidth += PADDING;
//        }
//        if (borderRight) {
//            x -= PADDING;
//            srcWidth += PADDING;
//        }
        if (borderTop) {
            y -= 7;
            srcY = 0;
            srcHeight += PADDING;
        }
        if (borderBottom) {
            srcHeight += PADDING;
        }

        BACKGROUND.src(srcX, srcY, srcWidth, srcHeight).dest(x, y).blit(guiGraphics);
    }

    @Override
    public void drawForegroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
        // 현재 단계에서는 추가 foreground 렌더링 없음
    }

    // --- 드래그 핸들링 ---

    @Override
    public boolean onMouseDown(Point mousePos, int button) {
        if (!visible || (button != 0 && button != 1)) {
            return false;
        }

        // 패널 영역 내 클릭인 경우
        if (mousePos.isIn(getBounds())) {
            // 슬롯 영역 위에 있으면 이 이벤트를 소비하지 않고 스크린(Slot 처리기)으로 넘김
            if (isOverSlot(mousePos)) {
                return false;
            }
            // 그 외의 패널 배경 클릭은 소비하여 뒤쪽 GUI 클릭 방지
            return true;
        }

        return false;
    }

    private boolean isOverSlot(Point mousePos) {
        for (Slot slot : upgradeSlots) {
            if (isMouseOverSlot(mousePos, slot))
                return true;
        }
        return false;
    }

    private boolean isMouseOverSlot(Point mousePos, Slot slot) {
        return mousePos.getX() >= slot.x && mousePos.getX() < slot.x + 16 &&
                mousePos.getY() >= slot.y && mousePos.getY() < slot.y + 16;
    }

    @Override
    public void addExclusionZones(List<Rect2i> exclusionZones, Rect2i screenBounds) {
        if (visible) {
            exclusionZones.add(new Rect2i(
                    screenBounds.getX() + x,
                    screenBounds.getY() + y,
                    PANEL_WIDTH,
                    PANEL_HEIGHT));
        }
    }

    private record SlotState(int x, int y, boolean active) {
        private static SlotState capture(Slot slot) {
            return new SlotState(slot.x, slot.y, slot.isActive());
        }

        private void restore(Slot slot) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(active);
            }
            slot.x = x;
            slot.y = y;
        }
    }
}
