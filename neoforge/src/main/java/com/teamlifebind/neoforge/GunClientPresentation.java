package com.teamlifebind.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.teamlifebind.common.GunCombatState;
import com.teamlifebind.common.GunMotion;
import com.teamlifebind.common.GunProfile;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.CustomData;

/** Native client controls and original procedural poses; damage and recoil authorization come from the server. */
public final class GunClientPresentation {
    private static final GunMotion.State MOTION = new GunMotion.State();
    private static UUID heldSerial;
    private static UUID sentSerial;
    private static UUID attackClickSerial;
    private static boolean sentFiring;
    private static boolean sentAiming;
    private static boolean blockAttack;
    private static boolean blockAim;
    private static boolean active;
    private static long tick;
    private static long sentTick;
    private static Object levelIdentity;
    private static Object playerIdentity;

    private GunClientPresentation() {}

    public static void tick(Minecraft client, boolean channelOpen, Consumer<GunInputPayload> send) {
        tick++;
        if (heldSerial != null && (client.level != levelIdentity || client.player != playerIdentity)) {
            if (sentSerial != null && channelOpen && client.getConnection() != null) send.accept(GunInputPayload.CANCEL);
            sentSerial = heldSerial = null;
            attackClickSerial = null;
            active = false;
            blockAttack = client.options.keyAttack.isDown();
            blockAim = client.options.keyUse.isDown();
            MOTION.select(null, null);
        }
        levelIdentity = client.level;
        playerIdentity = client.player;
        UUID serial = client.player == null ? null : serial(client.player.getMainHandItem());
        boolean controls = channelOpen && canControl(client) && serial != null;
        if (!controls) {
            if (sentSerial != null && channelOpen && client.getConnection() != null) send.accept(GunInputPayload.CANCEL);
            sentSerial = null;
            heldSerial = null;
            attackClickSerial = null;
            active = false;
            blockAttack = client.options.keyAttack.isDown();
            blockAim = client.options.keyUse.isDown();
            MOTION.select(null, null);
            return;
        }
        if (!serial.equals(heldSerial)) {
            if (sentSerial != null) {
                send.accept(GunInputPayload.CANCEL);
                blockAttack = client.options.keyAttack.isDown();
                blockAim = client.options.keyUse.isDown();
            }
            heldSerial = serial;
            sentSerial = null;
            MOTION.select(serial, ((TeamGunItem) client.player.getMainHandItem().getItem()).profile());
        }
        active = true;
        if (!client.options.keyAttack.isDown()) blockAttack = false;
        if (!client.options.keyUse.isDown()) blockAim = false;
        boolean pressed = !blockAttack && serial.equals(attackClickSerial);
        boolean firing = !blockAttack && (client.options.keyAttack.isDown() || pressed);
        attackClickSerial = null;
        boolean aiming = !blockAim && client.options.keyUse.isDown();
        // Two distinct short clicks may have no sampled up tick between them. Preserve the release/press edge.
        if (pressed && sentFiring && serial.equals(sentSerial)) send.accept(new GunInputPayload(serial, false, aiming));
        if (pressed || !serial.equals(sentSerial) || firing != sentFiring || aiming != sentAiming || tick - sentTick >= 10L) {
            send.accept(new GunInputPayload(serial, firing, aiming));
            sentSerial = serial;
            sentFiring = firing;
            sentAiming = aiming;
            sentTick = tick;
        }
        MOTION.step(aiming, tick);
    }

