package com.teamlifebind.fabric;

import com.teamlifebind.common.NativeHud;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;

/** Client presentation of the existing TLB objective; scores and packets stay server-owned. */
public final class NativeSidebarHud {
    private static final int DEFAULT_COLOR = 0xFFE1EAF2;
    private static final int MAX_LINES = 15;
    private static final Comparator<PlayerScoreEntry> ORDER = Comparator
        .comparingInt(PlayerScoreEntry::value).reversed()
        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final FormattedText.StyledContentConsumer<Integer> FIRST_COLOR = (style, text) ->
        text.isEmpty() ? Optional.empty() : Optional.of(style.getColor() == null
            ? DEFAULT_COLOR : 0xFF000000 | style.getColor().getValue());
    private static final ArrayList<PlayerScoreEntry> sorted = new ArrayList<>(MAX_LINES);
    private static final ArrayList<PlayerScoreEntry> previous = new ArrayList<>(MAX_LINES);
    private static final ArrayList<NativeHud.Line> lines = new ArrayList<>(MAX_LINES);
    private static Objective objective;
    private static Language language;
    private static Component titleComponent;
    private static String title = "";
    private static int sampledTick = Integer.MIN_VALUE;
    private static Objective deferredExpanded;

    private NativeSidebarHud() {}

    /** Return true only for the objective whose vanilla rendering this replaces. */
    public static boolean render(GuiGraphicsExtractor graphics, Objective value) {
        return render(graphics, value, false);
    }

    /** Vanilla draws the player list after its sidebar; put an expanded list above that overlay. */
    public static void renderExpanded(GuiGraphicsExtractor graphics) {
        Objective value = deferredExpanded;
        deferredExpanded = null;
        if (value != null) render(graphics, value, true);
    }

    private static boolean render(GuiGraphicsExtractor graphics, Objective value, boolean late) {
        if (!"tlb_sidebar".equals(value.getName())) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.hud.isHidden()
                || client.gui.screen() != null || client.gui.overlay() != null) return true;
        if (!late && client.options.keyPlayerList.isDown()) {
            deferredExpanded = value;
            return true;
        }

        Language currentLanguage = Language.getInstance();
        int tick = client.gui.hud.getGuiTicks();
        if (objective != value || language != currentLanguage || sampledTick != tick) {
            refresh(value, currentLanguage);
            sampledTick = tick;
        }
        if (late) graphics.nextStratum();
        NativeHud.sidebar(new NativeHudCanvas(graphics, client.font), graphics.guiWidth(),
            graphics.guiHeight(), title, lines, late || client.options.keyPlayerList.isDown(),
            NativeHudRenderer.text("sidebar_more", client.options.keyPlayerList.getTranslatedKeyMessage()));
        return true;
    }

    /** Reuse unchanged translations between frames; refresh scores at the client tick rate. */
    private static void refresh(Objective value, Language currentLanguage) {
        boolean invalidate = objective != value || language != currentLanguage;
        objective = value;
        language = currentLanguage;
        Component nextTitle = value.getDisplayName();
        if (invalidate || !Objects.equals(titleComponent, nextTitle)) {
            titleComponent = nextTitle;
            title = nextTitle.getString();
        }
        sorted.clear();
        for (PlayerScoreEntry entry : value.getScoreboard().listPlayerScores(value)) {
            if (!entry.isHidden()) sorted.add(entry);
        }
        sorted.sort(ORDER);
        int count = Math.min(MAX_LINES, sorted.size());
        boolean changed = invalidate || previous.size() != count;
        if (!changed) {
            for (int index = 0; index < count; index++) {
                if (!sorted.get(index).equals(previous.get(index))) { changed = true; break; }
            }
        }
        if (!changed) return;
        previous.clear();
        lines.clear();
        for (int index = 0; index < count; index++) {
            PlayerScoreEntry entry = sorted.get(index);
            previous.add(entry);
            Component label = entry.display() == null ? entry.ownerName() : entry.display();
            int color = label.visit(FIRST_COLOR, Style.EMPTY).orElse(DEFAULT_COLOR);
            lines.add(new NativeHud.Line(label.getString(), color));
        }
    }
}
