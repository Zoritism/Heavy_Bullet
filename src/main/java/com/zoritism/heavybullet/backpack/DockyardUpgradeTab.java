package com.zoritism.heavybullet.backpack;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinition;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinitions;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Button;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.widgets.RenderableWidget;

public class DockyardUpgradeTab extends UpgradeSettingsTab<DockyardUpgradeContainer> {

    private static final ButtonDefinition BUTTON_1 = ButtonDefinitions.createButtonDefinition(
            GuiHelper.getButtonStateData(0, "button.heavybullet.dockyard_1", Dimension.SQUARE_16, new Position(1, 1))
    );
    private static final ButtonDefinition BUTTON_2 = ButtonDefinitions.createButtonDefinition(
            GuiHelper.getButtonStateData(0, "button.heavybullet.dockyard_2", Dimension.SQUARE_16, new Position(1, 1))
    );

    public DockyardUpgradeTab(DockyardUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
        super(upgradeContainer, position, screen,
                Component.translatable("gui.heavybullet.dockyard.title"),
                Component.translatable("gui.heavybullet.dockyard.tooltip"));

        int x0 = x + 5;
        int y0 = y + 22;

        // Вертикальный список из двух строк (заглушки) в рамке
        addRenderableChild(new LabelWithBox(new Position(x0, y0), Component.translatable("gui.heavybullet.dockyard.text1")));
        addRenderableChild(new LabelWithBox(new Position(x0, y0 + 12), Component.translatable("gui.heavybullet.dockyard.text2")));

        // Две горизонтальные кнопки
        addRenderableChild(new Button(new Position(x0, y0 + 28), BUTTON_1, () -> {}));
        addRenderableChild(new Button(new Position(x0 + 40, y0 + 28), BUTTON_2, () -> {}));
    }

    @Override
    protected void moveSlotsToTab() {}

    // Внутренний класс для отрисовки надписи в рамке (заглушка)
    static class LabelWithBox extends RenderableWidget {
        private final Component text;

        public LabelWithBox(Position pos, Component text) {
            super(pos, new Dimension(110, 12));
            this.text = text;
        }

        @Override
        public void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, float z) {
            graphics.drawString(Minecraft.getInstance().font, text, getX() + 4, getY() + 2, 0x404040);
            graphics.drawRectangle(getX(), getY(), getWidth(), getHeight(), 0xFFAAAAAA);
        }
    }
}