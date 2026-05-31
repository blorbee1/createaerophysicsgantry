package com.blorbee.createaerophysicsgantry.ponder;

import com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry;
import com.blorbee.createaerophysicsgantry.ponder.scenes.BeltWheelScenes;
import com.blorbee.createaerophysicsgantry.ponder.scenes.PhysicsGantryScenes;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class CAPGPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreateAeroPhysicsGantry.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerScenes(helper);
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> HELPER = helper.withKeyFunction(RegistryEntry::getId);

        HELPER.forComponents(CAPGBlocks.PHYSICS_GANTRY_CARRIAGE)
            .addStoryBoard("physics_gantry/intro", PhysicsGantryScenes::introForCarriage);
        HELPER.forComponents(CAPGBlocks.PHYSICS_GANTRY_SHAFT)
            .addStoryBoard("physics_gantry/intro", PhysicsGantryScenes::introForShaft);
        HELPER.forComponents(CAPGBlocks.PHYSICS_GANTRY_CARRIAGE, CAPGBlocks.PHYSICS_GANTRY_SHAFT)
            .addStoryBoard("physics_gantry/redstone", PhysicsGantryScenes::redstone);

        HELPER.forComponents(CAPGBlocks.BELT_WHEEL)
            .addStoryBoard("belt_wheel/intro", BeltWheelScenes::connecting)
            .addStoryBoard("belt_wheel/intro", BeltWheelScenes::relaying);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerTags(helper);
        PonderTagRegistrationHelper<RegistryEntry<?, ?>> HELPER = helper.withKeyFunction(RegistryEntry::getId);

        HELPER.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
            .add(CAPGBlocks.PHYSICS_GANTRY_CARRIAGE)
            .add(CAPGBlocks.PHYSICS_GANTRY_SHAFT);

        HELPER.addToTag(AllCreatePonderTags.MOVEMENT_ANCHOR)
            .add(CAPGBlocks.PHYSICS_GANTRY_CARRIAGE)
            .add(CAPGBlocks.PHYSICS_GANTRY_SHAFT)
            .add(CAPGBlocks.BELT_WHEEL);

        HELPER.addToTag(AllCreatePonderTags.KINETIC_RELAYS)
            .add(CAPGBlocks.BELT_WHEEL);
    }

    public static void honeyGlueEffect(CreateSceneBuilder scene, Vec3 pos) {
        CreateSceneBuilder.EffectInstructions effects = scene.effects();
        effects.emitParticles(pos,
            effects.particleEmitterWithinBlockSpace(new DustParticleOptions((new Color(255, 232, 142)).asVectorF(), 1.0F),
                Vec3.ZERO), 10.0F, 2);
    }
}
