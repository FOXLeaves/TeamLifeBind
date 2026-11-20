package com.teamlifebind.clientui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.teamlifebind.gear.GunModule;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** Isolated loopback fixture. Read the same world frame before and after the production VFX pass. */
public final class GunWorkshopProbe {
    public static final boolean ENABLED = Boolean.getBoolean("teamlifebind.gun.workshop.test");
    private static final String SERIAL = "a9174232-752d-4dc7-94eb-ab04645035a6";
    private static CameraType wanted;
    private static int changed;
    private static Path output;
    private int step, ticks, view;
    private static final CameraType[] VIEWS = {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT};

    public static int[] before(RenderTarget main) {
        if (wanted == null || changed > 0 || Minecraft.getInstance().options.getCameraType() != wanted) return null;
        try { return AuiInkSelfTest.readbackFrame(main, output.resolve(wanted + "-base.png")); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static void after(RenderTarget main, int[] base) {
        if (base == null) return;
        try {
            int[] shot = AuiInkSelfTest.readbackFrame(main, output.resolve(wanted + "-shot.png"));
            int bright = 0;
            for (int i = 0; i < shot.length; i++) {
                int delta = (shot[i] >> 16 & 255) - (base[i] >> 16 & 255);
                if (delta >= 6) bright++;
            }
            if (bright > 6) changed = bright;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String item(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    boolean tick(ArchiveInteractionProbe host, Path out, List<String> evidence) throws Exception {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        output = out;
        if (++ticks > 240) throw new AssertionError("Gun workshop step " + step + " timed out in " + wanted);
        if (ticks < 10) return false;
        ItemStack gun = player.getOffhandItem();
        switch (step) {
            case 0 -> {
                mc.gui.setScreen(null);
                host.request("fit", "gamemode survival $PLAYER", "tp $PLAYER 0.5 106 0.5 0 0",
                    "fill -2 105 -2 2 105 2 minecraft:stone", "clear $PLAYER", 
                    "item replace entity $PLAYER weapon.offhand with teamlifebind:assault_rifle[minecraft:custom_data={tlb_gun_serial:'" + SERIAL + "',tlb_gun_ammo:20}]",
                    "item replace entity $PLAYER weapon.mainhand with teamlifebind:wide_lens 2");
            }
            case 1 -> {
                if (!item(player.getMainHandItem()).equals("wide_lens")) return false;
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
            case 2 -> {
                if (GunModule.installed(gun, GunModule.Socket.OPTIC) != GunModule.WIDE_LENS) return false;
                check(player.getMainHandItem().getCount() == 1, "Fit consumed wrong count");
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
            case 3 -> {
                check(player.getMainHandItem().getCount() == 1, "Duplicate fit consumed part");
                host.request("swap-part", "item replace entity $PLAYER weapon.mainhand with teamlifebind:star_lens");
            }
            case 4 -> {
                if (!item(player.getMainHandItem()).equals("star_lens")) return false;
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
            case 5 -> {
                if (GunModule.installed(gun, GunModule.Socket.OPTIC) != GunModule.STAR_LENS) return false;
                check(has("wide_lens"), "Swap did not return previous lens");
                host.request("spanner", "item replace entity $PLAYER weapon.mainhand with teamlifebind:rune_spanner");
            }
            case 6 -> {
                if (!item(player.getMainHandItem()).equals("rune_spanner")) return false;
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
            case 7 -> {
                if (GunModule.installed(gun, GunModule.Socket.OPTIC) != null) return false;
                check(has("star_lens") && has("rune_spanner"), "Removal lost module or tool");
                evidence.add("PASS GUN_WORKSHOP synced fit, duplicate protection, replacement, removal and returns");
                host.request("shoot", "item replace entity $PLAYER weapon.mainhand from entity $PLAYER weapon.offhand",
                    "item replace entity $PLAYER weapon.offhand with minecraft:air");
            }
            case 8 -> {
                if (!item(player.getMainHandItem()).equals("assault_rifle")) return false;
                mc.options.setCameraType(VIEWS[view]);
                player.setXRot(0); player.setYRot(0);
                mc.mouseHandler.grabMouse();
                wanted = VIEWS[view]; changed = 0;
            }
            case 9 -> {
                mc.options.keyAttack.setDown(true);
                if (changed == 0) return false;
                mc.options.keyAttack.setDown(false);
                evidence.add("PASS GUN_VIEW " + wanted + " pixels=" + changed);
                wanted = null;
                if (++view < VIEWS.length) { step = 8; ticks = 0; return false; }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                return true;
            }
        }
        step++; ticks = 0;
        return false;
    }

    private boolean has(String id) {
        var inventory = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
            if (item(inventory.getItem(i)).equals(id)) return true;
        return false;
    }
}
