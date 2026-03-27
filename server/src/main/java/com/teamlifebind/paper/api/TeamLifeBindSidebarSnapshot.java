package com.teamlifebind.paper.api;

import java.util.List;
import java.util.Objects;

public record TeamLifeBindSidebarSnapshot(String title, List<String> lines) {

    public TeamLifeBindSidebarSnapshot {
        title = Objects.requireNonNullElse(title, "");
        lines = List.copyOf(lines);
    }
}
