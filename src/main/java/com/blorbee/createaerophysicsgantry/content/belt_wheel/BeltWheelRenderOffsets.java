package com.blorbee.createaerophysicsgantry.content.belt_wheel;

import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public class BeltWheelRenderOffsets {
    private record Offset(Vec3 previous, Vec3 current) {}

    private static final Map<BeltWheelBlockEntity, Offset> OFFSETS = new WeakHashMap<>();

    public static void set(BeltWheelBlockEntity be, Vec3 absolute) {
        OFFSETS.compute(be, (k, old) -> new Offset(old == null ? absolute : old.current(), absolute));
    }

    public static void clear(BeltWheelBlockEntity be) {
        OFFSETS.remove(be);
    }

    public static Vec3 get(BeltWheelBlockEntity be, float partialTicks) {
        if (OFFSETS.isEmpty())
            return Vec3.ZERO;
        Offset offset = OFFSETS.get(be);
        return offset == null ? Vec3.ZERO : offset.previous().lerp(offset.current(), partialTicks);
    }
}
