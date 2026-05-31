package com.blorbee.createaerophysicsgantry.registry;

import com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public final class CAPGPartialModels {
    public static final PartialModel BELT_SEGMENT = block("belt_wheel/belt_segment");

    public static final PartialModel PHYSICS_GANTRY_COGS = block("gantry_carriage/wheels");

    public static final PartialModel GANTRY_SHAFT_START = block("gantry_shaft/block_start");
    public static final PartialModel GANTRY_SHAFT_MIDDLE = block("gantry_shaft/block_middle");
    public static final PartialModel GANTRY_SHAFT_END = block("gantry_shaft/block_end");
    public static final PartialModel GANTRY_SHAFT_SINGLE = block("gantry_shaft/block_single");
    public static final PartialModel GANTRY_SHAFT_START_POWERED = block("gantry_shaft_start_powered");
    public static final PartialModel GANTRY_SHAFT_MIDDLE_POWERED = block("gantry_shaft_middle_powered");
    public static final PartialModel GANTRY_SHAFT_END_POWERED = block("gantry_shaft_end_powered");
    public static final PartialModel GANTRY_SHAFT_SINGLE_POWERED = block("gantry_shaft_single_powered");
    public static final PartialModel GANTRY_SHAFT_START_FLIPPED = block("gantry_shaft_start_flipped");
    public static final PartialModel GANTRY_SHAFT_MIDDLE_FLIPPED = block("gantry_shaft_middle_flipped");
    public static final PartialModel GANTRY_SHAFT_END_FLIPPED = block("gantry_shaft_end_flipped");
    public static final PartialModel GANTRY_SHAFT_SINGLE_FLIPPED = block("gantry_shaft_single_flipped");
    public static final PartialModel GANTRY_SHAFT_START_POWERED_FLIPPED = block("gantry_shaft_start_powered_flipped");
    public static final PartialModel GANTRY_SHAFT_MIDDLE_POWERED_FLIPPED = block("gantry_shaft_middle_powered_flipped");
    public static final PartialModel GANTRY_SHAFT_END_POWERED_FLIPPED = block("gantry_shaft_end_powered_flipped");
    public static final PartialModel GANTRY_SHAFT_SINGLE_POWERED_FLIPPED = block("gantry_shaft_single_powered_flipped");

    public static void register() {}

    private static PartialModel block(String path) {
        return PartialModel.of(CreateAeroPhysicsGantry.path("block/" + path));
    }
}
