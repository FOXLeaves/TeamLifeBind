package com.sighs.apricityui.render;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.Optional;

/** 26.3 vanilla equivalent of NeoForge's toBuilder, preserving all material state. */
public final class PortablePipeline {
    private PortablePipeline() {}
    public static RenderPipeline.Builder copy(RenderPipeline source) {
        ColorTargetState[] colors = source.getColorTargetStates().toArray(ColorTargetState[]::new);
        VertexFormat[] formats = source.getVertexFormatBindings().toArray(VertexFormat[]::new);
        var snippet = new RenderPipeline.Snippet(source.getShaders(), Optional.of(source.getShaderDefines()),
                Optional.of(source.getBindGroupLayouts()), colors, source.getColorTargetStates().size(),
                Optional.ofNullable(source.getDepthStencilState()), Optional.of(source.getPolygonMode()),
                Optional.of(source.isCull()), formats, Optional.of(source.getPrimitiveTopology()), source.pushConstantSize());
        return RenderPipeline.builder(snippet).withLocation(source.getLocation());
    }
}
