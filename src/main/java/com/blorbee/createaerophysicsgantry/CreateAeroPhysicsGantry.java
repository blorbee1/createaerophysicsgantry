package com.blorbee.createaerophysicsgantry;

import com.blorbee.createaerophysicsgantry.config.ServerConfig;
import com.blorbee.createaerophysicsgantry.data.CAPGDatagen;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlockEntityTypes;
import com.blorbee.createaerophysicsgantry.registry.CAPGBlocks;
import com.blorbee.createaerophysicsgantry.registry.CAPGCreativeTab;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(CreateAeroPhysicsGantry.MOD_ID)
public class CreateAeroPhysicsGantry {
    public static final String MOD_ID = "createaerophysicsgantry";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
        .defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
        .setTooltipModifierFactory(item ->
            new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public CreateAeroPhysicsGantry(IEventBus modEventBus, ModContainer modContainer) {
        REGISTRATE.registerEventListeners(modEventBus);

        CAPGBlocks.register();
        CAPGBlockEntityTypes.register();
        CAPGCreativeTab.registerAeronauticsSections();

        modEventBus.addListener(EventPriority.HIGHEST, CAPGDatagen::gatherData);

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.getSpec());

        LOGGER.info("Create Aeronautics Physics Gantry started");
    }

    public static Component lang(String path, Object... args) {
        return Component.translatable(MOD_ID + "." + path, args);
    }

    public static ResourceLocation path(final String path) {
        return ResourceLocation.tryBuild(MOD_ID, path);
    }
}
