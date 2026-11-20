package com.teamlifebind.clientui;

import com.teamlifebind.aui.init.Document;
import com.teamlifebind.aui.parser.HTML;
import com.teamlifebind.aui.platform.ClientService;
import com.teamlifebind.aui.platform.ConfigService;
import com.teamlifebind.aui.platform.ItemRenderService;
import com.teamlifebind.aui.platform.RenderService;
import com.teamlifebind.aui.platform.ResourceService;
import com.teamlifebind.aui.registry.ApricityUIRegistry;
import com.teamlifebind.aui.spi.AuiServices;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.nio.charset.StandardCharsets;

/** Client-only bootstrap. The host retains its existing game network and permissions. */
public final class AuiClient {
    private static boolean initialized;
    private AuiClient() {}
    public static void initialize() {
        if (initialized) return;
        AuiServices.setClient(ClientService.INSTANCE);
        AuiServices.setConfig(ConfigService.INSTANCE);
        AuiServices.setResources(ResourceService.INSTANCE);
        AuiServices.setRender(RenderService.INSTANCE);
        AuiServices.setItems(ItemRenderService.INSTANCE);
        ApricityUIRegistry.register();
        initialized = true;
    }
    public static Document open(String path) {
        initialize();
        com.teamlifebind.aui.resource.async.style.StyleAsyncHandler.INSTANCE.invalidatePreparedStylesheets();
        // Resolve through the resource manager so resource packs can replace the HTML.
        var id = Identifier.fromNamespaceAndPath("tlb_aui", "apricity/" + path);
        try (var stream = Minecraft.getInstance().getResourceManager().open(id)) {
            HTML.putTemple(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot load TLB web interface " + id, failure);
        }
        Document result = Document.create(path);
        if (result == null || result.body == null) throw new IllegalStateException("AUI could not create " + path);
        return result;
    }
    public static void tick() {
        ArchiveChrome.tick();
        ArchivePresenter.tick();
        ArchiveHud.tick();
        ArchiveOverlays.tick();
        AuiMenuPage.tickActive();
        if (initialized) AuiInkEffect.releaseIdle();
        if (initialized) com.teamlifebind.aui.client.Client.tick();
        if (Boolean.getBoolean("teamlifebind.aui.selftest")) AuiSelfTest.tick();
        if (Boolean.getBoolean("teamlifebind.archive.selftest")) ArchiveSelfTest.tick();
        if (Boolean.getBoolean("teamlifebind.archive.e2e")) ArchiveE2E.tick();
        if (Boolean.getBoolean("teamlifebind.archive.layoutProbe")) ArchiveLayoutProbe.tick();
        if (Boolean.getBoolean("teamlifebind.sigil.selftest")) MagicSigilProbe.tick();
    }
    public static AuiInkTransition.Frame inkFrame(){return AuiScreenTransitions.active()?AuiScreenTransitions.frame():AuiMenuPage.hasActivePage()?AuiMenuPage.inkFrame():ArchivePresenter.inkFrame();}
}
