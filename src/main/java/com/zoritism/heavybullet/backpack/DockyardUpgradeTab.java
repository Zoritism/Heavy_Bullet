package com.zoritism.heavybullet.backpack;

import com.zoritism.heavybullet.network.C2SHandleBottleShipPacket;
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

import java.util.Map;

public class DockyardUpgradeTab extends UpgradeSettingsTab<DockyardUpgradeContainer> {

    // Как в AnvilUpgradeTab
    private static final int TAB_WIDTH = 103;
    private static final int TAB_HEIGHT = 92;
    private static final int BODY_HEIGHT = 92;

    // Полоски для текста (фон для ввода, как в AnvilUpgradeTab)
    private static final TextureBlitData EDIT_ITEM_NAME_BACKGROUND = new TextureBlitData(
            GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(28, 99), new Dimension(100, 16)
    );
    private static final TextureBlitData EDIT_ITEM_NAME_BACKGROUND_DISABLED = new TextureBlitData(
            GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(28, 115), new Dimension(100, 16)
    );

    // Кнопки с иконками (как в MagnetUpgradeTab)
    public static final ButtonDefinition.Toggle<Boolean> BUTTON_LEFT = ButtonDefinitions.createToggleButtonDefinition(
            Map.of(
                    false, GuiHelper.getButtonStateData(
                            new UV(128, 48),
                            "", // без подписи
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    ),
                    true, GuiHelper.getButtonStateData(
                            new UV(128, 48),
                            "", // без подписи
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    )
            )
    );

    public static final ButtonDefinition.Toggle<Boolean> BUTTON_RIGHT = ButtonDefinitions.createToggleButtonDefinition(
            Map.of(
                    false, GuiHelper.getButtonStateData(
                            new UV(144, 48),
                            "",
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    ),
                    true, GuiHelper.getButtonStateData(
                            new UV(144, 48),
                            "",
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    )
            )
    );

    // Позиции и размеры как в AnvilUpgradeTab
    private static final int STRIP_X = 5;
    private static final int STRIP1_Y = 25;
    private static final int STRIP2_Y = STRIP1_Y + 18;

    private static final int BUTTON1_X = 5;
    private static final int BUTTON2_X = 5 + 20;
    private static final int BUTTONS_Y = STRIP2_Y + 24;

    public DockyardUpgradeTab(DockyardUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
        super(upgradeContainer, position, screen,
                Component.translatable("gui.heavybullet.dockyard.title"),
                Component.translatable("gui.heavybullet.dockyard.tooltip"));

        openTabDimension = new Dimension(TAB_WIDTH, TAB_HEIGHT);

        // Левая кнопка (захват корабля)
        addHideableChild(new ToggleButton<>(
                new Position(x + BUTTON1_X, y + BUTTONS_Y),
                BUTTON_LEFT,
                btn -> {
                    System.out.println("[DockyardUpgradeTab] Left button clicked (capture ship)"); // Клиентский лог
                    NetworkHandler.CHANNEL.sendToServer(new C2SHandleBottleShipPacket(false));
                },
                () -> false
        ));

        // Правая кнопка (пока без функции)
        addHideableChild(new ToggleButton<>(
                new Position(x + BUTTON2_X, y + BUTTONS_Y),
                BUTTON_RIGHT,
                btn -> {},
                () -> false
        ));
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

        // Первая полоска с заглушкой
        GuiHelper.blit(graphics, x + STRIP_X, y + STRIP1_Y, EDIT_ITEM_NAME_BACKGROUND, 100, 16);
        graphics.drawString(Minecraft.getInstance().font,
                Component.literal("Заглушка 1"),
                x + STRIP_X + 6,
                y + STRIP1_Y + 4,
                0x404040, false);

        // Вторая полоска с заглушкой
        GuiHelper.blit(graphics, x + STRIP_X, y + STRIP2_Y, EDIT_ITEM_NAME_BACKGROUND_DISABLED, 100, 16);
        graphics.drawString(Minecraft.getInstance().font,
                Component.literal("Заглушка 2"),
                x + STRIP_X + 6,
                y + STRIP2_Y + 4,
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