package com.blorbee.createaerophysicsgantry.registry;

import com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class CAPGCreativeTab {
    private static final ResourceLocation SIMULATED_SECTION = ResourceLocation.fromNamespaceAndPath("simulated", "simulated");
    private static boolean sectionsInitialized = false;

    public static synchronized void registerAeronauticsSections() {
        if (sectionsInitialized)
            return;

        registerSectionItem(SIMULATED_SECTION, CreateAeroPhysicsGantry.path("physics_gantry_shaft"), CAPGBlocks.PHYSICS_GANTRY_SHAFT::asItem);
        registerSectionItem(SIMULATED_SECTION, CreateAeroPhysicsGantry.path("physics_gantry_carriage"), CAPGBlocks.PHYSICS_GANTRY_CARRIAGE::asItem);
        registerSectionItem(SIMULATED_SECTION, CreateAeroPhysicsGantry.path("belt_wheel"), CAPGBlocks.BELT_WHEEL::asItem);

        sectionsInitialized = true;
    }

    private static void registerSectionItem(ResourceLocation sectionId, ResourceLocation itemPath, Supplier<Item> itemSupplier) {
        SimulatedRegistrate.TAB_ITEMS.add(itemSupplier);
        SimulatedRegistrate.ITEM_TO_SECTION.put(itemPath, sectionId);
    }
}
