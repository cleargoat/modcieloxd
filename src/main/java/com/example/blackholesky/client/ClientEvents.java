package com.example.blackholesky.client;

import com.example.blackholesky.BlackHoleSky;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlackHoleSky.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    /** /shader cielo on  |  /shader cielo off */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("shader")
                .then(Commands.literal("cielo")
                        .then(Commands.literal("on").executes(ctx -> set(true)))
                        .then(Commands.literal("off").executes(ctx -> set(false)))));
    }

    private static int set(boolean on) {
        SkyToggle.setEnabled(on);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                    on ? "Shader de cielo: ACTIVADO" : "Shader de cielo: DESACTIVADO"));
        }
        return 1;
    }

    /** En el Overworld normal dibujamos nuestro cielo encima del cielo vanilla. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !SkyToggle.isEnabled()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        EventHorizonEffects.draw(level, event.getRenderTick(), event.getPartialTick(), event.getPoseStack());
    }
}
