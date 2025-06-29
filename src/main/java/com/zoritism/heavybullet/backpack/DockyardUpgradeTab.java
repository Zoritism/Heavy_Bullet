package com.zoritism.heavybullet.backpack;

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
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.UV;

import java.util.Map;

public class DockyardUpgradeTab extends UpgradeSettingsTab<DockyardUpgradeContainer> {

    public static final ButtonDefinition.Toggle<Boolean> BUTTON_DOCKYARD = ButtonDefinitions.createToggleButtonDefinition(
            Map.of(
                    false, GuiHelper.getButtonStateData(
                            new UV(192, 48),
                            "button.heavybullet.dockyard_1",
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    ),
                    true, GuiHelper.getButtonStateData(
                            new UV(208, 48),
                            "button.heavybullet.dockyard_2",
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    )
            )
    );

    // Параметры для содержимого (оставляем как было)
    private static final int CONTENT_OFFSET_X = 44;
    private static final int BLOCK_WIDTH = 160;
    private static final int BLOCK_LINE_HEIGHT = 14;
    private static final int BLOCK_HEIGHT = BLOCK_LINE_HEIGHT * 2 + 6;
    private static final int BLOCK_Y = 24;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_Y_OFFSET = BLOCK_Y + BLOCK_HEIGHT + 16;
    private static final int BUTTON_SPACING = 10;
    private static final int VERTICAL_BOTTOM_PADDING = 12;
    private static final int BUTTONS_BOTTOM = BUTTON_Y_OFFSET + BUTTON_HEIGHT;

    // Рамка: левая сторона ровно по левому краю вкладки (FRAME_LEFT_OFFSET = 0)
    // Правая сторона как ты настроил (FRAME_RIGHT_PADDING = -20)
    private static final int FRAME_LEFT_OFFSET = 0;
    private static final int FRAME_RIGHT_PADDING = -20;
    private static final int FRAME_WIDTH = CONTENT_OFFSET_X + BLOCK_WIDTH + FRAME_RIGHT_PADDING - FRAME_LEFT_OFFSET;
    private static final int FRAME_HEIGHT = BUTTONS_BOTTOM + VERTICAL_BOTTOM_PADDING;

    // Для хранения состояния кнопок при наведении
    private boolean leftButtonHovered = false;
    private boolean rightButtonHovered = false;

    public DockyardUpgradeTab(DockyardUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
        super(upgradeContainer, position, screen,
                Component.translatable("gui.heavybullet.dockyard.title"),
                Component.translatable("gui.heavybullet.dockyard.tooltip"));

        // Вкладка теперь расширяет рамку, не смещая содержимое
        openTabDimension = new Dimension(FRAME_WIDTH, FRAME_HEIGHT);

        addHideableChild(
                new ToggleButton<>(
                        new Position(x + 3, y + 24),
                        BUTTON_DOCKYARD,
                        btn -> {},
                        () -> false
                )
        );
    }

    @Override
    protected void moveSlotsToTab() {}

    @Override
    protected void renderBg(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        super.renderBg(graphics, minecraft, mouseX, mouseY);

        if (!isOpen) {
            return;
        }

        // Содержимое (заглушки и кнопки) отрисовываем как было!
        int xBlock = x + CONTENT_OFFSET_X;
        int yBlock = y + BLOCK_Y;
        graphics.fill(xBlock, yBlock, xBlock + BLOCK_WIDTH, yBlock + BLOCK_HEIGHT, 0xFFAAAAAA);
        graphics.drawString(Minecraft.getInstance().font,
                Component.literal("Заглушка 1"), xBlock + 6, yBlock + 4, 0x404040, false);
        graphics.drawString(Minecraft.getInstance().font,
                Component.literal("Заглушка 2"), xBlock + 6, yBlock + 4 + BLOCK_LINE_HEIGHT, 0x404040, false);

        int btnWidth = (BLOCK_WIDTH - BUTTON_SPACING) / 2;
        int btn1X = xBlock;
        int btn2X = xBlock + btnWidth + BUTTON_SPACING;
        int btnY = y + BUTTON_Y_OFFSET;

        leftButtonHovered = isPointInRect(mouseX, mouseY, btn1X, btnY, btnWidth, BUTTON_HEIGHT);
        rightButtonHovered = isPointInRect(mouseX, mouseY, btn2X, btnY, btnWidth, BUTTON_HEIGHT);

        // Левая кнопка
        graphics.fill(
                btn1X, btnY,
                btn1X + btnWidth, btnY + BUTTON_HEIGHT,
                leftButtonHovered ? 0xFFBBBBBB : 0xFF888888
        );
        graphics.drawString(Minecraft.getInstance().font,
                Component.literal("Кнопка 1"),
                btn1X + (btnWidth - Minecraft.getInstance().font.width("Кнопка 1")) / 2,
                btnY + 5, 0xFFFFFF, false);

        // Правая кнопка
        graphics.fill(
                btn2X, btnY,
                btn2X + btnWidth, btnY + BUTTON_HEIGHT,
                rightButtonHovered ? 0xFFBBBBBB : 0xFF888888
        );
        graphics.drawString(Minecraft.getInstance().font,
                Component.literal("Кнопка 2"),
                btn2X + (btnWidth - Minecraft.getInstance().font.width("Кнопка 2")) / 2,
                btnY + 5, 0xFFFFFF, false);
    }

    @Override
    public int getWidth() {
        return isOpen ? FRAME_WIDTH : super.getWidth();
    }

    @Override
    public int getHeight() {
        return isOpen ? FRAME_HEIGHT : super.getHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOpen) {
            int xBlock = x + CONTENT_OFFSET_X;
            int btnWidth = (BLOCK_WIDTH - BUTTON_SPACING) / 2;
            int btn1X = xBlock;
            int btn2X = xBlock + btnWidth + BUTTON_SPACING;
            int btnY = y + BUTTON_Y_OFFSET;

            if (isPointInRect(mouseX, mouseY, btn1X, btnY, btnWidth, BUTTON_HEIGHT)) {
                // TODO: Реакция на нажатие левой кнопки
                return true;
            }
            if (isPointInRect(mouseX, mouseY, btn2X, btnY, btnWidth, BUTTON_HEIGHT)) {
                // TODO: Реакция на нажатие правой кнопки
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isPointInRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}