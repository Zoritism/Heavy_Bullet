package com.zoritism.heavybullet.backpack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinition;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Button;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.UV;

public class DockyardUpgradeTab extends UpgradeSettingsTab<DockyardUpgradeContainer> {

    private static final ButtonDefinition BUTTON_1 = new ButtonDefinition(
            GuiHelper.getButtonStateData(new UV(192, 48), "button.heavybullet.dockyard_1", Dimension.SQUARE_16, new Position(1, 1))
    );
    private static final ButtonDefinition BUTTON_2 = new ButtonDefinition(
            GuiHelper.getButtonStateData(new UV(208, 48), "button.heavybullet.dockyard_2", Dimension.SQUARE_16, new Position(1, 1))
    );

    public DockyardUpgradeTab(DockyardUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
        super(upgradeContainer, position, screen,
                Component.translatable("gui.heavybullet.dockyard.title"),
                Component.translatable("gui.heavybullet.dockyard.tooltip"));

        int x0 = x + 5;
        int y0 = y + 22;

        // Отрисовка двух строк и рамки через addRenderable
        this.addRenderable((graphics, mouseX, mouseY, partialTicks, z) -> {
            drawLabelWithBox(graphics, new Position(x0, y0), Component.translatable("gui.heavybullet.dockyard.text1"));
            drawLabelWithBox(graphics, new Position(x0, y0 + 12), Component.translatable("gui.heavybullet.dockyard.text2"));
        });

        // Две горизонтальные кнопки
        addRenderableChild(new Button(new Position(x0, y0 + 28), BUTTON_1, () -> {}));
        addRenderableChild(new Button(new Position(x0 + 40, y0 + 28), BUTTON_2, () -> {}));
    }

    @Override
    protected void moveSlotsToTab() {}

    private void drawLabelWithBox(GuiGraphics graphics, Position pos, Component text) {
        int w = 110, h = 12;
        graphics.drawRectangle(pos.getX(), pos.getY(), w, h, 0xFFAAAAAA);
        graphics.drawString(Minecraft.getInstance().font, text, pos.getX() + 4, pos.getY() + 2, 0x404040, false);
    }
}