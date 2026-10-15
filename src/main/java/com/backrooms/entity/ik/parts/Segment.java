package com.backrooms.entity.ik.parts;

import net.minecraft.world.phys.Vec3;

/** Port dari {@code com.sp.entity.ik.parts.Segment}. */
public class Segment {
    public double length;
    public Segment.StretchDirection stretchDirection;
    public boolean hasAngleConstraints;
    public double angleOffset;
    public double angleSize;
    private Vec3 position = Vec3.ZERO;

    protected Segment(Segment.Builder builder) {
        this.length = builder.length;
        this.stretchDirection = builder.stretchDirection;
        this.angleSize = builder.angleSize;
        this.angleOffset = builder.angleOffset;
        this.hasAngleConstraints = builder.hasAngleConstraints;
    }

    public Vec3 getPosition() {
        return this.position;
    }

    public void move(Vec3 position) {
        this.position = position;
    }

    public static class Builder {
        private double length;
        private Segment.StretchDirection stretchDirection = Segment.StretchDirection.TARGET;
        private boolean hasAngleConstraints = true;
        private double angleOffset = 90.0D;
        private double angleSize = 30.0D;

        public Segment.Builder length(double length) {
            this.length = length;
            return this;
        }

        public Segment.Builder stretchDirection(Segment.StretchDirection stretchDirection) {
            this.stretchDirection = stretchDirection;
            return this;
        }

        public Segment.Builder angleConstraints(boolean hasAngleConstraints) {
            this.hasAngleConstraints = hasAngleConstraints;
            return this;
        }

        public Segment.Builder angleOffset(double angleOffset) {
            this.angleOffset = angleOffset;
            return this;
        }

        public Segment.Builder angleSize(double angleSize) {
            this.angleSize = angleSize;
            return this;
        }

        public Segment build() {
            return new Segment(this);
        }
    }

    public enum StretchDirection {
        FORWARDS,
        BACKWARDS,
        DOWN,
        TARGET,
        NONE
    }
}
