package com.example.blackholesky;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Control del tiempo:
 *   /tiempocielo acelerar <2-200>   -> el tiempo pasa X veces mas rapido
 *   /tiempocielo normal             -> velocidad normal
 *   /tiempocielo dia                -> fija el dia (mediodia)
 *   /tiempocielo noche              -> fija la noche (medianoche)
 */
@Mod.EventBusSubscriber(modid = BlackHoleSky.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEvents {
    private enum Mode { NORMAL, ACCEL, FIXED }

    private static Mode mode = Mode.NORMAL;
    private static int speed = 1;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tiempocielo")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("acelerar")
                        .then(Commands.argument("veces", IntegerArgumentType.integer(2, 200))
                                .executes(c -> accelerate(c.getSource(), IntegerArgumentType.getInteger(c, "veces")))))
                .then(Commands.literal("normal").executes(c -> normal(c.getSource())))
                .then(Commands.literal("dia").executes(c -> fix(c.getSource(), 6000L, "DIA fijo")))
                .then(Commands.literal("noche").executes(c -> fix(c.getSource(), 18000L, "NOCHE fija"))));
    }

    private static void daylightCycle(MinecraftServer server, boolean value) {
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(value, server);
    }

    private static int accelerate(CommandSourceStack src, int times) {
        mode = Mode.ACCEL;
        speed = times;
        daylightCycle(src.getServer(), true);
        src.sendSuccess(() -> Component.literal("Tiempo acelerado x" + times), true);
        return 1;
    }

    private static int normal(CommandSourceStack src) {
        mode = Mode.NORMAL;
        speed = 1;
        daylightCycle(src.getServer(), true);
        src.sendSuccess(() -> Component.literal("Tiempo normal"), true);
        return 1;
    }

    private static int fix(CommandSourceStack src, long time, String label) {
        mode = Mode.FIXED;
        speed = 1;
        MinecraftServer server = src.getServer();
        daylightCycle(server, false);
        ServerLevel ow = server.overworld();
        ow.setDayTime(time);
        server.getPlayerList().broadcastAll(new ClientboundSetTimePacket(ow.getGameTime(), time, false));
        src.sendSuccess(() -> Component.literal("Tiempo: " + label), true);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mode != Mode.ACCEL) return;
        MinecraftServer server = event.getServer();
        ServerLevel ow = server.overworld();
        long t = ow.getDayTime() + (speed - 1);
        ow.setDayTime(t);
        // Sincroniza cada tick para que no se vea a tirones en el cliente
        server.getPlayerList().broadcastAll(new ClientboundSetTimePacket(ow.getGameTime(), t, true));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        mode = Mode.NORMAL;
        speed = 1;
    }
}
