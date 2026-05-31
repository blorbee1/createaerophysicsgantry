package com.blorbee.createaerophysicsgantry.ponder.scenes;

import com.blorbee.createaerophysicsgantry.content.belt_wheel.BeltWheelBlockEntity;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.simulated_team.simulated.ponder.instructions.PullTheAssemblerKronkInstruction;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class BeltWheelScenes {
    public static void connecting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("belt_wheel_connecting", "Connecting Belt Wheels");
        scene.configureBasePlate(1, 0, 5);

        BlockPos beltWheelMainA = util.grid().at(5, 1, 2);
        BlockPos beltWheelMainB = util.grid().at(1, 1, 2);

        BlockPos beltWheelVertical = util.grid().at(5, 1, 1);

        BlockPos beltWheelMisalignedA = util.grid().at(3, 1, 1);
        BlockPos beltWheelMisalignedB = util.grid().at(2, 1, 4);

        BlockPos plank = util.grid().at(3, 1, 2);
        BlockPos assembler = util.grid().at(1, 2, 2);

        AABB connectBB = new AABB(util.vector().centerOf(beltWheelMainA), util.vector().centerOf(beltWheelMainA));
        AABB wheelBB = CAPGBlocks.BELT_WHEEL.getDefaultState()
            .getShape(null, null)
            .bounds();

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        scene.world().showSection(util.select().position(beltWheelMainA), Direction.DOWN);
        scene.world().showSection(util.select().position(beltWheelMainB), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(60)
            .attachKeyFrame()
            .text("Belt Wheels link together using a Mechanical Belt.")
            .pointAt(util.vector().centerOf(beltWheelMainA))
            .placeNearTarget();
        scene.idle(70);

        ItemStack beltItem = AllItems.BELT_CONNECTOR.asStack();

        scene.overlay().showControls(util.vector().topOf(beltWheelMainA), Pointing.DOWN, 57)
            .rightClick()
            .withItem(beltItem);
        scene.idle(7);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, beltWheelMainA, wheelBB.move(beltWheelMainA), 42);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLACK, util.vector().centerOf(beltWheelMainA), connectBB, 50);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(beltWheelMainB), Pointing.DOWN, 37)
            .rightClick()
            .withItem(beltItem);
        scene.idle(7);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, beltWheelMainB, wheelBB.move(beltWheelMainB), 17);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLACK, util.vector().centerOf(beltWheelMainA), connectBB.expandTowards(-4, 0, 0), 20);
        scene.idle(20);

        scene.world().modifyBlockEntity(beltWheelMainA, BeltWheelBlockEntity.class,
            b -> b.setLinkedTarget(beltWheelMainB, null));
        scene.world().modifyBlockEntity(beltWheelMainB, BeltWheelBlockEntity.class,
            b -> b.setLinkedTarget(beltWheelMainA, null));

        scene.overlay().showText(80)
            .text("Right-Clicking two Belt Wheels with a belt item will connect them together")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(plank));
        scene.idle(90);

        scene.world().showSection(util.select().position(plank), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(60)
            .text("Belt Wheels can link together through blocks")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(plank));
        scene.overlay().showOutline(PonderPalette.OUTPUT, "plank_outline", util.select().position(plank), 60);
        scene.idle(70);

        scene.world().modifyBlockEntity(beltWheelMainA, BeltWheelBlockEntity.class,
            b -> b.breakLink(false));
        scene.world().modifyBlockEntity(beltWheelMainB, BeltWheelBlockEntity.class,
            b -> b.breakLink(false));

        scene.world().hideSection(util.select().position(beltWheelMainA), Direction.UP);
        scene.world().hideSection(util.select().position(beltWheelMainB), Direction.UP);
        scene.world().hideSection(util.select().position(plank), Direction.UP);
        scene.idle(10);

        scene.world().showSection(util.select().position(beltWheelVertical), Direction.DOWN);
        scene.world().showSection(util.select().position(beltWheelMisalignedA), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(50)
            .text("You cannot connect two Belt Wheels that do not have the same rotation axis")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(beltWheelMisalignedA));

        scene.overlay().showOutline(PonderPalette.RED, "belt_wheel_vertical", util.select().position(beltWheelVertical), 50);
        scene.overlay().showOutline(PonderPalette.RED, "belt_wheel_misaligned_a", util.select().position(beltWheelMisalignedA), 50);

        scene.idle(60);

        scene.world().hideSection(util.select().position(beltWheelVertical), Direction.UP);
        scene.world().showSection(util.select().position(beltWheelMisalignedB), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(50)
            .text("They also must be aligned in order to connect them")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(beltWheelMisalignedB));

        scene.overlay().showOutline(PonderPalette.RED, "belt_wheel_misaligned_a", util.select().position(beltWheelMisalignedA), 50);
        scene.overlay().showOutline(PonderPalette.RED, "belt_wheel_misaligned_b", util.select().position(beltWheelMisalignedB), 50);
        scene.idle(60);

        scene.world().hideSection(util.select().position(beltWheelMisalignedA), Direction.UP);
        scene.world().hideSection(util.select().position(beltWheelMisalignedB), Direction.UP);
        scene.idle(10);

        scene.world().showSection(util.select().position(beltWheelMainA), Direction.DOWN);
        scene.world().showSection(util.select().position(beltWheelMainB), Direction.DOWN);
        scene.idle(20);

        scene.world().showSection(util.select().position(assembler), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(80)
            .text("Belt Wheels can also connect to across different Simulated Contraptions.")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(assembler));
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(assembler, Direction.WEST), Pointing.LEFT, 20).rightClick();
        scene.idle(3);
        scene.addInstruction(new PullTheAssemblerKronkInstruction(assembler, true, false));
        scene.idle(25);

        scene.overlay().showControls(util.vector().topOf(beltWheelMainA), Pointing.DOWN, 57)
            .rightClick()
            .withItem(beltItem);
        scene.idle(7);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, beltWheelMainA, wheelBB.move(beltWheelMainA), 42);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLACK, util.vector().centerOf(beltWheelMainA), connectBB, 50);
        scene.idle(20);

        scene.overlay().showControls(util.vector().topOf(beltWheelMainB), Pointing.DOWN, 37)
            .rightClick()
            .withItem(beltItem);
        scene.idle(7);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, beltWheelMainB, wheelBB.move(beltWheelMainB), 17);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLACK, util.vector().centerOf(beltWheelMainA), connectBB.expandTowards(-4, 0, 0), 20);
        scene.idle(20);

        scene.world().modifyBlockEntity(beltWheelMainA, BeltWheelBlockEntity.class,
            b -> b.setLinkedTarget(beltWheelMainB, null));
        scene.world().modifyBlockEntity(beltWheelMainB, BeltWheelBlockEntity.class,
            b -> b.setLinkedTarget(beltWheelMainA, null));

        scene.markAsFinished();
    }

    public static void relaying(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("belt_wheel_relaying", "Relaying Rotation with Belt Wheels");
        scene.configureBasePlate(1, 0, 5);

        BlockPos beltWheelA = util.grid().at(5, 1, 2);
        BlockPos beltWheelB = util.grid().at(1, 1, 2);

        BlockPos cogA = util.grid().at(5, 1, 3);
        BlockPos cogB = util.grid().at(1, 1, 1);
        BlockPos cogC = util.grid().at(0, 1, 2);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        scene.world().showSection(util.select().position(beltWheelA), Direction.DOWN);
        scene.world().showSection(util.select().position(beltWheelB), Direction.DOWN);
        scene.world().showSection(util.select().position(cogA), Direction.DOWN);
        scene.idle(10);

        scene.world().modifyBlockEntity(beltWheelA, BeltWheelBlockEntity.class,
            b -> b.setLinkedTarget(beltWheelB, null));
        scene.world().modifyBlockEntity(beltWheelB, BeltWheelBlockEntity.class,
            b -> b.setLinkedTarget(beltWheelA, null));

        scene.world().setKineticSpeed(util.select().layer(1), 32);
        scene.world().setKineticSpeed(util.select().position(cogC), -32);
        scene.idle(25);

        scene.overlay().showText(35)
            .placeNearTarget()
            .text("When one side is given rotation...")
            .attachKeyFrame()
            .pointAt(util.vector().centerOf(beltWheelA));
        scene.idle(35);

        scene.overlay().showText(35)
            .placeNearTarget()
            .text("...the other side will receive that same rotation.")
            .attachKeyFrame()
            .pointAt(util.vector().centerOf(beltWheelB));
        scene.idle(45);

        scene.world().showSection(util.select().position(cogB), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(35)
            .placeNearTarget()
            .text("You can then use that rotation through the shaft on either end...")
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(cogB, Direction.WEST));
        scene.idle(35);

        scene.world().showSection(util.select().position(cogC), Direction.EAST);
        scene.idle(10);

        scene.overlay().showText(35)
            .placeNearTarget()
            .text("...or from the cogwheel directly")
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(cogC, Direction.WEST));
        scene.idle(45);

        scene.markAsFinished();
    }
}
