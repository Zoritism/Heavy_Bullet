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

    public DockyardUpgradeTab(DockyardUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
        super(upgradeContainer, position, screen,
                Component.translatable("gui.heavybullet.dockyard.title"),
                Component.translatable("gui.heavybullet.dockyard.tooltip"));

        addHideableChild(
                new ToggleButton<>(
                        new Position(x + 3, y + 24),
                        BUTTON_DOCKYARD,
                        btn -> {
                            // TODO: реализация действия по клику (например, переключение режима)
                        },
                        () -> false // TODO: заменить на реальное условие
                )
        );
    }

    @Override
    protected void moveSlotsToTab() {}

    @Override
    protected void renderBg(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        super.renderBg(graphics, minecraft, mouseX, mouseY);

        int x0 = x + 5;
        int y0 = y + 22;
        drawLabelWithBox(graphics, new Position(x0, y0), Component.translatable("gui.heavybullet.dockyard.text1"));
        drawLabelWithBox(graphics, new Position(x0, y0 + 12), Component.translatable("gui.heavybullet.dockyard.text2"));
    }

    private void drawLabelWithBox(GuiGraphics graphics, Position pos, Component text) {
        int w = 110, h = 12;
        // Используем package-private методы для доступа к координатам
        // (или protected, если класс наследуется от своего пакета)
        // В SophisticatedCore Position есть методы left() и top()
        int px, py;
        try {
            px = (int) Position.class.getMethod("left").invoke(pos);
            py = (int) Position.class.getMethod("top").invoke(pos);
        } catch (Exception e) {
            throw new RuntimeException("Position does not have accessible left()/top() methods!");
        }
        graphics.fill(px, py, px + w, py + h, 0xFFAAAAAA);
        graphics.drawString(Minecraft.getInstance().font, text, px + 4, py + 2, 0x404040, false);
    }
}