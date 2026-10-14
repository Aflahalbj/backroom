package com.backrooms.client.render;

import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Port dari {@code com.sp.render.PreviousUniforms}: matriks frame sebelumnya untuk motion blur. */
public final class PreviousUniforms {

    public static Matrix4f prevModelViewMat;
    public static Matrix4f prevProjMat;
    public static Vector3f prevCameraPos;

    private PreviousUniforms() {}

    public static void update() {
        CameraMatrices matrices = VeilRenderSystem.renderer().getCameraMatrices();
        prevProjMat = new Matrix4f(matrices.getProjectionMatrix());
        prevModelViewMat = new Matrix4f(matrices.getViewMatrix());
        prevCameraPos = new Vector3f(matrices.getCameraPosition());
    }
}
