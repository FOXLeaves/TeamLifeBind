package com.teamlifebind.clientui;

import java.util.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;

/** Semantic workspace roles; all slots and progress values still belong to the native menu. */
final class ArchiveWorkstation {
    record Zone(String key, int columns, List<Integer> slots) {}
    record Meter(String key, float fraction, String value) {}
    record Profile(String key, List<Zone> zones) {}
    private ArchiveWorkstation() {}
    private static Zone zone(String key, int columns, int... slots) {
        return new Zone(key, columns, Arrays.stream(slots).boxed().toList());
    }
    static Profile profile(AbstractContainerMenu menu) {
        if(menu instanceof InventoryMenu) return new Profile("inventory",List.of(zone("craft2",2,1,2,3,4),zone("result",1,0)));
        if(menu instanceof CraftingMenu) return new Profile("crafting",List.of(zone("craft3",3,1,2,3,4,5,6,7,8,9),zone("result",1,0)));
        if(menu instanceof AbstractFurnaceMenu) return new Profile("furnace",List.of(zone("ingredient",1,0),zone("fuel",1,1),zone("result",1,2)));
        if(menu instanceof BrewingStandMenu) return new Profile("brewing",List.of(zone("bottles",3,0,1,2),zone("reagent",1,3),zone("blaze",1,4)));
        if(menu instanceof SmithingMenu) return new Profile("smithing",List.of(zone("template",1,0),zone("equipment",1,1),zone("addition",1,2),zone("result",1,3)));
        if(menu instanceof AnvilMenu) return new Profile("anvil",List.of(zone("equipment",1,0),zone("repair",1,1),zone("result",1,2)));
        if(menu instanceof GrindstoneMenu) return new Profile("grindstone",List.of(zone("equipment",1,0),zone("optional",1,1),zone("result",1,2)));
        if(menu instanceof CartographyTableMenu) return new Profile("cartography",List.of(zone("map",1,0),zone("map_material",1,1),zone("result",1,2)));
        if(menu instanceof StonecutterMenu) return new Profile("stonecutter",List.of(zone("stone",1,0),zone("result",1,1)));
        if(menu instanceof LoomMenu) return new Profile("loom",List.of(zone("banner",1,0),zone("dye",1,1),zone("pattern",1,2),zone("result",1,3)));
        if(menu instanceof EnchantmentMenu) return new Profile("enchanting",List.of(zone("equipment",1,0),zone("lapis",1,1)));
        if(menu instanceof MerchantMenu) return new Profile("trading",List.of(zone("payment",2,0,1),zone("result",1,2)));
        if(menu instanceof BeaconMenu) return new Profile("beacon",List.of(zone("offering",1,0)));
        if(menu instanceof CrafterMenu) return new Profile("crafter",List.of(zone("crafter",3,0,1,2,3,4,5,6,7,8),zone("preview",1,45)));
        if(menu instanceof LecternMenu) return new Profile("lectern",List.of(zone("book",1,0)));
        if(menu instanceof AbstractMountInventoryMenu){
            var cargo=menu.slots.stream().filter(s->!(s.container instanceof Inventory)&&s.index>=2).map(s->s.index).toList();
            var zones=new ArrayList<>(List.of(zone("saddle",1,0),zone("mount_armor",1,1)));
            if(!cargo.isEmpty())zones.add(new Zone("storage",5,cargo));
            return new Profile("mount",List.copyOf(zones));
        }
        var work = menu.slots.stream().filter(s->!(s.container instanceof Inventory)).map(s->s.index).toList();
        int columns=menu instanceof DispenserMenu?3:menu instanceof HopperMenu?5:6;
        return new Profile("storage",List.of(new Zone("storage",columns,work)));
    }
    static List<Meter> meters(AbstractContainerMenu menu) {
        if(menu instanceof AbstractFurnaceMenu furnace) return List.of(
            new Meter("smelting",furnace.getBurnProgress(),Math.round(furnace.getBurnProgress()*100)+"%"),
            new Meter("fuel",furnace.getLitProgress(),Math.round(furnace.getLitProgress()*100)+"%"));
        if(menu instanceof BrewingStandMenu brewing) {
            int ticks=Math.max(0,brewing.getBrewingTicks());
            return List.of(new Meter("brewing",ticks>0?1-ticks/(float)Math.max(1,brewing.getTotalBrewingTicks()):0,ticks>0?(int)Math.ceil(ticks/20.0)+"s":"—"),
                new Meter("blaze",brewing.getFuel()/(float)Math.max(1,brewing.getTotalFuel()),brewing.getFuel()+" / "+brewing.getTotalFuel()));
        }
        return List.of();
    }
    static String status(AbstractContainerMenu menu) {
        if(menu instanceof AbstractFurnaceMenu furnace) return ArchiveTheme.tr("work.state."+(!menu.getSlot(2).getItem().isEmpty()?"output":furnace.isLit()?"working":menu.getSlot(0).getItem().isEmpty()?"ingredient":"fuel"));
        if(menu instanceof BrewingStandMenu brewing) return ArchiveTheme.tr("work.state."+(brewing.getBrewingTicks()>0?"working":"brewing"));
        if(menu instanceof AnvilMenu anvil) return ArchiveTheme.tr("control.repair",anvil.getCost());
        if(menu instanceof BeaconMenu beacon) return ArchiveTheme.tr("control.beacon",beacon.getLevels());
        var profile=profile(menu);
        var output=profile.zones().stream().filter(z->z.key().equals("result")).findFirst();
        if(output.isPresent())return ArchiveTheme.tr("work.state."+(menu.getSlot(output.get().slots().getFirst()).hasItem()?"output":"recipe"));
        return profile.key().equals("storage")?ArchiveTheme.tr("work.hint.storage"):"";
    }
}
