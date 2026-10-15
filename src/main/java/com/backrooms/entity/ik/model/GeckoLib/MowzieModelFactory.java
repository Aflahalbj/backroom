package com.backrooms.entity.ik.model.GeckoLib;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.loading.json.raw.Bone;
import software.bernie.geckolib.loading.json.raw.Cube;
import software.bernie.geckolib.loading.json.raw.ModelProperties;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.BoneStructure;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Port dari {@code com.sp.entity.ik.model.GeckoLib.MowzieModelFactory}: semua
 * model GeckoLib namespace {@code spb-revamped} dibangun dari {@link MowzieGeoBone}.
 */
public class MowzieModelFactory implements BakedModelFactory {
    @Override
    public BakedGeoModel constructGeoModel(GeometryTree geometryTree) {
        List<GeoBone> bones = new ObjectArrayList<>();
        for (BoneStructure boneStructure : geometryTree.topLevelBones().values()) {
            bones.add(this.constructBone(boneStructure, geometryTree.properties(), null));
        }
        return new BakedGeoModel(bones, geometryTree.properties());
    }

    @Override
    public GeoBone constructBone(BoneStructure boneStructure, ModelProperties properties, @Nullable GeoBone parent) {
        Bone bone = boneStructure.self();
        MowzieGeoBone newBone = new MowzieGeoBone(parent, bone.name(), bone.mirror(), bone.inflate(), bone.neverRender(), bone.reset());
        Vec3 rotation = RenderUtils.arrayToVec(bone.rotation());
        Vec3 pivot = RenderUtils.arrayToVec(bone.pivot());
        newBone.updateRotation((float) Math.toRadians(-rotation.x), (float) Math.toRadians(-rotation.y), (float) Math.toRadians(rotation.z));
        newBone.updatePivot((float) (-pivot.x), (float) pivot.y, (float) pivot.z);

        for (Cube cube : bone.cubes()) {
            newBone.getCubes().add(this.constructCube(cube, properties, newBone));
        }
        for (BoneStructure child : boneStructure.children().values()) {
            newBone.getChildBones().add(this.constructBone(child, properties, newBone));
        }
        return newBone;
    }

    @Override
    public GeoCube constructCube(Cube cube, ModelProperties properties, GeoBone bone) {
        boolean mirror = cube.mirror() == Boolean.TRUE;
        double inflate = cube.inflate() != null ? cube.inflate() / 16.0D : (bone.getInflate() == null ? 0.0D : bone.getInflate() / 16.0D);
        Vec3 size = RenderUtils.arrayToVec(cube.size());
        Vec3 origin = RenderUtils.arrayToVec(cube.origin());
        Vec3 rotation = RenderUtils.arrayToVec(cube.rotation());
        Vec3 pivot = RenderUtils.arrayToVec(cube.pivot());
        origin = new Vec3(-(origin.x + size.x) / 16.0D, origin.y / 16.0D, origin.z / 16.0D);
        Vec3 vertexSize = size.multiply(0.0625D, 0.0625D, 0.0625D);
        pivot = pivot.multiply(-1.0D, 1.0D, 1.0D);
        rotation = new Vec3(Math.toRadians(-rotation.x), Math.toRadians(-rotation.y), Math.toRadians(rotation.z));
        GeoQuad[] quads = this.buildQuads(cube.uv(), new BakedModelFactory.VertexSet(origin, vertexSize, inflate), cube,
                (float) properties.textureWidth(), (float) properties.textureHeight(), mirror);
        return new GeoCube(quads, pivot, rotation, size, inflate, mirror);
    }
}
