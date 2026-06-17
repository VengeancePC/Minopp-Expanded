package cn.zbx1425.minopp.forge;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.MinoClient;
import cn.zbx1425.minopp.MinoCommand;
import cn.zbx1425.minopp.entity.EntityAutoPlayer;
import cn.zbx1425.minopp.forge.compat.touhou_little_maid.TouhouLittleMaidCompat;
import cn.zbx1425.minopp.forge.config.ForgeClientConfig;
import cn.zbx1425.minopp.platform.forge.CompatPacket;
import cn.zbx1425.minopp.platform.forge.CompatPacketRegistry;
import cn.zbx1425.minopp.platform.forge.RegistriesWrapperImpl;
import cn.zbx1425.minopp.platform.forge.SchedulerTask;
import cn.zbx1425.minopp.game.TaskScheduler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;


@Mod(Mino.MOD_ID)
public final class MinoForge {

    private static final RegistriesWrapperImpl registries = new RegistriesWrapperImpl();
    public static final CompatPacketRegistry PACKET_REGISTRY = new CompatPacketRegistry();

    public static final SimpleChannel NETWORK = NetworkRegistry.ChannelBuilder.named(Mino.id("network"))
            .networkProtocolVersion(() -> "3.0.10+")
            .serverAcceptedVersions(ignored -> true)
            .clientAcceptedVersions(ignored -> true)
            .simpleChannel();

    public MinoForge() {
        Mino.init(registries);

        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        registries.registerAllDeferred(eventBus);
        eventBus.register(RegistriesWrapperImpl.RegisterCreativeTabs.class);
        eventBus.register(ModEventBusListener.class);

        MinecraftForge.EVENT_BUS.register(ForgeEventBusListener.class);

        TaskScheduler.Holder.INSTANCE = new SchedulerTask();
        MinecraftForge.EVENT_BUS.register(SchedulerTask.class);

        NETWORK.registerMessage(0, CompatPacket.class, CompatPacket::encode, CompatPacket::decode, CompatPacket::handle);

        // Touhou Little Maid compat
        TouhouLittleMaidCompat.init(eventBus);

        if (FMLEnvironment.dist.isClient()) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeClientConfig.SPEC);
            MinoClient.init();
            eventBus.register(ClientProxy.ModEventBusListener.class);
            MinecraftForge.EVENT_BUS.register(ClientProxy.ForgeEventBusListener.class);
        }
    }

    public static class ModEventBusListener {
        @SubscribeEvent
        public static void newEntityAttributes(EntityAttributeCreationEvent event) {
            event.put(Mino.ENTITY_AUTO_PLAYER.get(), EntityAutoPlayer.createAttributes());
        }

        @SubscribeEvent
        public static void onConfigLoad(net.minecraftforge.fml.event.config.ModConfigEvent event) {
            if (event.getConfig().getType() == ModConfig.Type.CLIENT) {
            cn.zbx1425.minopp.config.ClientConfig.GUI_X_POSITION = ForgeClientConfig.GUI_X_POSITION.get();
            cn.zbx1425.minopp.config.ClientConfig.GUI_Y_POSITION = ForgeClientConfig.GUI_Y_POSITION.get();
            cn.zbx1425.minopp.config.ClientConfig.ENABLE_GAME_HISTORY_UI = ForgeClientConfig.ENABLE_GAME_HISTORY_UI.get();
            }
        }

        @SubscribeEvent
        public static void onConfigReload(net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) {
            if (event.getConfig().getType() == ModConfig.Type.CLIENT) {
            cn.zbx1425.minopp.config.ClientConfig.GUI_X_POSITION = ForgeClientConfig.GUI_X_POSITION.get();
            cn.zbx1425.minopp.config.ClientConfig.GUI_Y_POSITION = ForgeClientConfig.GUI_Y_POSITION.get();
            cn.zbx1425.minopp.config.ClientConfig.ENABLE_GAME_HISTORY_UI = ForgeClientConfig.ENABLE_GAME_HISTORY_UI.get();
    }
    }
}

    public static class ForgeEventBusListener {
        @SubscribeEvent
        public static void onRegisterCommands(final RegisterCommandsEvent event) {
            MinoCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onServerChatMessage(final ServerChatEvent event) {
            Mino.onServerChatMessage(event.getRawText(), event.getPlayer());
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onAttackEntity(final AttackEntityEvent event) {
            Mino.onPlayerAttackEntity(event.getTarget(), event.getEntity());
        }
    }
}

