package com.zoritism.heavybullet.backpack.dockyard;

import com.zoritism.heavybullet.network.C2SHandleDockyardShipPacket;
import com.zoritism.heavybullet.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
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

    private static final int TAB_WIDTH = 103;
    private static final int TAB_HEIGHT = 92;
    private static final int BODY_HEIGHT = 92;

    // Поле чуть короче, как у AnvilUpgradeTab (84x16)
    private static final int FIELD_WIDTH = 84;
    private static final int FIELD_HEIGHT = 16;

    private static final TextureBlitData FIELD_ACTIVE = new TextureBlitData(
            GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(28, 99), new Dimension(FIELD_WIDTH, FIELD_HEIGHT)
    );
    private static final TextureBlitData FIELD_INACTIVE = new TextureBlitData(
            GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(28, 115), new Dimension(FIELD_WIDTH, FIELD_HEIGHT)
    );

    // Кнопка: синяя если слот пустой, оранжевая если в слоте корабль
    private static final ButtonDefinition.Toggle<Boolean> SLOT_BUTTON = ButtonDefinitions.createToggleButtonDefinition(
            Map.of(
                    false, GuiHelper.getButtonStateData(
                            new UV(144, 48),
                            "",
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    ),
                    true, GuiHelper.getButtonStateData(
                            new UV(128, 48),
                            "",
                            Dimension.SQUARE_16,
                            new Position(1, 1)
                    )
            )
    );

    // Позиции оставляем как в старом интерфейсе
    private static final int FIELD_X = 5;
    private static final int FIELD1_Y = 25;
    private static final int FIELD2_Y = FIELD1_Y + 18;

    private static final int BUTTON1_X = 5;
    private static final int BUTTON2_X = 5 + 20;
    private static final int BUTTONS_Y = FIELD2_Y + 24;

    public DockyardUpgradeTab(DockyardUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
        super(upgradeContainer, position, screen,
                net.minecraft.network.chat.Component.translatable("gui.heavybullet.dockyard.title"),
                net.minecraft.network.chat.Component.translatable("gui.heavybullet.dockyard.tooltip"));

        openTabDimension = new Dimension(TAB_WIDTH, TAB_HEIGHT);

        // Кнопка для первого слота (слот 0)
        addHideableChild(new ToggleButton<>(
                new Position(x + BUTTON1_X, y + BUTTONS_Y),
                SLOT_BUTTON,
                btn -> handleSlotButtonClick(0),
                () -> hasShipInSlot(0)
        ));

        // Кнопка для второго слота (слот 1)
        addHideableChild(new ToggleButton<>(
                new Position(x + BUTTON2_X, y + BUTTONS_Y),
                SLOT_BUTTON,
                btn -> handleSlotButtonClick(1),
                () -> hasShipInSlot(1)
        ));
    }

    // Проверка наличия корабля в слоте (использует NBT рюкзака, для поддержки двух слотов)
    private boolean hasShipInSlot(int slot) {
        ItemStack backpack = getBackpack();
        if (backpack == null || !backpack.hasTag()) {
            return false;
        }
        CompoundTag tag = backpack.getTag();
        // Для двух слотов: DockyardStoredShip (для 0), DockyardStoredShip1 (для 1)
        String key = slot == 0 ? "DockyardStoredShip" : "DockyardStoredShip" + slot;
        return tag.contains(key);
    }

    // Получить название корабля для слота (по NBT, если есть)
    private String getStoredShipName(int slot) {
        ItemStack backpack = getBackpack();
        if (backpack == null || !backpack.hasTag()) {
            return "";
        }
        CompoundTag tag = backpack.getTag();
        String key = slot == 0 ? "DockyardStoredShip" : "DockyardStoredShip" + slot;
        if (tag.contains(key)) {
            CompoundTag shipTag = tag.getCompound(key);
            if (shipTag.contains("vs_ship_name")) {
                return shipTag.getString("vs_ship_name");
            }
            // Fallback: если нет красивого названия, показывать id
            if (shipTag.contains("vs_ship_id")) {
                return "id:" + shipTag.getLong("vs_ship_id");
            }
            return "<ship>";
        }
        return "";
    }

    // Получение рюкзака игрока (всегда в main hand!)
    private ItemStack getBackpack() {
        return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getMainHandItem() : ItemStack.EMPTY;
    }

    // Клик по кнопке: если слот пустой — подобрать, если есть корабль — выпустить
    private void handleSlotButtonClick(int slot) {
        boolean hasShip = hasShipInSlot(slot);
        // Если пусто — собрать корабль (release=false), если есть — выпустить (release=true)
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

        // Поле 1 (верхнее)
        boolean slot1HasShip = hasShipInSlot(0);
        TextureBlitData field1 = slot1HasShip ? FIELD_ACTIVE : FIELD_INACTIVE;
        GuiHelper.blit(graphics, x + FIELD_X, y + FIELD1_Y, field1, FIELD_WIDTH, FIELD_HEIGHT);
        String name1 = getStoredShipName(0);
        graphics.drawString(Minecraft.getInstance().font,
                name1,
                x + FIELD_X + 6,
                y + FIELD1_Y + 4,
                0x404040, false);

        // Поле 2 (нижнее)
        boolean slot2HasShip = hasShipInSlot(1);
        TextureBlitData field2 = slot2HasShip ? FIELD_ACTIVE : FIELD_INACTIVE;
        GuiHelper.blit(graphics, x + FIELD_X, y + FIELD2_Y, field2, FIELD_WIDTH, FIELD_HEIGHT);
        String name2 = getStoredShipName(1);
        graphics.drawString(Minecraft.getInstance().font,
                name2,
                x + FIELD_X + 6,
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