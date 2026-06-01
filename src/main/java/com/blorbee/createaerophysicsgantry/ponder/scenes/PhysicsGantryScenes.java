package com.blorbee.createaerophysicsgantry.ponder.scenes;

import com.blorbee.createaerophysicsgantry.ponder.CAPGPonderPlugin;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.simulated_team.simulated.index.SimItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class PhysicsGantryScenes {
    public static void introForCarriage(SceneBuilder scene, SceneBuildingUtil util) {
        intro(scene, util, true);
    }

    public static void introForShaft(SceneBuilder scene, SceneBuildingUtil util) {
        intro(scene, util, false);
    }

    private static void intro(SceneBuilder builder, SceneBuildingUtil util, boolean carriage) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        String id = "physics_gantry_" + (carriage ? "carriage" : "shaft");
        String title = "Using Physics Gantry " + (carriage ? "Carriages" : "Shafts");
        scene.title(id, title);

        scene.world().setKineticSpeed(util.select().layer(0), 32);
        scene.world().setKineticSpeed(util.select().layer(1), 32);
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(10);

        ElementLink<WorldSectionElement> gantry =
            scene.world().showIndependentSection(util.select().layer(2), Direction.DOWN);
        scene.idle(10);

        BlockPos centralShaft = util.grid().at(2, 1, 2);
        BlockPos gantryPos = util.grid().at(4, 2, 2);

        String text = carriage ? "Physics Gantry Carriages can mount to and slide along only Physics Gantry Shafts"
            : "Physics Gantry Shafts form the basis of a physics gantry setup. Attached Carriages will move along them.";

        scene.overlay().showText(80)
            .attachKeyFrame()
            .placeNearTarget()
            .text(text)
            .pointAt(util.vector().centerOf(centralShaft));
        scene.idle(100);

        scene.overlay().showText(80)
            .attachKeyFrame()
            .placeNearTarget()
            .text("Physics Gantry Carriages will only move while it is a Simulated Contraption, even if the shaft is rotating.")
            .pointAt(util.vector().centerOf(gantryPos));
        scene.idle(80);

        scene.rotateCameraY(180.0F);
        scene.idle(40);

        scene.overlay().showText(60)
            .attachKeyFrame()
            .placeNearTarget()
            .text("Make the carriage a Simulated Contraption by right clicking it, at which it will begin moving.")
            .pointAt(util.vector().centerOf(gantryPos));
        scene.overlay().showControls(util.vector().blockSurface(gantryPos, Direction.UP), Pointing.DOWN, 50)
            .rightClick();

        scene.idle(50);

        scene.world().moveSection(gantry, util.vector().of(-4, 0, 0), 60);

        if (!carriage) {
            scene.markAsFinished();
            return;
        }

        scene.idle(80);
        scene.world().hideIndependentSection(gantry, Direction.UP);
        scene.rotateCameraY(180.0F);
        scene.idle(50);

        gantry = scene.world().showIndependentSection(util.select().layer(2), Direction.DOWN);
        Vec3 gantryTop = util.vector().topOf(4, 2, 2);

        scene.overlay().showText(60)
            .attachKeyFrame()
            .text("Just like regular Gantries, Physics Gantry setups can move attached blocks.")
            .pointAt(gantryTop)
            .placeNearTarget();
        scene.idle(50);

        Selection planks = util.select().position(5, 3, 1);

        scene.world().showSectionAndMerge(util.select().layersFrom(3).substract(planks), Direction.DOWN, gantry);
        scene.world().replaceBlocks(util.select().fromTo(5, 3, 2, 3, 4, 2), Blocks.OAK_PLANKS.defaultBlockState(), false);
        scene.idle(10);

        scene.world().showSectionAndMerge(planks, Direction.SOUTH, gantry);

        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "honey_glue", util.select().position(3, 4, 2)
            .add(util.select().fromTo(3, 3, 2, 5, 3, 2))
            .add(util.select().position(5, 3, 1)), 40);
        scene.overlay().showControls(util.vector().centerOf(util.grid().at(3, 3, 2)), Pointing.UP, 40)
            .withItem(SimItems.HONEY_GLUE.asStack());
        CAPGPonderPlugin.honeyGlueEffect(scene, new Vec3(5, 3, 1));
        scene.idle(20);

        scene.overlay().showText(80)
            .attachKeyFrame()
            .text("Either Honey Glue or Super Glue can be used to move larger structures.")
            .pointAt(util.vector().blockSurface(util.grid().at(3, 3, 2), Direction.WEST))
            .placeNearTarget();
        scene.idle(80);

        scene.world().moveSection(gantry, util.vector().of(-4, 0, 0), 60);
        scene.idle(20);
        scene.markAsFinished();
    }

    public static void redstone(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("physics_gantry_redstone", "Physics Gantry Power Halting");

        scene.world().setKineticSpeed(util.select().layer(0), 32);
        scene.world().setKineticSpeed(util.select().layer(1), 32);
        scene.configureBasePlate(0, 0, 5);

        Selection leverRedstone = util.select().fromTo(3, 1, 0, 3, 1, 1);
        Selection shaft = util.select().fromTo(0, 1, 2, 4, 1, 2);
        Selection shaftAndCog = util.select().fromTo(0, 1, 2, 5, 1, 2);

        scene.world().showSection(util.select().layer(0)
            .add(leverRedstone), Direction.UP);

        scene.idle(10);
        scene.world().showSection(shaftAndCog, Direction.DOWN);
        scene.idle(10);

        BlockPos gantryPos = util.grid().at(4, 2, 2);
        ElementLink<WorldSectionElement> gantry =
            scene.world().showIndependentSection(util.select().position(gantryPos), Direction.DOWN);

        scene.idle(15);
        scene.world().moveSection(gantry, util.vector().of(-3, 0, 0), 40);
        scene.idle(40);

        scene.world().toggleRedstonePower(shaft);
        scene.world().toggleRedstonePower(util.select().position(3, 1, 0));
        scene.world().toggleRedstonePower(util.select().position(3, 1, 1));
        scene.effects().indicateRedstone(util.grid().at(3, 1, 0));
        scene.idle(40);

        BlockPos endPos = util.grid().at(1, 2, 1);
        scene.overlay().showText(60)
            .attachKeyFrame()
            .colored(PonderPalette.RED)
            .pointAt(util.vector().centerOf(endPos.below().south()))
            .text("Redstone-powered physics gantry shafts stop moving their carriages")
            .placeNearTarget();
        scene.idle(70);

        scene.world().toggleRedstonePower(shaft);
        scene.world().toggleRedstonePower(util.select().position(3, 1, 0));
        scene.world().toggleRedstonePower(util.select().position(3, 1, 1));
        scene.effects().indicateRedstone(util.grid().at(3, 1, 0));

        scene.world().hideIndependentSection(gantry, Direction.UP);
        scene.idle(20);

        gantry = scene.world().showIndependentSection(util.select().layer(2), Direction.DOWN);

        scene.idle(15);
        scene.world().moveSection(gantry, util.vector().of(-2.5, 0, 0), 40);
        scene.idle(40);

        scene.world().toggleRedstonePower(shaft);
        scene.world().toggleRedstonePower(util.select().position(3, 1, 0));
        scene.world().toggleRedstonePower(util.select().position(3, 1, 1));
        scene.effects().indicateRedstone(util.grid().at(3, 1, 0));
        scene.idle(40);

        Vec3 halfwayEndPos = util.vector().of(1.5, 2, 1.5);
        scene.overlay().showText(80)
            .attachKeyFrame()
            .pointAt(halfwayEndPos)
            .text("Because Physics Gantry Carriages are Simulated Contraptions, they will not disassemble when stopping between blocks.")
            .placeNearTarget();

        scene.overlay().showOutline(PonderPalette.GREEN, "physics_gantry_shaft", util.select().position(2, 1, 2), 80);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "physics_gantry_shaft_2", util.select().position(1, 1, 2), 80);

        scene.idle(90);

        scene.markAsFinished();
    }
}
