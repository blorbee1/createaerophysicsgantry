package com.blorbee.createaerophysicsgantry;

import com.blorbee.createaerophysicsgantry.ponder.CAPGPonderPlugin;
import com.blorbee.createaerophysicsgantry.registry.CAPGPartialModels;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CreateAeroPhysicsGantry.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CreateAeroPhysicsGantry.MOD_ID, value = Dist.CLIENT)
public class CreateAeroPhysicsGantryClient {
    public CreateAeroPhysicsGantryClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CreateAeroPhysicsGantry.LOGGER.info("Create Aero Physics Gantry client started");
        event.enqueueWork(CreateAeroPhysicsGantryClient::clientInit);
    }

    public static void clientInit() {
        CAPGPartialModels.register();
        PonderIndex.addPlugin(new CAPGPonderPlugin());
    }
}
