package net.roguelogix.quartz.testing;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.phosphophyllite.threading.Queues;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.QuartzInternalEvent;

import java.util.Optional;
import java.util.function.Function;

public class AutomaticTesting {
    @OnModLoad
    private static void onModLoad() {
        if (!QuartzTestingConfig.INSTANCE.AutoRun) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(AutomaticTesting::onStartupEvent);
        NeoForge.EVENT_BUS.addListener(AutomaticTesting::onLogin);
        Quartz.EVENT_BUS.addListener(EventPriority.LOWEST, AutomaticTesting::onTestingStatus);
    }
    
    private static boolean recursing = false;
    
    private static void onStartupEvent(ScreenEvent.Opening event) {
        if (recursing) {
            return;
        }
        recursing = true;
        final var gameRules = new GameRules();
        gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(false, null);
        gameRules.getRule(GameRules.RULE_DOBLOCKDROPS).set(false, null);
        gameRules.getRule(GameRules.RULE_DO_VINES_SPREAD).set(false, null);
        
        final var levelSettings = new LevelSettings("QuartzAutomaticTesting", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, gameRules, WorldDataConfiguration.DEFAULT);
        
        Function<RegistryAccess, WorldDimensions> dimensionFunc = registryAccess -> {
            
            var generatorSettings = JsonParser.parseString("""
                    {
                      "biome": "minecraft:the_void",
                      "features": true,
                      "lakes": false,
                      "layers": [
                        {
                          "block": "minecraft:air",
                          "height": 1
                        }
                      ],
                      "structure_overrides": []
                    }
                    """);
            RegistryOps<JsonElement> registryops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
            Optional<FlatLevelGeneratorSettings> optional = FlatLevelGeneratorSettings.CODEC
                    .parse(new Dynamic<>(registryops, generatorSettings))
                    .resultOrPartial(QuartzCore.LOGGER::error);
            
            var worldDimensions = WorldPresets.createNormalWorldDimensions(registryAccess);
            return worldDimensions.replaceOverworldGenerator(registryAccess, new FlatLevelSource(optional.get()));
        };
        
        Minecraft.getInstance().createWorldOpenFlows().createFreshLevel("QuartzAutomaticTesting", levelSettings, WorldOptions.defaultWithRandomSeed(), dimensionFunc, new GenericDirtMessageScreen(Component.translatable("selectWorld.data_read")));
    }
    
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Queues.offThread.enqueueUntracked(() -> {
            System.out.println("Testing starting in 3 seconds");
            // wait a little bit to start the tests, make sure everything has loaded in fully
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Queues.clientThread.enqueue(QuartzTestRegistry::runAllTests);
        });
    }
    
    public static void onTestingStatus(QuartzInternalEvent.TestingStatus event) {
        if (!event.running) {
            System.out.println("Testing completed, goodbye");
            System.exit(0);
        }
    }
}
