package com.example.blackholesky.client;

import com.example.blackholesky.BlackHoleSky;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlackHoleSky.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(new ResourceLocation(BlackHoleSky.MODID, "event_horizon"), new EventHorizonEffects());
    }
}
