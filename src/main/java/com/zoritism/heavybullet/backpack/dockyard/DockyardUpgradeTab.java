package com.zoritism.heavybullet.backpack.dockyard;

import com.zoritism.heavybullet.network.C2SHandleDockyardShipPacket;
import com.zoritism.heavybullet.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinition;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinitions;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ToggleButton;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TextureBlitData;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.UV;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class DockyardUpgradeTab extends UpgradeSettingsTab<DockyardUpgradeContainer> {

    private static final Logger LOGGER = LogManager.getLogger("HeavyBullet/DockyardUpgradeTab");

    private static final int TAB_WIDTH = 92;
    private static final int TAB_HEIGHT = 92;
    private static final int BODY_HEIGHT = 92;

    // Поля для кораблей (как в AnvilUpgradeTab, только чуть короче)
    private static final int FIELD_WIDTH = 84;
    private static final int FIELD_HEIGHT = 16;

    private static final TextureBlitData FIELD_ACTIVE = new TextureBlitData(
            GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(28, 99), new Dimension(FIELD_WIDTH, FIELD_HEIGHT)
    );
    private static final TextureBlitData FIELD_INACTIVE = new TextureBlitData(
            GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(28, 115), new Dimension(FIELD_WIDTH, FIELD_HEIGHT)
    );

    // Кнопка: правая текстура если слот пустой, левая если корабль есть
    private static final ButtonDefinition.Toggle<Boolean> SLOT_BUTTON = ButtonDefinitions.createToggleButtonDefinition(
            Map.of(
                    false, GuiHelper.getButtonStateData(
                            new UV(144, 48),
                            "",
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    ),
                    true, GuiHelper.getButtonStateData(
                            new UV(112, 48),
                            "",
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    )
            )
    );

    // Позиции
    private static final int FIELD1_X = 6;
    private static final int FIELD1_Y = 24;
    private static final int FIELD2_X = 6;
    private static final int FIELD2_Y = FIELD1_Y + 22;

    private static final int BUTTON1_X = FIELD1_X + FIELD_WIDTH + 4;
    private static final int BUTTON1_Y = FIELD1_Y;
    private static final int BUTTON2_X = FIELD2_X + FIELD_WIDTH + 4;
    private static final int BUTTON2_Y = FIELD2_Y;

    public DockyardUpgradeTab(DockyardUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
        super(upgradeContainer, position, screen,
                Component.translatable("gui.heavybullet.dockyard.title"),
                Component.translatable("gui.heavybullet.dockyard.tooltip"));

        openTabDimension = new Dimension(TAB_WIDTH, TAB_HEIGHT);

        // Левая кнопка (Слот 1)
        addHideableChild(new ToggleButton<>(
                new Position(x + BUTTON1_X, y + BUTTON1_Y),
                SLOT_BUTTON,
                btn -> handleSlotButtonClick(0),
                () -> hasShipInSlot(0)
        ));

        // Правая кнопка (Слот 2)
        addHideableChild(new ToggleButton<>(
                new Position(x + BUTTON2_X, y + BUTTON2_Y),
                SLOT_BUTTON,
                btn -> handleSlotButtonClick(1),
                () -> hasShipInSlot(1)
        ));
    }

    // Проверка: есть ли корабль в слоте (заполнить из логики контейнера!)
    private boolean hasShipInSlot(int slot) {
        // TODO: заменить на логику проверки в контейнере/апгрейде
        boolean result = getStoredShipName(slot) != null;
        LOGGER.debug("[hasShipInSlot] slot={}, result={}", slot, result);
        return result;
    }

    // Получить название корабля для слота (заполнить из логики контейнера!)
    private String getStoredShipName(int slot) {
        // TODO: получить название корабля по слоту из контейнера/апгрейда (например, через wrapper или напрямую)
        // Вот пример заглушки:
        // return slot == 0 ? "wrestler-hunger-health" : null;
        LOGGER.debug("[getStoredShipName] slot={} (STUB: always null)", slot);
        return null;
    }

    // Клик по кнопке слота: если слот пустой — подобрать, иначе выпустить
    private void handleSlotButtonClick(int slot) {
        boolean hasShip = hasShipInSlot(slot);
        LOGGER.info("[handleSlotButtonClick] slot={}, hasShip={}, sending packet...", slot, hasShip);
        NetworkHandler.CHANNEL.sendToServer(new C2SHandleDockyardShipPacket(slot, hasShip));
    }

    @Override
    protected void moveSlotsToTab() {
        // Нет слотов в этой вкладке
    }

    @Override
    protected void renderBg(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        super.renderBg(graphics, minecraft, mouseX, mouseY);

        if (!isOpen) {
            return;
        }

        // Слот 1: поле и название корабля (или пустое/неактивное)
        boolean slot1HasShip = hasShipInSlot(0);
        TextureBlitData field1 = slot1HasShip ? FIELD_ACTIVE : FIELD_INACTIVE;
        GuiHelper.blit(graphics, x + FIELD1_X, y + FIELD1_Y, field1, FIELD_WIDTH, FIELD_HEIGHT);
        String name1 = getStoredShipName(0);
        graphics.drawString(Minecraft.getInstance().font,
                name1 == null ? "" : name1,
                x + FIELD1_X + 6,
                y + FIELD1_Y + 4,
                0x404040, false);

        // Слот 2: поле и название корабля (или пустое/неактивное)
        boolean slot2HasShip = hasShipInSlot(1);
        TextureBlitData field2 = slot2HasShip ? FIELD_ACTIVE : FIELD_INACTIVE;
        GuiHelper.blit(graphics, x + FIELD2_X, y + FIELD2_Y, field2, FIELD_WIDTH, FIELD_HEIGHT);
        String name2 = getStoredShipName(1);
        graphics.drawString(Minecraft.getInstance().font,
                name2 == null ? "" : name2,
                x + FIELD2_X + 6,
                y + FIELD2_Y + 4,
                0x404040, false);
    }

    @Override
    public int getWidth() {
        return isOpen ? TAB_WIDTH : super.getWidth();
    }

    @Override
    public int getHeight() {
        return isOpen ? TAB_HEIGHT : super.getHeight();
    }

    public int getBodyHeight() {
        return isOpen ? BODY_HEIGHT : TAB_HEIGHT;
    }
}