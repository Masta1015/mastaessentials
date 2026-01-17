package com.mastaessentials.events;

import com.mastaessentials.commands.HomeCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = "mastaessentials",
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class CommandRegistration {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        HomeCommand.register(event.getDispatcher());
    }
}

