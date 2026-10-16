package dev.slc.client.gui;

import dev.slc.admin.AdminAction;
import dev.slc.client.ClientAdminKitCache;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.network.C2SAdminActionPacket;
import dev.slc.network.ModNetwork;
import dev.slc.role.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Deliberately plain OP panel: clear controls first, every mutation routes through C2SAdminActionPacket. */
public final class AdminPanelScreen extends Screen {
    private enum Tab { ROUND, PLAYERS, POWERS, ITEMS, MAP_079, LOADOUTS, DUMMIES, MODERATION }

    private Tab tab = Tab.ROUND;
    private UUID selectedPlayer;
    private int roleIndex;
    private EditBox itemId;
    private EditBox itemCount;
    private static final int BG = 0xEE101318;
    private static final int PANEL = 0xFF191E25;
    private static final int ACCENT = 0xFF4FA3DD;

    public AdminPanelScreen() {
        super(Component.literal("SLC ADMIN PANEL"));
    }

    @Override
    protected void init() {
        if (Minecraft.getInstance().player != null && selectedPlayer == null) selectedPlayer = Minecraft.getInstance().player.getUUID();
        int x = 12;
        for (Tab value : Tab.values()) {
            Tab captured = value;
            int width = value == Tab.MAP_079 ? 76 : 62;
            addRenderableWidget(Button.builder(Component.literal(label(value)), b -> {
                tab = captured;
                rebuild();
            }).bounds(x, 16, width, 20).build());
            x += width + 3;
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int x = 12;
        for (Tab value : Tab.values()) {
            Tab captured = value;
            int width = value == Tab.MAP_079 ? 76 : 62;
            addRenderableWidget(Button.builder(Component.literal(label(value)), b -> {
                tab = captured;
                rebuild();
            }).bounds(x, 16, width, 20).build());
            x += width + 3;
        }
        switch (tab) {
            case ROUND -> buildRound();
            case PLAYERS -> buildPlayers(false);
            case POWERS -> buildPowers();
            case ITEMS -> buildItems();
            case MAP_079 -> buildMap();
            case LOADOUTS -> buildLoadouts();
            case DUMMIES -> buildDummies();
            case MODERATION -> buildPlayers(true);
        }
    }

    private void buildRound() {
        addAction("START ROUND", 24, 64, 128, AdminAction.ROUND_START);
        addAction("RESTART → LOBBY", 24, 90, 128, AdminAction.ROUND_RESTART);
        addAction("FORCE MTF WAVE", 24, 126, 128, AdminAction.FORCE_MTF_WAVE);
        addAction("FORCE CHAOS WAVE", 24, 152, 128, AdminAction.FORCE_CHAOS_WAVE);
        addAction("END: DRAW", 190, 64, 110, AdminAction.ROUND_END_DRAW);
        addAction("END: FOUNDATION", 190, 90, 110, AdminAction.ROUND_END_FOUNDATION);
        addAction("END: CHAOS", 190, 116, 110, AdminAction.ROUND_END_CHAOS);
        addAction("END: SCP", 190, 142, 110, AdminAction.ROUND_END_SCP);
    }

    private void buildPlayers(boolean moderation) {
        int y = 62;
        List<PlayerInfo> players = onlinePlayers();
        for (PlayerInfo info : players) {
            UUID id = info.getProfile().getId();
            String name = info.getProfile().getName();
            addRenderableWidget(Button.builder(Component.literal((id.equals(selectedPlayer) ? "> " : "") + name), b -> {
                selectedPlayer = id;
                rebuild();
            }).bounds(20, y, 170, 20).build());
            y += 22;
        }
        if (moderation) {
            addAction("KICK SELECTED", 220, 70, 140, AdminAction.KICK);
            addAction("BAN SELECTED", 220, 96, 140, AdminAction.BAN);
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("< ROLE"), b -> { roleIndex = Math.floorMod(roleIndex - 1, Role.values().length); rebuild(); })
                .bounds(220, 66, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ROLE >"), b -> { roleIndex = (roleIndex + 1) % Role.values().length; rebuild(); })
                .bounds(380, 66, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("APPLY " + Role.values()[roleIndex].name()), b ->
                send(AdminAction.SET_ROLE, selectedPlayer, Role.values()[roleIndex].name(), 0)).bounds(300, 66, 76, 20).build());
    }

    private void buildPowers() {
        buildTargetSummary();
        addAction("TOGGLE GOD MODE", 24, 100, 160, AdminAction.TOGGLE_GOD);
        addAction("TOGGLE NO-CLIP", 24, 126, 160, AdminAction.TOGGLE_NOCLIP);
    }

    private void buildItems() {
        itemId = new EditBox(font, 24, 74, 248, 20, Component.literal("item id"));
        itemId.setValue("minecraft:stone");
        itemId.setMaxLength(128);
        addRenderableWidget(itemId);
        itemCount = new EditBox(font, 280, 74, 42, 20, Component.literal("count"));
        itemCount.setValue("1");
        itemCount.setMaxLength(2);
        addRenderableWidget(itemCount);
        addRenderableWidget(Button.builder(Component.literal("GIVE TO SELECTED"), b -> {
            int count = 1;
            try { count = Integer.parseInt(itemCount.getValue()); } catch (NumberFormatException ignored) { }
            send(AdminAction.GIVE_ITEM, selectedPlayer, itemId.getValue(), count);
        }).bounds(24, 102, 180, 20).build());
    }

    private void buildMap() {
        addRenderableWidget(Button.builder(Component.literal("GIVE 0-7-9 MAP TABLET"), b -> send(AdminAction.GIVE_079_MAP, selectedPlayer, "", 0))
                .bounds(24, 74, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT ZONE MARKER"), b -> send(AdminAction.GIVE_ZONE_MARKER, null, "EXIT", -1))
                .bounds(24, 154, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("< ROLE"), b -> { roleIndex = Math.floorMod(roleIndex - 1, Role.values().length); rebuild(); })
                .bounds(210, 154, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ROLE >"), b -> { roleIndex = (roleIndex + 1) % Role.values().length; rebuild(); })
                .bounds(364, 154, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("POINT"), b -> send(AdminAction.GIVE_ZONE_MARKER, null, "SPAWN_POINT", roleIndex))
                .bounds(286, 154, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SPAWN ZONE"), b -> send(AdminAction.GIVE_ZONE_MARKER, null, "SPAWN_ZONE", roleIndex))
                .bounds(210, 180, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("LIFT CABIN"), b -> send(AdminAction.GIVE_ZONE_MARKER, null, "ELEVATOR_CABIN", -1))
                .bounds(326, 180, 108, 20).build());
    }

    private void buildLoadouts() {
        addRenderableWidget(Button.builder(Component.literal("< ROLE"), b -> { roleIndex = Math.floorMod(roleIndex - 1, Role.values().length); requestKit(); rebuild(); })
                .bounds(24, 58, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ROLE >"), b -> { roleIndex = (roleIndex + 1) % Role.values().length; requestKit(); rebuild(); })
                .bounds(254, 58, 72, 20).build());
        requestKit();
    }

    private void buildDummies() {
        addRenderableWidget(Button.builder(Component.literal("STATIC DUMMY"), b -> send(AdminAction.GIVE_DUMMY_SPAWNER, null, "STATIC", 0))
                .bounds(24, 74, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("AUTO-RESPAWN DUMMY"), b -> send(AdminAction.GIVE_DUMMY_SPAWNER, null, "RESPAWN", 0))
                .bounds(24, 100, 170, 20).build());
        addRenderableWidget(Button.builder(Component.literal("AI: PATROL"), b -> send(AdminAction.GIVE_DUMMY_SPAWNER, null, "AI:PATROL", 0))
                .bounds(24, 126, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("AI: COMBAT"), b -> send(AdminAction.GIVE_DUMMY_SPAWNER, null, "AI:COMBAT", 0))
                .bounds(24, 152, 140, 20).build());
    }

    private void buildTargetSummary() {
        String target = selectedName();
        addRenderableWidget(Button.builder(Component.literal("TARGET: " + target), b -> cyclePlayer()).bounds(24, 64, 220, 20).build());
    }

    private void addAction(String label, int x, int y, int width, AdminAction action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> send(action, selectedPlayer, "", 0)).bounds(x, y, width, 20).build());
    }

    private void cyclePlayer() {
        List<PlayerInfo> players = onlinePlayers();
        if (players.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < players.size(); i++) if (players.get(i).getProfile().getId().equals(selectedPlayer)) index = i + 1;
        selectedPlayer = players.get(index % players.size()).getProfile().getId();
        rebuild();
    }

    private List<PlayerInfo> onlinePlayers() {
        if (Minecraft.getInstance().getConnection() == null) return List.of();
        return new ArrayList<>(Minecraft.getInstance().getConnection().getOnlinePlayers());
    }

    private String selectedName() {
        for (PlayerInfo info : onlinePlayers()) if (info.getProfile().getId().equals(selectedPlayer)) return info.getProfile().getName();
        return "—";
    }

    private void requestKit() {
        send(AdminAction.KIT_REQUEST, null, Role.values()[roleIndex].name(), 0);
    }

    private void send(AdminAction action, UUID target, String value, int number) {
        ModNetwork.CHANNEL.sendToServer(new C2SAdminActionPacket(action, target, value, number));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG);
        graphics.fill(10, 44, width - 10, height - 12, PANEL);
        graphics.drawString(font, "SLC ADMIN PANEL  •  OP 4", 14, 4, ACCENT, false);
        graphics.drawString(font, titleForTab(), 24, 48, 0xFFFFFFFF, false);
        switch (tab) {
            case ROUND -> drawRoundHelp(graphics);
            case PLAYERS -> graphics.drawString(font, "Выбранный игрок получает роль, набор и телепорт на спавн.", 24, 186, 0xFFB8C2CC, false);
            case POWERS -> graphics.drawString(font, "No-clip и god mode снимаются автоматически при потере OP 4 или выходе.", 24, 158, 0xFFB8C2CC, false);
            case ITEMS -> graphics.drawString(font, "Идентификатор из любого загруженного мода; ключ-карты — только scpfr:*.", 24, 132, 0xFFB8C2CC, false);
            case MAP_079 -> drawMapHelp(graphics);
            case LOADOUTS -> drawLoadoutGrid(graphics);
            case DUMMIES -> drawDummyHelp(graphics);
            case MODERATION -> graphics.drawString(font, "Бан постоянный по UUID. Нельзя кикнуть/забанить себя через панель.", 24, 150, 0xFFB8C2CC, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRoundHelp(GuiGraphics graphics) {
        graphics.drawString(font, "Принудительные волны бесплатны: не тратят билеты и не меняют 6-минутный таймер.", 24, 190, 0xFFB8C2CC, false);
    }

    private void drawMapHelp(GuiGraphics graphics) {
        graphics.drawString(font, "Карта 079 открывается отдельным инструментом 0-7-9. Эта вкладка — её SLC-связь.", 24, 108, 0xFFB8C2CC, false);
        graphics.drawString(font, "Маркер: ЛКМ — первая точка; Shift+ПКМ — применить объём. Точка спавна ставится ПКМ.", 24, 126, 0xFFB8C2CC, false);
        graphics.drawCenteredString(font, Role.values()[roleIndex].getDisplayName(), 322, 158, 0xFFFFFFFF);
    }

    private void drawDummyHelp(GuiGraphics graphics) {
        graphics.drawString(font, "Static • Auto-respawn • AI (patrol/combat). Спавнеры доступны в инструментах настройки карты.", 24, 74, 0xFFB8C2CC, false);
    }

    private void drawLoadoutGrid(GuiGraphics graphics) {
        Role role = Role.values()[roleIndex];
        graphics.drawCenteredString(font, role.getDisplayName(), 175, 63, 0xFFFFFFFF);
        graphics.drawString(font, "ЛКМ: копия предмета из руки  •  ПКМ: очистить", 24, 86, 0xFFB8C2CC, false);
        int originX = 24;
        int originY = 106;
        for (int slot = 0; slot < SlcLoadoutSavedData.INVENTORY_SLOTS; slot++) {
            int x = originX + (slot % 9) * 20;
            int y = originY + (slot / 9) * 20;
            graphics.fill(x, y, x + 18, y + 18, 0xFF080B10);
            graphics.fill(x, y, x + 18, y + 1, ACCENT);
            ItemStack stack = ClientAdminKitCache.get(role, slot);
            if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
        }
        int armorX = originX + 202;
        int armorY = originY + 22;
        graphics.fill(armorX, armorY, armorX + 34, armorY + 34, 0xFF080B10);
        graphics.fill(armorX, armorY, armorX + 34, armorY + 1, 0xFFF1C40F);
        ItemStack armor = ClientAdminKitCache.get(role, SlcLoadoutSavedData.INVENTORY_SLOTS);
        if (!armor.isEmpty()) graphics.renderItem(armor, armorX + 9, armorY + 9);
        graphics.drawString(font, "SLC", armorX + 7, armorY + 38, 0xFFF1C40F, false);
        graphics.drawString(font, "ARMOR", armorX + 1, armorY + 48, 0xFFF1C40F, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.LOADOUTS) {
            int originX = 24;
            int originY = 106;
            int slot = slotAt(mouseX, mouseY, originX, originY);
            if (slot == -1 && mouseX >= originX + 202 && mouseX < originX + 236 && mouseY >= originY + 22 && mouseY < originY + 56) {
                slot = SlcLoadoutSavedData.INVENTORY_SLOTS;
            }
            if (slot >= 0) {
                send(button == 1 ? AdminAction.KIT_CLEAR : AdminAction.KIT_SAVE_HAND, null, Role.values()[roleIndex].name(), slot);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int slotAt(double mouseX, double mouseY, int originX, int originY) {
        int col = (int) ((mouseX - originX) / 20);
        int row = (int) ((mouseY - originY) / 20);
        if (col < 0 || col >= 9 || row < 0 || row >= 4) return -1;
        double xIn = (mouseX - originX) % 20;
        double yIn = (mouseY - originY) % 20;
        return xIn < 18 && yIn < 18 ? row * 9 + col : -1;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String label(Tab tab) {
        return switch (tab) {
            case ROUND -> "РАУНД";
            case PLAYERS -> "ИГРОКИ";
            case POWERS -> "РЕЖИМ";
            case ITEMS -> "ПРЕДМЕТЫ";
            case MAP_079 -> "079 / КАРТА";
            case LOADOUTS -> "НАБОРЫ";
            case DUMMIES -> "DUMMIES";
            case MODERATION -> "МОДЕРАЦИЯ";
        };
    }

    private String titleForTab() { return label(tab); }
}
