package com.blorbee.createaerophysicsgantry.data;

import com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry;
import com.blorbee.createaerophysicsgantry.ponder.CAPGPonderPlugin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.simibubi.create.foundation.utility.FilesHelper;
import com.tterrag.registrate.providers.ProviderType;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Map;

import static com.blorbee.createaerophysicsgantry.CreateAeroPhysicsGantry.REGISTRATE;

public class CAPGDatagen {
    public static void gatherData(GatherDataEvent event) {
        if (!event.getMods().contains(CreateAeroPhysicsGantry.MOD_ID))
            return;

        REGISTRATE.addDataGenerator(ProviderType.LANG, provider -> {
            JsonElement jsonElement = FilesHelper.loadJsonResource("assets/createaerophysicsgantry/lang/default/default.json");
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                provider.add(entry.getKey(), entry.getValue().getAsString());
            }

            PonderIndex.addPlugin(new CAPGPonderPlugin());
            PonderIndex.getLangAccess().provideLang(CreateAeroPhysicsGantry.MOD_ID, provider::add);
        });
    }
}
