package com.blorbee.createaerophysicsgantry.registry;

import com.blorbee.createaerophysicsgantry.content.belt_wheel.BeltWheelBlockEntity;
import com.blorbee.createaerophysicsgantry.content.belt_wheel.BeltWheelRenderer;
import com.blorbee.createaerophysicsgantry.content.belt_wheel.BeltWheelVisual;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlockEntity;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageRenderer;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageVisual;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft.PhysicsGantryShaftBlockEntity;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft.PhysicsGantryShaftVisual;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry.REGISTRATE;

public final class CAPGBlockEntityTypes {
    public static final BlockEntityEntry<PhysicsGantryShaftBlockEntity> PHYSICS_GANTRY_SHAFT =
        REGISTRATE.blockEntity("physics_gantry_shaft", PhysicsGantryShaftBlockEntity::new)
            .visual(() -> PhysicsGantryShaftVisual::create, false)
            .validBlocks(CAPGBlocks.PHYSICS_GANTRY_SHAFT)
            .renderer(() -> KineticBlockEntityRenderer::new)
            .register();

    public static final BlockEntityEntry<PhysicsGantryCarriageBlockEntity> PHYSICS_GANTRY_CARRIAGE =
        REGISTRATE.blockEntity("physics_gantry_carriage", PhysicsGantryCarriageBlockEntity::new)
            .visual(() -> PhysicsGantryCarriageVisual::new)
            .validBlocks(CAPGBlocks.PHYSICS_GANTRY_CARRIAGE)
            .renderer(() -> PhysicsGantryCarriageRenderer::new)
            .register();

    public static final BlockEntityEntry<BeltWheelBlockEntity> BELT_WHEEL =
        REGISTRATE.blockEntity("belt_wheel", BeltWheelBlockEntity::new)
            .visual(() -> BeltWheelVisual::new)
            .validBlocks(CAPGBlocks.BELT_WHEEL)
            .renderer(() -> BeltWheelRenderer::new)
            .register();

    public static void register() {}
}
