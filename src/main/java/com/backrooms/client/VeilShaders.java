package com.backrooms.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/** Ambil program shader Veil (null kalau renderer Veil belum siap atau shader gagal dimuat). */
public final class VeilShaders {

    private VeilShaders() {}

    public static ShaderProgram program(ResourceLocation id) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        return renderer == null ? null : renderer.getShaderManager().getShader(id);
    }

    public static ShaderInstance instance(ResourceLocation id) {
        ShaderProgram program = program(id);
        return program == null ? null : program.toShaderInstance();
    }
}