    /** Observe actual up-to-down changes, so keyboard repeat never rearms a semi-automatic trigger. */
    public static void recordAttackKey(KeyMapping key, boolean down) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null || key != client.options.keyAttack) return;
        if (!down) { blockAttack = false; return; }
        if (!key.isDown() && !blockAttack && hasInitializedGun(client) && canControl(client)) {
            attackClickSerial = serial(client.player.getMainHandItem());
        }
    }

    /** Guns consume attack clicks here; their actual press edges and held state use the dedicated input payload. */
    public static void drainAttackClicks(Minecraft client) {
        if (!hasInitializedGun(client)) return;
        while (client.options.keyAttack.consumeClick()) {}
    }

    public static boolean canControl(Minecraft client) {
        return client.player != null && client.level != null && client.player.isAlive() && !client.player.isSpectator()
                && client.getConnection() != null && client.gui.screen() == null && client.gui.overlay() == null
                && client.isWindowActive() && client.mouseHandler.isMouseGrabbed();
    }

    public static boolean hasInitializedGun(Minecraft client) {
        return client.player != null && serial(client.player.getMainHandItem()) != null;
    }

    public static void receive(GunVisualPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !payload.serial().equals(serial(client.player.getMainHandItem()))) return;
        var reasons = GunCombatState.Reason.values();
        if (payload.reason() < 0 || payload.reason() >= reasons.length) return;
        boolean shot = MOTION.confirm(payload.serial(), payload.sequence(), payload.ammo(), payload.reloadRemaining(),
                payload.heat(), reasons[payload.reason()], payload.fired(), tick);
        if (shot && active && canControl(client)) {
            double aiming = MOTION.frame(1.0D).aim();
            float pitch = (float) (MOTION.profile().pitchKickDegrees() * (1.0D - aiming * 0.35D));
            client.player.setXRot(Mth.clamp(client.player.getXRot() - pitch, -90.0F, 90.0F));
            double direction = (payload.sequence() % 2L == 0L ? 1.0D : -1.0D);
            client.player.setYRot(client.player.getYRot() + (float) (direction * pitch * 0.08D));
        }
    }

    public static boolean blocksVanillaAttack() { return hasInitializedGun(Minecraft.getInstance()); }

    public static boolean sameGun(ItemStack previous, ItemStack current) {
        UUID serial = serial(previous);
        return serial != null && previous.getItem() == current.getItem() && serial.equals(serial(current));
    }

    public static boolean shouldTransform(ItemStack stack, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && active && heldSerial != null && heldSerial.equals(serial(stack));
    }

    public static void transform(PoseStack pose, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || MOTION.profile() == null) return;
        var frame = MOTION.frame(partial);
        var profile = MOTION.profile();
        double side = client.player.getMainArm() == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        double breath = Math.sin(frame.timeTicks() * 0.11D) * 0.002D * (1.0D - frame.aim() * 0.85D);
        double vibration = Math.sin(frame.timeTicks() * 2.9D) * frame.spin() * 0.0015D;
        pose.translate(side * (-0.43D * frame.aim() + vibration), profile.aimHeight() * frame.aim() + breath - frame.reloadPose() * 0.18D,
                profile.aimInset() * frame.aim() + frame.recoil() * 0.075D + frame.boltPose() * 0.035D);
        pose.rotateDegrees(Axis.XP, (float) (-frame.recoil() * 7.0D + frame.reloadPose() * 18.0D + frame.boltPose() * 3.5D + frame.heat() * frame.spin() * 2.5D));
        pose.rotateDegrees(Axis.ZP, (float) (side * (-frame.reloadPose() * 24.0D + frame.boltPose() * 4.0D)));
    }

    /** Return a transient render copy; neither the real held stack nor the server receives rotor floats. */
    public static ItemStack renderStack(ItemStack stack, ItemDisplayContext context, ItemOwner owner) {
        Minecraft client = Minecraft.getInstance();
        boolean heldContext = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        if (!heldContext || owner != client.player || !active || heldSerial == null || !heldSerial.equals(serial(stack))
                || MOTION.profile() == null || !MOTION.profile().rotary()) return stack;
        var frame = MOTION.frame(client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (frame.spin() < 0.025D) return stack;
        int phase = Math.floorMod((int) Math.floor(frame.rotorDegrees() / 22.5D), 16);
        CustomModelData previous = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        List<Float> floats = new ArrayList<>(previous == null ? List.of() : previous.floats());
        if (floats.isEmpty()) floats.add((float) (phase + 1)); else floats.set(0, (float) (phase + 1));
        ItemStack render = stack.copy();
        render.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.copyOf(floats),
                previous == null ? List.of() : previous.flags(), previous == null ? List.of() : previous.strings(),
                previous == null ? List.of() : previous.colors()));
        return render;
    }

    private static boolean scopedPresentation() {
        Minecraft client = Minecraft.getInstance();
        return active && MOTION.profile() != null && MOTION.allowed() && canControl(client) && client.options.getCameraType().isFirstPerson();
    }

    public static float worldFov(float original, float partial) {
        return scopedPresentation() ? (float) GunMotion.worldFov(original, MOTION.frame(partial).aim(), MOTION.profile().ironMagnification()) : original;
    }

    public static float modelFov(float original, float partial) {
        if (!scopedPresentation()) return original;
        return (float) (original + (MOTION.profile().modelAdsFov() - original) * MOTION.frame(partial).aim());
    }

    private static UUID serial(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TeamGunItem)) return null;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return null;
        try {
            UUID serial = UUID.fromString(custom.copyTag().getString("tlb_gun_serial").orElse(""));
            return GunInputPayload.CANCEL_SERIAL.equals(serial) ? null : serial;
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
