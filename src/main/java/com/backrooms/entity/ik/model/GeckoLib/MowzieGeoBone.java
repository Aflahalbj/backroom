package com.backrooms.entity.ik.model.GeckoLib;

import com.backrooms.entity.ik.model.BoneAccessor;
import com.backrooms.entity.ik.util.MathUtil;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

/** Port dari {@code com.sp.entity.ik.model.GeckoLib.MowzieGeoBone}: bone GeckoLib yang bisa digerakkan IK. */
public class MowzieGeoBone extends GeoBone implements BoneAccessor {
    public Matrix4f rotationOverride;
    public boolean inheritRotation = true;
    public boolean inheritTranslation = true;
    protected boolean forceMatrixTransform = false;
    private boolean isDynamicJoint = false;

    public MowzieGeoBone(@Nullable GeoBone parent, String name, Boolean mirror, @Nullable Double inflate, @Nullable Boolean dontRender, @Nullable Boolean reset) {
        super(parent, name, mirror, inflate, dontRender, reset);
        this.rotationOverride = null;
    }

    public MowzieGeoBone(MowzieGeoBone geoBone) {
        super(null, geoBone.getName() + "_chain", geoBone.getMirror(), geoBone.getInflate(), geoBone.shouldNeverRender(), geoBone.getReset());
        this.setPos(geoBone.getPos());
        this.setRot(geoBone.getRot());
        this.setPivotX(geoBone.getPivotX());
        this.setPivotY(geoBone.getPivotY());
        this.setPivotZ(geoBone.getPivotZ());
        this.setScale(geoBone.getScale());
        this.getCubes().addAll(geoBone.getCubes());
        this.saveInitialSnapshot();
        this.getChildBones().addAll(geoBone.getChildBones());
    }

    public static void removeMatrixTranslation(Matrix4f matrix) {
        matrix.m30(0.0F);
        matrix.m31(0.0F);
        matrix.m32(0.0F);
    }

    @Override
    public MowzieGeoBone getParent() {
        return (MowzieGeoBone) super.getParent();
    }

    public void addPos(Vec3 vec) {
        this.addPos((float) vec.x, (float) vec.y, (float) vec.z);
    }

    public void addPos(float x, float y, float z) {
        this.addPosX(x);
        this.addPosY(y);
        this.addPosZ(z);
    }

    public void addPosX(float x) {
        this.setPosX(this.getPosX() + x);
    }

    public void addPosY(float y) {
        this.setPosY(this.getPosY() + y);
    }

    public void addPosZ(float z) {
        this.setPosZ(this.getPosZ() + z);
    }

    public void setPos(float x, float y, float z) {
        this.setPosX(x);
        this.setPosY(y);
        this.setPosZ(z);
    }

    public Vec3 getPos() {
        return new Vec3(this.getPosX(), this.getPosY(), this.getPosZ());
    }

    public void setPos(Vec3 vec) {
        this.setPos((float) vec.x, (float) vec.y, (float) vec.z);
    }

    public void addRot(Vec3 vec) {
        this.addRot((float) vec.x, (float) vec.y, (float) vec.z);
    }

    public void addRot(float x, float y, float z) {
        this.addRotX(x);
        this.addRotY(y);
        this.addRotZ(z);
    }

    public void addRotX(float x) {
        this.setRotX(this.getRotX() + x);
    }

    public void addRotY(float y) {
        this.setRotY(this.getRotY() + y);
    }

    public void addRotZ(float z) {
        this.setRotZ(this.getRotZ() + z);
    }

    public void setRot(float x, float y, float z) {
        this.setRotX(x);
        this.setRotY(y);
        this.setRotZ(z);
    }

    public Vector3d getRot() {
        return new Vector3d(this.getRotX(), this.getRotY(), this.getRotZ());
    }

    public void setRot(Vector3d vec) {
        this.setRot((float) vec.x(), (float) vec.y(), (float) vec.z());
    }

    public void setRot(Vec3 vec) {
        this.setRot((float) vec.x, (float) vec.y, (float) vec.z);
    }

    public void multiplyScale(Vec3 vec) {
        this.multiplyScale((float) vec.x, (float) vec.y, (float) vec.z);
    }

