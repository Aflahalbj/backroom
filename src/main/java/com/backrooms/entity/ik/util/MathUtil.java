package com.backrooms.entity.ik.util;

import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/** Port dari {@code com.sp.entity.ik.util.MathUtil}. */
public class MathUtil {
    public static Vec3 toVec3(Vector3d vector3d) {
        return new Vec3(vector3d.x(), vector3d.y(), vector3d.z());
    }

    public static Vector3d toVector3d(Vec3 vector3d) {
        return new Vector3d(vector3d.x, vector3d.y, vector3d.z);
    }

    public static Vec3 getFlatRotationVector(Entity entity) {
        return getFlatRotationVector(entity.getVisualRotationYInDegrees());
    }

    public static Vec3 getFlatRotationVector(double yRot) {
        float f = 0.0F;
        float g = (float) (-yRot * (double) ((float) Math.PI / 180F));
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j).normalize();
    }

    public static Vec3 getOpposingFlatRotationVector(Entity entity) {
        return getFlatRotationVector(entity.getVisualRotationYInDegrees() - 180.0F);
    }

    public static Vec3 getRotation(Vec3 base, Vec3 target) {
        float dz = (float) (target.z - base.z);
        float dx = (float) (target.x - base.x);
        float dy = (float) (target.y - base.y);
        float yaw = (float) Mth.atan2(dz, dx);
        float pitch = (float) Mth.atan2(Math.sqrt(dz * dz + dx * dx), dy);
        return wrapAngles(new Vec3(yaw, pitch, 0.0D));
    }

    private static Vec3 wrapAngles(Vec3 vec3d) {
        double x = vec3d.x;
        double y = vec3d.y;
        double z;
        for (z = vec3d.z; x > Math.PI; x -= Math.PI * 2D) {
        }
        while (x < -Math.PI) {
            x += Math.PI * 2D;
        }
        while (y > Math.PI) {
            y -= Math.PI * 2D;
        }
        while (y < -Math.PI) {
            y += Math.PI * 2D;
        }
        while (z > Math.PI) {
            z -= Math.PI * 2D;
        }
        while (z < -Math.PI) {
            z += Math.PI * 2D;
        }
        return new Vec3(x, y, z);
    }

    public static Vec3 dividePos(Vec3 v1, double divide) {
        return new Vec3(v1.x / divide, v1.y / divide, v1.z / divide);
    }

    public static Quaternionf quatFromRotationXYZ(float x, float y, float z, boolean degrees) {
        if (degrees) {
            x *= (float) Math.PI / 180F;
            y *= (float) Math.PI / 180F;
            z *= (float) Math.PI / 180F;
        }
        return new Quaternionf().rotationXYZ(x, y, z);
    }

    public static Vec3 getAverage(List<Vec3> points) {
        Vec3 sum = Vec3.ZERO;
        for (Vec3 point : points) {
            sum = sum.add(point);
        }
        return dividePos(sum, points.size());
    }

    public static Vec3 getAverage(Vec3... points) {
        return getAverage(Arrays.stream(points).toList());
    }

    public static double calculateAngle(Vec3 A, Vec3 B, Vec3 C) {
        double a = C.distanceTo(B);
        double b = A.distanceTo(C);
        double c = A.distanceTo(B);
        double cosA = (b * b + c * c - a * a) / (2.0D * b * c);
        return Math.acos(cosA);
    }

    public static Vec3 rotatePointOnAPlaneAround(Vec3 RotatedPoint, Vec3 stationaryPoint, double angle, Vec3 rotationAxis) {
        Vector3d A = new Vector3d(stationaryPoint.x, stationaryPoint.y, stationaryPoint.z);
        Vector3d C = new Vector3d(RotatedPoint.x, RotatedPoint.y, RotatedPoint.z);
        Quaterniond rotation = new Quaterniond().rotateAxis(Math.toRadians(angle), rotationAxis.x, rotationAxis.y, rotationAxis.z);
        Vector3d rotatedV1 = new Vector3d();
        rotation.transform(new Vector3d(C).sub(A), rotatedV1);
        return new Vec3(new Vector3d(rotatedV1).add(A).x(), new Vector3d(rotatedV1).add(A).y(), new Vector3d(rotatedV1).add(A).z());
    }

    public static Vec3 getUpDirection(Vec3 v1, Vec3 v2, Vec3 v3) {
        Vec3 AB = v2.subtract(v1);
        Vec3 AC = v3.subtract(v1);
        return AB.cross(AC).normalize();
    }

    public static Vec3 getClosestNormalRelativeToEntity(Vec3 basePoint, Vec3 v2, Vec3 v3, Entity entity) {
        Vec3 referencePoint = getFlatRotationVector(entity.getVisualRotationYInDegrees() + 90.0F);
        return getNormalClosestTo(basePoint, v2, v3, basePoint.add(referencePoint.scale(100.0D)));
    }

    public static Vec3 getNormalClosestTo(Vec3 basePoint, Vec3 v2, Vec3 v3, Vec3 orientationPoint) {
        Vec3 normal = getUpDirection(basePoint, v2, v3);
        Vec3 oppositeNormal = normal.reverse();
        return basePoint.add(normal).distanceToSqr(orientationPoint) < basePoint.add(oppositeNormal).distanceToSqr(orientationPoint) ? normal : oppositeNormal;
    }

    public static Vec3 lerpVec3(int step, Vec3 OldPos, Vec3 newPos) {
        double d = 1.0D / (double) step;
        double newX = Mth.lerp(d, OldPos.x, newPos.x);
        double newY = Mth.lerp(d, OldPos.y, newPos.y);
        double newZ = Mth.lerp(d, OldPos.z, newPos.z);
        return new Vec3(newX, newY, newZ);
    }

    public static Vec3 lerpVector3d(int step, Vector3d OldPos, Vector3d newPos) {
        return lerpVec3(step, toVec3(OldPos), toVec3(newPos));
    }

    public static Vec3 convertToFlatVector(Vec3 v1) {
        return new Vec3((float) v1.x, 0.0D, (float) v1.z);
    }
}
