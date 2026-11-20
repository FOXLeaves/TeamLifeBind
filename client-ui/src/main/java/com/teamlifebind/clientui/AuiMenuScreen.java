package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.screen.AuiLinkedScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;

/** Presents the already-open server ChestMenu; no local inventory transactions or commands. */
public final class AuiMenuScreen extends AbstractContainerScreen<ChestMenu> implements AuiLinkedScreen {
    private final String kind;
    private AuiMenuPage page;
    public AuiMenuScreen(ChestMenu menu, Inventory inventory, Component title, String kind) {
        super(menu, inventory, title); this.kind = kind;
    }
    @Override protected void init() {
        super.init();
        if (page == null) page = new AuiMenuPage(kind, title, this::activate, this::onClose);
        else page.resize();
        update();
    }
    private void update() {
        if (page == null) return;
        var entries = new ArrayList<AuiMenuPage.Entry>();
        for (int slot = 0; slot < menu.getRowCount() * 9; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (AuiMenuPage.visible(stack)) entries.add(new AuiMenuPage.Entry(slot, stack));
        }
        page.update(entries);
    }
    private void activate(AuiMenuPage.Entry entry, int button) {
        if (minecraft.gui.screen() != this || minecraft.player == null || minecraft.player.containerMenu != menu) return;
        if (entry.slot() < 0 || entry.slot() >= menu.getRowCount() * 9 || (button != 0 && button != 1)) return;
        var slot = menu.getSlot(entry.slot());
        // A server update between mouse-down and mouse-up invalidates the old button.
        if (!AuiMenuPage.visible(slot.getItem()) || !ItemStack.matches(slot.getItem(), entry.stack())) return;
        slotClicked(slot, entry.slot(), button, ContainerInput.PICKUP);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float tick) {}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float tick) { update(); page.render(graphics, x, y); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) { return page.mouse(event, true); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return page.mouse(event, false); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return true; }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { return page.scroll(x, y, dy); }
    @Override public boolean keyPressed(KeyEvent event) { return page.key(event); }
    @Override public boolean charTyped(CharacterEvent event) { return page.character(event.codepoint()); }
    @Override public Document getLinkedDocument() { return page == null ? null : page.document(); }
    @Override public void removed() { if (page != null) { page.close(); page = null; } super.removed(); }
}