    public void multiplyScale(float x, float y, float z) {
        this.setScaleX(this.getScaleX() * x);
        this.setScaleY(this.getScaleY() * y);
        this.setScaleZ(this.getScaleZ() * z);
    }

    public void setScale(float x, float y, float z) {
        this.setScaleX(x);
        this.setScaleY(y);
        this.setScaleZ(z);
    }

    public Vector3d getScale() {
        return new Vector3d(this.getScaleX(), this.getScaleY(), this.getScaleZ());
    }

    public void setScale(Vec3 vec) {
        this.setScale((float) vec.x, (float) vec.y, (float) vec.z);
    }

    public void setScale(Vector3d vec) {
        this.setScale((float) vec.x(), (float) vec.y(), (float) vec.z());
    }

    public void setScale(float scale) {
        this.setScale(scale, scale, scale);
    }

    public void addRotationOffsetFromBone(MowzieGeoBone source) {
        this.setRotX(this.getRotX() + source.getRotX() - source.getInitialSnapshot().getRotX());
        this.setRotY(this.getRotY() + source.getRotY() - source.getInitialSnapshot().getRotY());
        this.setRotZ(this.getRotZ() + source.getRotZ() - source.getInitialSnapshot().getRotZ());
    }

    public boolean isForceMatrixTransform() {
        return this.forceMatrixTransform;
    }

    public void setForceMatrixTransform(boolean forceMatrixTransform) {
        this.forceMatrixTransform = forceMatrixTransform;
    }

    public Matrix4f getModelRotationMat() {
        Matrix4f matrix = new Matrix4f(this.getModelSpaceMatrix());
        removeMatrixTranslation(matrix);
        return matrix;
    }

    public void setModelXformOverride(Matrix4f mat) {
        this.rotationOverride = mat;
    }

    public boolean isDynamicJoint() {
        return this.isDynamicJoint;
    }

    public void setDynamicJoint(boolean dynamicJoint) {
        this.isDynamicJoint = dynamicJoint;
    }

    @Override
    public Vec3 getPosition() {
        return MathUtil.toVec3(this.getWorldPosition());
    }

    @Override
    public void moveTo(Vec3 to, @Nullable Vec3 facing, Entity entity) {
        this.setForceMatrixTransform(true);
        Matrix4f xformOverride = new Matrix4f();
        Vec3 newModelPosWorldSpace;
        if (entity instanceof LivingEntity) {
            newModelPosWorldSpace = MathUtil.rotatePointOnAPlaneAround(to, entity.position(), -180.0F + entity.getVisualRotationYInDegrees(), new Vec3(0.0D, 1.0D, 0.0D));
        } else {
            newModelPosWorldSpace = MathUtil.rotatePointOnAPlaneAround(to, entity.position(), -180.0D, new Vec3(0.0D, 1.0D, 0.0D));
        }

        xformOverride = xformOverride.translate(newModelPosWorldSpace.toVector3f());
        if (facing != null) {
            Vec3 newTargetVecWorldSpace;
            if (entity instanceof LivingEntity) {
                newTargetVecWorldSpace = MathUtil.rotatePointOnAPlaneAround(facing, entity.position(), -180.0F + entity.getVisualRotationYInDegrees(), new Vec3(0.0D, 1.0D, 0.0D));
            } else {
                newTargetVecWorldSpace = MathUtil.rotatePointOnAPlaneAround(facing, entity.position(), -180.0D, new Vec3(0.0D, 1.0D, 0.0D));
            }

            Vector3f desiredDir = newTargetVecWorldSpace.toVector3f().sub(newModelPosWorldSpace.toVector3f(), new Vector3f()).normalize();
            Vector3f startingDir = new Vector3f(0.0F, 0.0F, 1.0F);
            double dot = desiredDir.dot(startingDir);
            Quaternionf q;
            if (dot > 0.9999999D) {
                q = new Quaternionf();
            } else {
                q = new Quaternionf().rotateTo(startingDir, desiredDir).normalize();
            }
            xformOverride.rotate(q);
        }

        this.setWorldSpaceMatrix(xformOverride);
    }

    @Override
    public List<BoneAccessor> getChildren() {
        return this.getChildBones().stream().map(bone -> (BoneAccessor) bone).toList();
    }
}
