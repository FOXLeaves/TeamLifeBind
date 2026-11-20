package com.teamlifebind.clientui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;

/** Reproduce native world-list completion at the production semantic-projection boundary. */
public final class ArchiveMenuModelProbe {
    static final class QuietTabs extends net.minecraft.client.gui.components.tabs.TabManager {
        QuietTabs(){super(w->{},w->{});}
        @Override public void setCurrentTab(net.minecraft.client.gui.components.tabs.Tab tab,boolean sound){super.setCurrentTab(tab,false);}
    }
    static final class FixtureTabButton extends TabButton {
        FixtureTabButton(QuietTabs manager,net.minecraft.client.gui.components.tabs.Tab tab){super(manager,tab,160,24);}
        protected void extractWidgetRenderState(GuiGraphicsExtractor g,int x,int y,float delta){}
    }
    static final class TabsFixture extends Screen {
        final QuietTabs manager=new QuietTabs();
        final net.minecraft.client.gui.components.tabs.TabNavigationBar bar;
        final java.util.List<FixtureTabButton> buttons;
        TabsFixture(){
            super(Component.literal("Create world"));
            var game=new net.minecraft.client.gui.components.tabs.GridLayoutTab(Component.literal("Game"));
            var world=new net.minecraft.client.gui.components.tabs.GridLayoutTab(Component.literal("World"));
            var more=new net.minecraft.client.gui.components.tabs.GridLayoutTab(Component.literal("More"));
            buttons=java.util.List.of(new FixtureTabButton(manager,game),new FixtureTabButton(manager,world),new FixtureTabButton(manager,more));
            bar=net.minecraft.client.gui.components.tabs.TabNavigationBar.builder(manager,0,0,480,24)
                .addTab(buttons.get(0),game).addTab(buttons.get(1),world).addTab(buttons.get(2),more).build();
            bar.arrangeElements(480);manager.setCurrentTab(game,false);addWidget(bar);
        }
    }
    static final class FixtureEntry extends ObjectSelectionList.Entry<FixtureEntry> implements SelectableEntry,ArchiveRefreshable {
        int confirmations,mouseClicks,updates;
        public void tlb$refreshArchiveData(){updates++;}
        public Component getNarration(){return Component.literal("A saved world");}
        public void extractContent(GuiGraphicsExtractor g,int x,int y,boolean hover,float dt){}
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean twice){mouseClicks++;return true;}
        public boolean keyPressed(net.minecraft.client.input.KeyEvent event){if(event.isConfirmation()){confirmations++;return true;}return false;}
    }
    static final class PendingList extends ObjectSelectionList<FixtureEntry> implements ArchiveRefreshable {
        final CompletableFuture<FixtureEntry> pending=CompletableFuture.completedFuture(new FixtureEntry());
        boolean published;
        PendingList(){super(Minecraft.getInstance(),400,300,0,40);}
        public void tlb$refreshArchiveData(){if(!published&&pending.isDone()){addEntry(pending.join());published=true;}}
    }
    static final class Fixture extends Screen {
        Fixture(PendingList list){super(Component.literal("Select world"));addWidget(list);addWidget(Button.builder(Component.translatable("gui.friends.open"),b->{}).bounds(0,0,120,20).build());}
    }
    static final class SettingsFixture extends net.minecraft.client.gui.screens.options.OptionsSubScreen {
        final Button distance=Button.builder(Component.literal("Render distance: 16 chunks"),b->{}).bounds(0,0,150,20).build();
        final Button mipmap=Button.builder(Component.literal("Mipmap levels: 4"),b->{}).bounds(0,0,150,20).build();
        SettingsFixture(){super(null,null,Component.literal("Video settings"));width=1024;height=768;list=new OptionsList(Minecraft.getInstance(),width,this);list.addSmall(distance,mipmap);addWidget(list);}
        protected void addOptions(){}
    }
    static final class RecipeFixture extends Screen implements net.minecraft.client.gui.screens.inventory.MenuAccess<net.minecraft.world.inventory.InventoryMenu> {
        final net.minecraft.world.inventory.InventoryMenu menu;
        RecipeFixture(sun.misc.Unsafe unsafe)throws Exception{
            super(Component.literal("Crafting"));menu=(net.minecraft.world.inventory.InventoryMenu)unsafe.allocateInstance(net.minecraft.world.inventory.InventoryMenu.class);
            addWidget(new ImageButton(0,0,20,18,net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.RECIPE_BUTTON_SPRITES,b->{}));
            addWidget(new net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton(0,0,
                new net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.TabInfo(net.minecraft.world.item.ItemStack.EMPTY,java.util.Optional.empty(),net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory.CRAFTING),b->{}));
        }
        public net.minecraft.world.inventory.InventoryMenu getMenu(){return menu;}
    }
    public static void main(String[] args) throws Exception {
        var singleton=Minecraft.class.getDeclaredField("instance");singleton.setAccessible(true);
        var handle=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");handle.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)handle.get(null);
        singleton.set(null,unsafe.allocateInstance(Minecraft.class));
        Minecraft.getInstance().setLastInputType(net.minecraft.client.InputType.MOUSE);
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var recipes=ArchiveModel.read(new RecipeFixture(unsafe));
        long toggles=recipes.controls.stream().filter(c->c.label().equals(ArchiveTheme.tr("work.recipes"))).count();
        if(toggles!=1||recipes.controls.stream().noneMatch(c->c.kind().equals("recipe-category")))
            throw new AssertionError("Recipe categories are projected as duplicate recipe-book buttons: "+recipes.controls.stream().map(c->c.kind()+":"+c.label()).toList());
        System.out.println("ARCHIVE_RECIPE_ROLES_PASS one book toggle; categories retain their own identity");
        PendingList list=new PendingList();
        var model=ArchiveModel.read(new Fixture(list));
        if(!list.published||model.controls.stream().noneMatch(c->c.source()==list.children().getFirst()))
            throw new AssertionError("Completed world-list data stays unpublished when native rendering is replaced");
        System.out.println("ARCHIVE_WORLD_LIST_LIFECYCLE_PASS completed async entries appear without native rendering");
        var control=model.controls.stream().filter(c->c.source()==list.children().getFirst()).findFirst().orElseThrow();
        var entry=list.children().getFirst();ArchiveModel.activateEntry(control,false);
        if(entry.updates!=1)throw new AssertionError("Server-entry status is never refreshed when native entry rendering is replaced");
        if(list.getSelected()!=entry||entry.mouseClicks!=0||entry.confirmations!=0)throw new AssertionError("Single click must select without hitting native join/reorder icon quadrants");
        ArchiveModel.activateEntry(control,true);
        if(entry.confirmations!=1||entry.mouseClicks!=0)throw new AssertionError("Confirmation must reach the native entry once");
        System.out.println("ARCHIVE_ENTRY_ACTION_PASS single-click selects; confirmation uses original native callback once");
        if(model.controls.stream().anyMatch(c->c.source() instanceof AbstractWidget w&&ArchivePolicy.friendText(w.getMessage())))throw new AssertionError("Friends entry remains visible");
        if(!ArchivePolicy.friendText(Component.translatable("options.generic_value",Component.translatable("options.friendsList"),"ON")))throw new AssertionError("Friends option nested in a value label remains visible");
        var friendScreen=(Screen)unsafe.allocateInstance(Class.forName("net.minecraft.client.gui.screens.friends.FriendsOverlayScreen"));
        if(!ArchivePolicy.blocked(friendScreen))throw new AssertionError("Friends screen remains reachable");
        System.out.println("ARCHIVE_FRIENDS_POLICY_PASS entry, nested preference and screen removed");
        var slider=Button.builder(Component.translatable("options.generic_value",Component.translatable("options.fov"),90),b->{}).bounds(0,0,120,20).build();
        if(!ArchiveNavigation.group(new ArchiveModel.Control(slider,null,"slider","FOV","",true)).equals("display"))throw new AssertionError("Settings categories depend on a translated label");
        System.out.println("ARCHIVE_SETTINGS_GROUP_PASS groups use native translation identities");
        var settings=new SettingsFixture();var options=ArchiveModel.read(settings);
        if(options.controls.stream().noneMatch(c->c.source()==settings.distance)||options.controls.stream().noneMatch(c->c.source()==settings.mipmap))
            throw new AssertionError("Real OptionsList entries hide their named widgets behind generic placeholder rows: "+options.controls.stream().map(c->c.kind()+":"+c.label()).toList());
        System.out.println("ARCHIVE_NATIVE_OPTIONS_PASS real OptionsList exposes render-distance and mipmap controls");
        var tabs=new TabsFixture();var projected=ArchiveModel.read(tabs);
        for(int index:new int[]{1,2,0}){
            var button=tabs.buttons.get(index);var parent=projected.relays.get(button);
            if(parent!=tabs.bar)throw new AssertionError("Projected tab loses its native navigation owner; World/More clicks cannot select their page: "+index);
            var click=new net.minecraft.client.input.MouseButtonEvent(button.getX()+button.getWidth()/2,button.getY()+button.getHeight()/2,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
            parent.mouseClicked(click,false);parent.mouseReleased(click);
            if(!button.isSelected())throw new AssertionError("Native tab did not switch through its projected event relay: "+index);
        }
        System.out.println("ARCHIVE_TAB_RELAY_PASS real native tab navigation switches World, More and Game");
        var category=net.minecraft.client.KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("teamlifebind","compat_keys"));
        var r=com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_R);
        var p=com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_P);
        for(boolean irisFirst:new boolean[]{false,true}){
            net.minecraft.client.KeyMapping reload,iris;
            if(irisFirst){iris=new net.minecraft.client.KeyMapping("iris.keybind.reload",r.getValue(),category);reload=new net.minecraft.client.KeyMapping("key.teamlifebind.reload",r.getValue(),category);}
            else{reload=new net.minecraft.client.KeyMapping("key.teamlifebind.reload",r.getValue(),category);iris=new net.minecraft.client.KeyMapping("iris.keybind.reload",r.getValue(),category);}
            net.minecraft.client.KeyMapping.resetMapping();net.minecraft.client.KeyMapping.click(r);
            if(!reload.consumeClick()||!iris.consumeClick())throw new AssertionError("Iris registration order stole the weapon reload key");
            reload.setKey(p);net.minecraft.client.KeyMapping.resetMapping();net.minecraft.client.KeyMapping.click(r);
            if(reload.consumeClick()||!iris.consumeClick())throw new AssertionError("Remapping weapon reload changed the Iris binding");
            net.minecraft.client.KeyMapping.click(p);
            if(!reload.consumeClick()||iris.consumeClick())throw new AssertionError("Custom weapon reload key is not independent of Iris");
        }
        System.out.println("ARCHIVE_RENDER_KEY_COMPAT_PASS R survives both registration orders; remapping keeps Iris and weapon keys independent");
    }
}
