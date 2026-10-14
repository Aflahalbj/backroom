package com.backrooms.client.render;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/**
 * Port dari {@code com.sp.render.VertexFormats}.
 *
 * <ul>
 *   <li>{@link #BLOCKS}: format blok vanilla + satu nilai "Material" (ID blok)
 *       yang dibaca shader deferred untuk membedakan jenis permukaan.</li>
 *   <li>{@link #PBR}: format untuk layer PBR (zoom, resolusi tekstur, height
 *       map aktif, kedalaman parallax).</li>
 * </ul>
 */
public final class ModVertexFormats {

    private static final VertexFormatElement FLOAT =
            new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1);

    public static final VertexFormat BLOCKS = new VertexFormat(ImmutableMap.<String, VertexFormatElement>builder()
            .put("Position", DefaultVertexFormat.ELEMENT_POSITION)
            .put("Color", DefaultVertexFormat.ELEMENT_COLOR)
            .put("UV0", DefaultVertexFormat.ELEMENT_UV0)
            .put("UV2", DefaultVertexFormat.ELEMENT_UV2)
            .put("Normal", DefaultVertexFormat.ELEMENT_NORMAL)
            .put("Padding", DefaultVertexFormat.ELEMENT_PADDING)
            .put("Material", FLOAT)
            .build());

    public static final VertexFormat PBR = new VertexFormat(ImmutableMap.<String, VertexFormatElement>builder()
            .put("Position", DefaultVertexFormat.ELEMENT_POSITION)
            .put("Color", DefaultVertexFormat.ELEMENT_COLOR)
            .put("UV0", DefaultVertexFormat.ELEMENT_UV0)
            .put("UV2", DefaultVertexFormat.ELEMENT_UV2)
            .put("Normal", DefaultVertexFormat.ELEMENT_NORMAL)
            .put("Zoom", FLOAT)
            .put("Resolution", FLOAT)
            .put("EnableHeight", FLOAT)
            .put("DepthMultiplier", FLOAT)
            .build());

    private ModVertexFormats() {}
}
