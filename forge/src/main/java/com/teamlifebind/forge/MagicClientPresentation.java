package com.teamlifebind.forge;

import com.teamlifebind.common.SpellCatalogue;
import com.teamlifebind.common.SpellDefinition;
import com.teamlifebind.common.SpellGeometry;
import com.teamlifebind.common.NativeHud;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The HUD only displays server state; GUI choices send slot intent, never spell power. */
public final class MagicClientPresentation {
    private static MagicSyncPayload snapshot = new MagicSyncPayload(100, 100, 0, 0, List.of(), List.of());
    private static Consumer<MagicInputPayload> sender = ignored -> {};
    private static Object level, player;
    private static boolean hadControl;
    private static int elapsed;
    private static float oldPose, castingPose;
    private static long animationAge;
    private static int castTotal;
    private static final int[] cooldownTotals = new int[3];
    private MagicClientPresentation() {}
    public static void receive(MagicSyncPayload value) {
        if (value.learned().size() > 3 || value.learned().size() != value.cooldowns().size() || value.learned().stream().anyMatch(id -> SpellCatalogue.byId(id) == null)) return;
        int previousCast = Math.max(0, snapshot.castRemaining() - elapsed);
        if (value.castRemaining() == 0) castTotal = 0;
        else if (previousCast == 0 || value.selected() != snapshot.selected() || value.castRemaining() > previousCast + 2) castTotal = value.castRemaining();
        else castTotal = Math.max(castTotal, value.castRemaining());
        for (int slot = 0; slot < 3; slot++) {
            int remaining = slot < value.cooldowns().size() ? value.cooldowns().get(slot) : 0;
            boolean sameSpell = slot < snapshot.learned().size() && slot < value.learned().size()
                && snapshot.learned().get(slot).equals(value.learned().get(slot));
            cooldownTotals[slot] = remaining == 0 ? 0 : sameSpell ? Math.max(cooldownTotals[slot], remaining) : remaining;
        }
        snapshot = value; elapsed = 0;
    }
    public static void tick(Minecraft client, boolean channel, Consumer<MagicInputPayload> send) {
        sender = send;
        if (client.level != level || client.player != player) {
            if (hadControl && channel) send.accept(new MagicInputPayload(0, MagicInputPayload.CANCEL));
            snapshot = new MagicSyncPayload(100, 100, 0, 0, List.of(), List.of()); elapsed = 0;
            level = client.level; player = client.player; hadControl = false;
            oldPose = castingPose = 0; animationAge = 0;
            castTotal = 0; java.util.Arrays.fill(cooldownTotals, 0);
        }
        elapsed++;
        boolean control = channel && controls(client);
        if (hadControl && !control && channel) send.accept(new MagicInputPayload(0, MagicInputPayload.CANCEL));
        hadControl = control;
        oldPose = castingPose;
        float desired = control && snapshot.castRemaining() > elapsed ? 1 : 0;
        castingPose += (desired - castingPose) * 0.28F;
        if (castingPose < 0.001F) castingPose = 0;
        animationAge++;
    }
    public static boolean focus(Minecraft client) { return client.player != null && !snapshot.learned().isEmpty(); }
    public static boolean controls(Minecraft client) { return focus(client) && client.level != null && client.getConnection() != null && client.player.isAlive() && !client.player.isSpectator() && client.gui.screen() == null && client.gui.overlay() == null && client.isWindowActive() && client.mouseHandler.isMouseGrabbed(); }
    public static void cast() { if (!snapshot.learned().isEmpty()) sender.accept(new MagicInputPayload(snapshot.selected(), MagicInputPayload.CAST)); }
    public static void select(int slot) { if (slot < 0 || slot >= snapshot.learned().size()) return; sender.accept(new MagicInputPayload(slot, MagicInputPayload.SELECT)); snapshot = new MagicSyncPayload(snapshot.mana(), snapshot.maximum(), slot, snapshot.castRemaining(), snapshot.learned(), snapshot.cooldowns()); }
    public static void scroll(double direction) { int count = snapshot.learned().size(); if (count > 0 && direction != 0) select(Math.floorMod(snapshot.selected() + (direction > 0 ? -1 : 1), count)); }
    public static int selected() { return snapshot.selected(); }
    public static boolean shouldTransform(net.minecraft.world.item.ItemStack stack, net.minecraft.world.InteractionHand hand) {
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND && castingPose > 0.001F
            && stack.getItem() instanceof TeamMagicItem item && item.kind() == TeamMagicItem.Kind.GRIMOIRE;
    }
    /** A server-synchronized chant gently raises the held book; no local animation authorizes a cast. */
    public static void transform(PoseStack pose, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        SpellDefinition spell = at(snapshot.selected());
        if (client.player == null || spell == null) return;
        float amount = oldPose + (castingPose - oldPose) * Math.clamp(partialTick, 0, 1);
        double time = (animationAge + partialTick) / 20.0;
        double side = client.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
        SpellGeometry.Motion motion = spell.behaviour().visual().motion();
        double lift = switch (motion) { case RISE -> 0.02 * Math.sin(time * 2); case PULSE -> 0.012 * Math.cos(time * 3); default -> 0.004 * Math.sin(time * 2); };
        double turn = switch (motion) { case SPIN, ORBIT -> Math.sin(time * 2) * 4; case COUNTER_ROTATE -> Math.cos(time * 2) * 4; case FOLD -> Math.sin(time * 3) * 7; default -> Math.sin(time) * 1.5; };
        pose.translate(side * -0.12 * amount, (0.16 + lift) * amount, -0.10 * amount);
        pose.rotateDegrees(Axis.XP, (float) (-16 * amount));
        pose.rotateDegrees(Axis.ZP, (float) (side * turn * amount));
    }
    public static List<String> learned() { return snapshot.learned(); }
    public static SpellDefinition at(int slot) { return slot < 0 || slot >= snapshot.learned().size() ? null : SpellCatalogue.byId(snapshot.learned().get(slot)); }
    public static int cooldown(int slot) { return slot < 0 || slot >= snapshot.cooldowns().size() ? 0 : Math.max(0, snapshot.cooldowns().get(slot) - elapsed); }
    static String name(SpellDefinition spell) { return spell == null ? Component.translatable("magic.teamlifebind.empty_slot").getString() : Component.translatable("spell.teamlifebind." + spell.id()).getString(); }
    public static void renderHud(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden() || client.gui.screen() != null && !(client.gui.screen() instanceof com.teamlifebind.clientui.HudEditorScreen)
                || client.gui.overlay() != null || !client.player.isAlive() || client.player.isSpectator()) return;
        com.teamlifebind.clientui.ArchiveHud.magic(graphics, hudModel());
    }

    static NativeHud.Magic hudModel() {
        var slots = new java.util.ArrayList<NativeHud.Slot>(3);
        for (int index = 0; index < 3; index++) {
            SpellDefinition spell = at(index);
            int remaining = cooldown(index);
            boolean affordable = spell != null && snapshot.mana() >= spell.manaCost();
            String state = spell == null ? "" : remaining > 0 ? NativeHud.seconds(remaining)
                : NativeHudRenderer.text(affordable ? "ready" : "low_mana");
            slots.add(new NativeHud.Slot(name(spell), state, spell == null ? NativeHud.QUIET : TeamMagicRuntime.color(spell.school()),
                spell == null ? 0 : spell.tier(), spell == null ? 0 : spell.manaCost(), remaining,
                Math.max(remaining, cooldownTotals[index]), spell != null, affordable));
        }
        return new NativeHud.Magic(NativeHudRenderer.text("mana"),
            NativeHudRenderer.text("mana"), snapshot.learned().isEmpty() ? NativeHudRenderer.text("unlearned_hint")
                : NativeHudRenderer.text("magic_hint", TeamMagicClient.CAST.getTranslatedKeyMessage(),
                    TeamMagicClient.WHEEL.getTranslatedKeyMessage()), NativeHudRenderer.text("casting"),
            snapshot.mana(), snapshot.maximum(), snapshot.selected(), slots,
            Math.max(0, snapshot.castRemaining() - elapsed), castTotal);
    }
}
