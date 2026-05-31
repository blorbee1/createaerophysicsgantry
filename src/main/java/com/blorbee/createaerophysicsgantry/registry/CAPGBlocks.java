package com.blorbee.createaerophysicsgantry.registry;

import com.blorbee.createaerophysicsgantry.content.belt_wheel.BeltWheelBlock;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_carriage.PhysicsGantryCarriageBlock;
import com.blorbee.createaerophysicsgantry.content.physics_gantry_shaft.PhysicsGantryShaftBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.data.*;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;

import static com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry.REGISTRATE;

public final class CAPGBlocks {
    public static final BlockEntry<PhysicsGantryShaftBlock> PHYSICS_GANTRY_SHAFT =
        REGISTRATE.block("physics_gantry_shaft", PhysicsGantryShaftBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p
                .mapColor(MapColor.NETHER)
                .forceSolidOn()
            )
            .transform(TagGen.axeOrPickaxe())
            .blockstate((c, p) -> p.directionalBlock(c.get(), s -> {
                boolean isPowered = s.getValue(PhysicsGantryShaftBlock.POWERED);
                boolean isFlipped = s.getValue(PhysicsGantryShaftBlock.FACING)
                    .getAxisDirection() == Direction.AxisDirection.NEGATIVE;
                String partName = s.getValue(PhysicsGantryShaftBlock.PART)
                    .getSerializedName();
                String flipped = isFlipped ? "_flipped" : "";
                String powered = isPowered ? "_powered": "";

                ModelFile existing = p.models().getExistingFile(p.modLoc("block/gantry_shaft/block_" + partName));
                if (!isPowered && !isFlipped)
                    return existing;

                return p.models()
                    .withExistingParent("block/gantry_shaft_" + partName + powered + flipped, existing.getLocation())
                    .texture("2", p.modLoc("block/gantry/gantry_shaft" + powered + flipped));
            }))
            .lang("Physics Gantry Shaft")
            .recipe((ctx, provider) -> {
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 8)
                    .pattern("B")
                    .pattern("R")
                    .pattern("B")
                    .define('B', AllItems.BRASS_INGOT)
                    .define('R', Items.REDSTONE)
                    .unlockedBy("has_brass", RegistrateRecipeProvider.has(AllItems.BRASS_INGOT))
                    .unlockedBy("has_redstone", RegistrateRecipeProvider.has(Items.REDSTONE))
                    .save(provider);
            })
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/gantry_shaft/block_single")))
            .build()
            .register();

    public static final BlockEntry<PhysicsGantryCarriageBlock> PHYSICS_GANTRY_CARRIAGE =
        REGISTRATE.block("physics_gantry_carriage", PhysicsGantryCarriageBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p
                .mapColor(MapColor.PODZOL)
                .noOcclusion()
            )
            .transform(TagGen.axeOrPickaxe())
            .blockstate((c, p) -> BlockStateGen.directionalAxisBlock(c, p, ($, vertical) ->
                p.models().getExistingFile(p.modLoc("block/gantry_carriage/" + (vertical ? "vertical" : "horizontal")))
            ))
            .lang("Physics Gantry Carriage")
            .recipe((ctx, provider) -> {
                ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 1)
                    .pattern("S")
                    .pattern("C")
                    .pattern("W")
                    .define('S', ItemTags.WOODEN_SLABS)
                    .define('C', AllBlocks.BRASS_CASING)
                    .define('W', AllBlocks.COGWHEEL)
                    .unlockedBy("has_gantry_shaft", RegistrateRecipeProvider.has(PHYSICS_GANTRY_SHAFT))
                    .unlockedBy("has_brass_casing", RegistrateRecipeProvider.has(AllBlocks.BRASS_CASING))
                    .unlockedBy("has_cogwheel", RegistrateRecipeProvider.has(AllBlocks.COGWHEEL))
                    .unlockedBy("has_wooden_slabs", RegistrateRecipeProvider.has(ItemTags.WOODEN_SLABS))
                    .save(provider);
            })
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/gantry_carriage/item")))
            .build()
            .register();

    public static final BlockEntry<BeltWheelBlock> BELT_WHEEL =
        REGISTRATE.block("belt_wheel", BeltWheelBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p
                .mapColor(MapColor.COLOR_GRAY)
                .strength(3.0F)
                .noOcclusion()
            )
            .transform(TagGen.axeOrPickaxe())
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStates(state -> {
                Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
                boolean bottomShaft = state.getValue(BeltWheelBlock.BOTTOM_SHAFT);
                boolean topShaft = state.getValue(BeltWheelBlock.TOP_SHAFT);

                String modelSuffix = "block";
                if (topShaft && bottomShaft) {
                    modelSuffix = "block_top_bottom";
                } else if (topShaft) {
                    modelSuffix = "block_top";
                } else if (bottomShaft) {
                    modelSuffix = "block_bottom";
                }

                ModelFile model = p.models().getExistingFile(p.modLoc("block/belt_wheel/" + modelSuffix));
                int xRot = (axis == Direction.Axis.Y) ? 0 : 90;
                int yRot = (axis == Direction.Axis.Z) ? 180 : (axis == Direction.Axis.X ? 90 : 0);

                return ConfiguredModel.builder()
                    .modelFile(model)
                    .rotationX(xRot)
                    .rotationY(yRot)
                    .build();
            }))
            .lang("Belt Wheel")
            .recipe((ctx, provider) -> {
                ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ctx.get(), 2)
                    .requires(AllBlocks.COGWHEEL)
                    .requires(AllBlocks.BRASS_CASING)
                    .requires(AllItems.BELT_CONNECTOR)
                    .unlockedBy("has_cogwheel", RegistrateRecipeProvider.has(AllBlocks.COGWHEEL))
                    .unlockedBy("has_brass_casing", RegistrateRecipeProvider.has(AllBlocks.BRASS_CASING))
                    .unlockedBy("has_belt_connector", RegistrateRecipeProvider.has(AllItems.BELT_CONNECTOR))
                    .save(provider);
            })
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/belt_wheel/item")))
            .build()
            .register();

    public static void register() {}
}
