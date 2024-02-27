package net.roguelogix.quartz.testing;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.internal.QuartzCore;
import net.roguelogix.quartz.internal.QuartzInternalEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.roguelogix.quartz.testing.QuartzTesting.AUTOMATED;
import static net.roguelogix.quartz.testing.tests.Util.*;

public class QuartzTestRegistry {
    
    @OnModLoad
    public static void onModLoad() {
        NeoForge.EVENT_BUS.addListener(QuartzTestRegistry::registerCommands);
        Quartz.EVENT_BUS.addListener(QuartzTestRegistry::onTestingStatusEvent);
        registerTests();
    }
    
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("quartz").then(Commands.literal("recreate_tests").executes(ctx -> {
            registerTests();
            sendChatMessage("Tests recreated " + testRegistry.size());
            return Command.SINGLE_SUCCESS;
        })));
        event.getDispatcher().register(Commands.literal("quartz").then(Commands.literal("run_all_tests").executes(ctx -> {
            runAllTests();
            return Command.SINGLE_SUCCESS;
        })));
        event.getDispatcher().register(Commands.literal("quartz").then(Commands.literal("run_test").then(Commands.argument("test_name", StringArgumentType.string()).executes(ctx -> {
            runSpecificTest(ctx.getArgument("test_name", String.class));
            return Command.SINGLE_SUCCESS;
        }))));
    }
    
    public static void registerTests() {
        testRegistry.clear();
        Quartz.EVENT_BUS.post(new QuartzInternalEvent.CreateTests());
        QuartzCore.resourcesReloaded();
    }
    
    public static void runAllTests() {
        Minecraft.getInstance().player.sendSystemMessage(Component.literal("Quartz running all tests"));
        QuartzTesting.runTests(testRegistry.values());
    }
    
    public static void runSpecificTest(String testName) {
        @Nullable final var test = testRegistry.get(testName.intern());
        if (test == null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Quartz test " + testName + " not found"));
        } else {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("Quartz running test " + testName));
            QuartzTesting.runTests(List.of(test));
        }
    }
    
    private static void onTestingStatusEvent(QuartzInternalEvent.TestingStatus testingStatus) {
        if (testingStatus.running) {
            failedTests.clear();
            savePlayerState();
            System.out.println("##teamcity[testSuiteStarted name='Quartz']");
        } else {
            System.out.println("##teamcity[testSuiteFinished name='Quartz']");
            restorePlayerState();
            sendChatMessage(failedTests.size() + " tests failed");
            for (QuartzTest failedTest : failedTests) {
                sendChatMessage(failedTest.id);
            }
        }
    }
    
    private static final Reference2ReferenceMap<String, QuartzTest> testRegistry = new Reference2ReferenceOpenHashMap<>();
    private static final ReferenceArrayList<QuartzTest> failedTests = new ReferenceArrayList<>();
    
    public static void registerTest(QuartzTest test, boolean runnable) {
        testRegistry.put(test.id, test);
    }
    
    public static void testStarted(QuartzTest test) {
        sendChatMessage("Test " + test.id + " started");
        if (AUTOMATED) {
            System.out.println("##teamcity[testStarted name='" + test.id + "']");
        }
    }
    
    public static void testCompleted(QuartzTest test) {
        if (!test.passed()) {
            failedTests.add(test);
            if (AUTOMATED) {
                System.out.println("##teamcity[testFailed name='" + test.id + "' message='']");
            }
        }
        sendChatMessage("Test " + test.id + " " + (test.passed() ? "passed" : "failed"));
        if (AUTOMATED) {
            System.out.println("##teamcity[testFinished name='" + test.id + "']");
        }
    }
    
    public static void fatalTestFailure(String message) {
        Minecraft.getInstance().emergencySaveAndCrash(new CrashReport("Quartz testing caught fatal failure", new IllegalStateException(message)));
    }
}
