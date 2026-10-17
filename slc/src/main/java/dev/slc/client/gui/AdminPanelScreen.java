package dev.slc.client.gui;

import dev.slc.admin.AdminAction;
import dev.slc.client.ClientAdminKitCache;
import dev.slc.client.ClientAdminPanelState;
import dev.slc.client.ClientRoundCache;
import dev.slc.client.ClientRoundControlCache;
import dev.slc.client.ClientRoleSkinCache;
import dev.slc.controls.RoundControl;
import dev.slc.loadout.SlcLoadoutSavedData;
import dev.slc.init.ModItems;
import dev.slc.network.C2SAdminActionPacket;
import dev.slc.network.ModNetwork;
import dev.slc.role.Role;
import com.mojang.blaze3d.platform.InputConstants;
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
    private enum Tab { ROUND, CONTROLS, PLAYERS, POWERS, ITEMS, MAP_079, LOADOUTS, SKINS, DUMMIES, MODERATION }

    private Tab tab = Tab.ROUND;
    private UUID selectedPlayer;
    private int roleIndex;
    private EditBox itemId;
    private EditBox itemCount;
    private EditBox skinUrl;
    private Role skinRole = Role.CLASS_D;
    private MapGroup mapGroup = MapGroup.CIVILIANS;
    private Role mapRole = Role.CLASS_D;
    private int capturingRoundControl = -1;

    private enum MapGroup {
        CIVILIANS("ПЕРСОНАЛ", new Role[]{Role.CLASS_D, Role.SCIENTIST, Role.FACILITY_GUARD}),
        REINFORCEMENTS("ПОДКРЕПЛЕНИЯ", new Role[]{Role.MTF_PRIVATE, Role.MTF_SERGEANT, Role.MTF_SPECIALIST, Role.MTF_CAPTAIN,
                Role.CHAOS_CONSCRIPT, Role.CHAOS_MARAUDER, Role.CHAOS_REPRESSOR}),
        SCPS("SCP", new Role[]{Role.SCP_079, Role.SCP_106, Role.SCP_939, Role.SCP_173, Role.SCP_049, Role.SCP_049_2, Role.SCP_096}),
        OTHER("ПРОЧЕЕ", new Role[]{Role.SPECTATOR});

        private final String label;
        private final Role[] roles;
        MapGroup(String label, Role[] roles) { this.label = label; this.roles = roles; }
    }
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
            case CONTROLS -> buildControls();
            case PLAYERS -> buildPlayers(false);
            case POWERS -> buildPowers();
            case ITEMS -> buildItems();
            case MAP_079 -> buildMap();
            case LOADOUTS -> buildLoadouts();
            case SKINS -> buildSkins();
            case DUMMIES -> buildDummies();
            case MODERATION -> buildPlayers(true);
        }
    }

    private void buildRound() {
        addAction("START ROUND", 24, 64, 128, AdminAction.ROUND_START);
        addAction("RESTART → LOBBY", 24, 90, 128, AdminAction.ROUND_RESTART);
        addAction("FORCE MTF WAVE", 24, 126, 128, AdminAction.FORCE_MTF_WAVE);
        addAction("FORCE CHAOS WAVE", 24, 152, 128, AdminAction.FORCE_CHAOS_WAVE);
        addAction("STOP ROUND", 190, 64, 110, AdminAction.ROUND_STOP);
        addAction("END: FOUNDATION", 190, 90, 110, AdminAction.ROUND_END_FOUNDATION);
        addAction("END: CHAOS", 190, 116, 110, AdminAction.ROUND_END_CHAOS);
        addAction("END: SCP", 190, 142, 110, AdminAction.ROUND_END_SCP);
        addAction("НЕ ОСТАНАВЛИВАТЬ РАУНД: " + (ClientRoundCache.isAutomaticRoundEndLocked() ? "ВКЛ" : "ВЫКЛ"),
                24, 184, 220, AdminAction.ROUND_TOGGLE_AUTO_END);
    }

    private void buildControls() {
        send(AdminAction.CONTROL_REQUEST, null, "", 0);
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
        addRenderableWidget(Button.builder(Component.literal("ОТКРЫТЬ ИНСТРУМЕНТ 079"), b -> send(AdminAction.GIVE_079_MAP, selectedPlayer, "", 0))
                .bounds(24, 64, 182, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ЗОНА ВЫХОДА"), b -> send(AdminAction.GIVE_EXIT_ZONE_MARKER, null, "", 0))
                .bounds(212, 64, 128, 20).build());
        addRenderableWidget(Button.builder(Component.literal("КАБИНА ЛИФТА"), b -> send(AdminAction.GIVE_ELEVATOR_CABIN_MARKER, null, "", 0))
                .bounds(346, 64, 128, 20).build());
        addRenderableWidget(Button.builder(Component.literal("106: ПРИБЫТИЕ"), b -> send(AdminAction.GIVE_POCKET_ARRIVAL_MARKER, null, "", 0))
                .bounds(24, 90, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("106: ВЫХОД"), b -> send(AdminAction.GIVE_POCKET_ESCAPE_MARKER, null, "", 0))
                .bounds(170, 90, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("106: ВОЗВРАТ"), b -> send(AdminAction.GIVE_POCKET_RETURN_MARKER, null, "", 0))
                .bounds(296, 90, 140, 20).build());

        int groupX = 24;
        for (MapGroup group : MapGroup.values()) {
            MapGroup captured = group;
            int width = group == MapGroup.REINFORCEMENTS ? 122 : 82;
            addRenderableWidget(Button.builder(Component.literal(group.label), b -> {
                mapGroup = captured;
                if (!containsRole(mapGroup, mapRole)) mapRole = mapGroup.roles[0];
                rebuild();
            }).bounds(groupX, 122, width, 18).build());
            groupX += width + 3;
        }

        int x = 24;
        int y = 150;
        for (Role role : mapGroup.roles) {
            Role captured = role;
            addRenderableWidget(Button.builder(Component.literal(role == mapRole ? "• " + role.getDisplayName() : role.getDisplayName()), b -> {
                mapRole = captured;
                rebuild();
            }).bounds(x, y, 170, 20).build());
            y += 22;
            if (y > height - 100) { y = 150; x += 178; }
        }

        addRenderableWidget(Button.builder(Component.literal("ВЫДАТЬ ТОЧКУ СПАВНА"), b ->
                send(AdminAction.GIVE_ROLE_SPAWN_POINT_MARKER, null, "", mapRole.ordinal())).bounds(24, height - 76, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ВЫДАТЬ ЗОНУ СПАВНА"), b ->
                send(AdminAction.GIVE_ROLE_SPAWN_ZONE_MARKER, null, "", mapRole.ordinal())).bounds(220, height - 76, 190, 20).build());
    }

    private static boolean containsRole(MapGroup group, Role role) {
        for (Role candidate : group.roles) if (candidate == role) return true;
        return false;
    }

    private void buildLoadouts() {
        addRenderableWidget(Button.builder(Component.literal("< ROLE"), b -> { roleIndex = Math.floorMod(roleIndex - 1, Role.values().length); requestKit(); rebuild(); })
                .bounds(24, 58, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ROLE >"), b -> { roleIndex = (roleIndex + 1) % Role.values().length; requestKit(); rebuild(); })
                .bounds(254, 58, 72, 20).build());
        requestKit();
    }

    private void buildSkins() {
        send(AdminAction.ROLE_SKIN_REQUEST, null, "", 0);
        if (!skinRole.isHuman()) skinRole = Role.CLASS_D;
        addRenderableWidget(Button.builder(Component.literal("< ROLE"), b -> { skinRole = previousHumanRole(skinRole); rebuild(); })
                .bounds(24, 66, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("ROLE >"), b -> { skinRole = nextHumanRole(skinRole); rebuild(); })
                .bounds(294, 66, 72, 20).build());
        skinUrl = new EditBox(font, 24, 102, 342, 20, Component.literal("https://…/skin.png"));
        skinUrl.setMaxLength(512);
        skinUrl.setValue(ClientRoleSkinCache.url(skinRole));
        addRenderableWidget(skinUrl);
        addRenderableWidget(Button.builder(Component.literal("SAVE HTTPS PNG"), b ->
                send(AdminAction.ROLE_SKIN_SET, null, skinUrl.getValue(), skinRole.ordinal())).bounds(24, 130, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLEAR"), b ->
                send(AdminAction.ROLE_SKIN_SET, null, "", skinRole.ordinal())).bounds(180, 130, 80, 20).build());
    }

    private static Role nextHumanRole(Role role) {
        Role[] roles = Role.values();
        for (int offset = 1; offset <= roles.length; offset++) {
            Role candidate = roles[(role.ordinal() + offset) % roles.length];
            if (candidate.isHuman()) return candidate;
        }
        return Role.CLASS_D;
    }

    private static Role previousHumanRole(Role role) {
        Role[] roles = Role.values();
        for (int offset = 1; offset <= roles.length; offset++) {
            Role candidate = roles[Math.floorMod(role.ordinal() - offset, roles.length)];
            if (candidate.isHuman()) return candidate;
        }
        return Role.CLASS_D;
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
            case CONTROLS -> drawControls(graphics);
            case PLAYERS -> graphics.drawString(font, "Выбранный игрок получает роль, набор и телепорт на спавн.", 24, 186, 0xFFB8C2CC, false);
            case POWERS -> graphics.drawString(font, "No-clip и god mode снимаются автоматически при потере OP 4 или выходе.", 24, 158, 0xFFB8C2CC, false);
            case ITEMS -> graphics.drawString(font, "Идентификатор из любого загруженного мода; ключ-карты — только scpfr:*.", 24, 132, 0xFFB8C2CC, false);
            case MAP_079 -> drawMapHelp(graphics);
            case LOADOUTS -> drawLoadoutGrid(graphics);
            case SKINS -> drawSkinHelp(graphics);
            case DUMMIES -> drawDummyHelp(graphics);
            case MODERATION -> graphics.drawString(font, "Бан постоянный по UUID. Нельзя кикнуть/забанить себя через панель.", 24, 150, 0xFFB8C2CC, false);
        }
        String adminStatus = ClientAdminPanelState.message();
        if (!adminStatus.isEmpty()) {
            int color = ClientAdminPanelState.success() ? 0xFF77D68A : 0xFFFF7777;
            graphics.fill(18, height - 34, width - 18, height - 16, 0xEE080B10);
            graphics.drawString(font, adminStatus, 24, height - 29, color, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawControls(GuiGraphics graphics) {
        graphics.drawString(font, "УПРАВЛЕНИЕ РАУНДА • сохраняется для этого мира и применяется ко всем игрокам", 24, 58, 0xFFFFFFFF, false);
        graphics.drawString(font, capturingRoundControl >= 0
                ? "Нажми новую клавишу для: " + RoundControl.values()[capturingRoundControl].displayName() + " • ESC отмена"
                : "ЛКМ по строке — назначить клавишу. Одинаковые клавиши сервер отклонит.",
                24, 76, capturingRoundControl >= 0 ? 0xFFF1C40F : 0xFFB8C2CC, false);
        int x = 24;
        int y = 96;
        for (RoundControl control : RoundControl.values()) {
            boolean selected = capturingRoundControl == control.ordinal();
            graphics.fill(x, y, x + 280, y + 24, selected ? 0xFF384A59 : 0xFF080B10);
            graphics.fill(x, y, x + 100, y + 24, selected ? 0xFFF1C40F : ACCENT);
            graphics.drawCenteredString(font, control.displayName(), x + 50, y + 8, 0xFF101318);
            String keyName = InputConstants.getKey(ClientRoundControlCache.key(control), -1).getDisplayName().getString().toUpperCase();
            graphics.drawString(font, keyName, x + 112, y + 8, 0xFFFFFFFF, false);
            y += 28;
        }
        graphics.drawString(font, "В раунде эти клавиши заменяют ванильные действия. TAB/список игроков и E/инвентарь не откроют ванильный UI.",
                24, y + 6, 0xFFB8C2CC, false);
    }

    private void drawRoundHelp(GuiGraphics graphics) {
        graphics.drawString(font, "Принудительные волны бесплатны: не тратят билеты и не меняют 6-минутный таймер.", 24, 216, 0xFFB8C2CC, false);
        graphics.drawString(font, "«Не останавливать» блокирует только автоматическую победу; OP-кнопки STOP/END работают всегда.", 24, 230, 0xFFB8C2CC, false);
    }

    private void drawMapHelp(GuiGraphics graphics) {
        graphics.drawString(font, "Редактор карты SLC / 079 / SCP-106", 24, 44, 0xFFFFFFFF, false);
        graphics.drawString(font, "106: настрой ПРИБЫТИЕ, ВЫХОД и ВОЗВРАТ. Без полной настройки захват даёт только слепоту и обездвиживание.", 24, height - 126, 0xFFB8C2CC, false);
        graphics.drawString(font, "ЗОНА: ЛКМ = угол A, Shift+ПКМ = сохранить объём. ТОЧКА: ПКМ = добавить.", 24, height - 112, 0xFFB8C2CC, false);
        graphics.drawString(font, "Выбрано: " + mapRole.getDisplayName(), 24, height - 96, 0xFFFFFFFF, false);
        graphics.renderItem(new ItemStack(ModItems.spawnPointMarker(mapRole).get()), 195, height - 100);
        graphics.renderItem(new ItemStack(ModItems.spawnZoneMarker(mapRole).get()), 391, height - 100);

        int x = 28;
        int y = 152;
        for (Role role : mapGroup.roles) {
            graphics.renderItem(new ItemStack(ModItems.spawnZoneMarker(role).get()), x, y + 2);
            y += 22;
            if (y > height - 100) { y = 152; x += 178; }
        }
    }

    private void drawSkinHelp(GuiGraphics graphics) {
        graphics.drawString(font, "Роль: " + skinRole.getDisplayName(), 108, 72, 0xFFFFFFFF, false);
        graphics.drawString(font, "Скины доступны только человеческим ролям. SCP используют отдельные модели, не URL-скины.", 24, 166, 0xFFB8C2CC, false);
        graphics.drawString(font, "Ссылка должна быть HTTPS и вести на PNG 64×64. Пустая ссылка возвращает обычный Minecraft-скин.", 24, 180, 0xFFB8C2CC, false);
        graphics.drawString(font, "URL сохраняется в данных мира и синхронизируется всем клиентам; загрузка происходит только у клиента.", 24, 194, 0xFFB8C2CC, false);
    }

    private void drawDummyHelp(GuiGraphics graphics) {
        graphics.drawString(font, "Static • Auto-respawn • AI (patrol/combat). Спавнеры доступны в инструментах настройки карты.", 24, 74, 0xFFB8C2CC, false);
    }

    private void drawLoadoutGrid(GuiGraphics graphics) {
        Role role = Role.values()[roleIndex];
        graphics.drawCenteredString(font, role.getDisplayName(), 175, 63, 0xFFFFFFFF);
        graphics.drawString(font, "ЛКМ: копия из руки  •  ПКМ: очистить  •  AMMO: только tacz:ammo с полным NBT", 24, 86, 0xFFB8C2CC, false);
        graphics.drawString(font, "CTRL / E / G / X — глобальные клавиши раунда, не зависят от набора роли.", 24, 226, 0xFFB8C2CC, false);
        int originX = 24;
        int originY = 106;
        for (int slot = 0; slot < SlcLoadoutSavedData.AMMO_SLOT; slot++) {
            int x = originX + (slot % 9) * 20;
            int y = originY + (slot / 9) * 20;
            graphics.fill(x, y, x + 18, y + 18, 0xFF080B10);
            graphics.fill(x, y, x + 18, y + 1, ACCENT);
            ItemStack stack = ClientAdminKitCache.get(role, slot);
            if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
        }
        int ammoX = originX + 202;
        int ammoY = originY + 8;
        graphics.fill(ammoX, ammoY, ammoX + 34, ammoY + 34, 0xFF080B10);
        graphics.fill(ammoX, ammoY, ammoX + 34, ammoY + 1, 0xFFE67E22);
        ItemStack ammo = ClientAdminKitCache.get(role, SlcLoadoutSavedData.AMMO_SLOT);
        if (!ammo.isEmpty()) graphics.renderItem(ammo, ammoX + 9, ammoY + 9);
        graphics.drawString(font, "TACZ", ammoX + 7, ammoY + 38, 0xFFE67E22, false);
        graphics.drawString(font, "AMMO", ammoX + 1, ammoY + 48, 0xFFE67E22, false);

        int armorX = originX + 202;
        int armorY = originY + 76;
        graphics.fill(armorX, armorY, armorX + 34, armorY + 34, 0xFF080B10);
        graphics.fill(armorX, armorY, armorX + 34, armorY + 1, 0xFFF1C40F);
        ItemStack armor = ClientAdminKitCache.get(role, SlcLoadoutSavedData.BODY_ARMOR_SLOT);
        if (!armor.isEmpty()) graphics.renderItem(armor, armorX + 9, armorY + 9);
        graphics.drawString(font, "SLC", armorX + 7, armorY + 38, 0xFFF1C40F, false);
        graphics.drawString(font, "ARMOR", armorX + 1, armorY + 48, 0xFFF1C40F, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.CONTROLS && button == 0 && mouseX >= 24 && mouseX < 304 && mouseY >= 96
                && mouseY < 96 + RoundControl.values().length * 28) {
            capturingRoundControl = (int) ((mouseY - 96) / 28);
            return true;
        }
        if (tab == Tab.LOADOUTS) {
            int originX = 24;
            int originY = 106;
            int slot = slotAt(mouseX, mouseY, originX, originY);
            if (slot == -1 && mouseX >= originX + 202 && mouseX < originX + 236 && mouseY >= originY + 8 && mouseY < originY + 42) {
                slot = SlcLoadoutSavedData.AMMO_SLOT;
            } else if (slot == -1 && mouseX >= originX + 202 && mouseX < originX + 236 && mouseY >= originY + 76 && mouseY < originY + 110) {
                slot = SlcLoadoutSavedData.BODY_ARMOR_SLOT;
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
        int slot = row * 9 + col;
        return xIn < 18 && yIn < 18 && slot < SlcLoadoutSavedData.AMMO_SLOT ? slot : -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.CONTROLS && capturingRoundControl >= 0) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                capturingRoundControl = -1;
            } else {
                send(AdminAction.CONTROL_SET, null, "", (capturingRoundControl << 16) | (keyCode & 0xFFFF));
                capturingRoundControl = -1;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String label(Tab tab) {
        return switch (tab) {
            case ROUND -> "РАУНД";
            case CONTROLS -> "КЛАВИШИ";
            case PLAYERS -> "ИГРОКИ";
            case POWERS -> "РЕЖИМ";
            case ITEMS -> "ПРЕДМЕТЫ";
            case MAP_079 -> "079 / КАРТА";
            case LOADOUTS -> "НАБОРЫ";
            case SKINS -> "СКИНЫ";
            case DUMMIES -> "DUMMIES";
            case MODERATION -> "МОДЕРАЦИЯ";
        };
    }

    private String titleForTab() { return label(tab); }
}
