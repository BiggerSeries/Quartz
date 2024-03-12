package net.roguelogix.quartz.testing.tests;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3ic;
import org.lwjgl.glfw.GLFW;

public final class Util {
    private static final Minecraft minecraft = Minecraft.getInstance();
    
    private Util() {
    }
    
    @OnModLoad
    public static void onModLoadFOV(){
        NeoForge.EVENT_BUS.register(Util.class);
    }
    
    @Nullable
    private static Vec3 savedPosition;
    private static float savedXRot;
    private static float savedYRot;
    private static int savedFOV;
    private static boolean savedHUDHidden;
    private static boolean savedFlying;
    private static boolean savedBob;
    private static ResourceKey<Level> savedLevelID;
    private static int windowWidth;
    private static int windowHeight;
    
    public static void savePlayerState() {
        assert minecraft.player != null;
        savedPosition = minecraft.player.position();
        savedXRot = minecraft.player.getXRot();
        savedYRot = minecraft.player.getYRot();
        savedFOV = minecraft.options.fov().get();
        savedHUDHidden = minecraft.options.hideGui;
        minecraft.options.cloudStatus().set(CloudStatus.OFF);
        savedFlying = minecraft.player.getAbilities().flying;
        savedLevelID = minecraft.player.level().dimension();
        savedBob = minecraft.options.bobView().get();
        windowWidth = minecraft.getWindow().getWidth();
        windowHeight = minecraft.getWindow().getHeight();
        minecraft.getWindow().updateVsync(false);
        minecraft.getWindow().setFramerateLimit(300);
        minecraft.options.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NEARBY);
    }
    
    public static void restorePlayerState() {
        assert minecraft.player != null;
        minecraft.options.fov().set(savedFOV);
        minecraft.options.hideGui = savedHUDHidden;
        minecraft.player.getAbilities().flying = savedFlying;
        minecraft.options.bobView().set(savedBob);
        
        var server = minecraft.getSingleplayerServer();
        assert server != null;
        final var serverPlayer = server.getPlayerList().getPlayer(minecraft.player.getUUID());
        assert serverPlayer != null;
        var level = server.getLevel(savedLevelID);
        assert level != null;
        assert savedPosition != null;
        serverPlayer.teleportTo(level, savedPosition.x, savedPosition.y, savedPosition.z, savedXRot, savedYRot);
        minecraft.player.setPos(savedPosition.x, savedPosition.y, savedPosition.z);
        minecraft.player.setXRot(savedXRot);
        minecraft.player.setXRot(savedYRot);
        setWindowSize(windowWidth, windowHeight);
        minecraft.getWindow().updateVsync(true);
    }
    
    public static void hideHUD() {
        minecraft.options.hideGui = true;
    }
    
    public static void disableBob() {
        minecraft.options.bobView().set(false);
    }
    
    public static void setFOV(int fov) {
        minecraft.options.fov().set(fov);
    }
    
    @SubscribeEvent
    public static void onFOVEvent(ViewportEvent.ComputeFov event){
        event.setFOV(minecraft.options.fov().get());
    }
    
    public static void setFlying() {
        assert minecraft.player != null;
        minecraft.player.getAbilities().flying = true;
    }
    
    public static void setWindowSize(int w, int h) {
        final var window = minecraft.getWindow().getWindow();
        GLFW.glfwRestoreWindow(window);
        GLFW.glfwSetWindowSize(window, w, h);
    }
    
//    public static void setEyePosAndLook(Vector3ic pos, Vector3ic look) {
//
//    }
    
    public static void setEyePos(Vector3ic pos) {
        setEyePos(pos.x(), pos.y(), pos.z());
    }
    
    public static void setEyePos(int x, int y, int z) {
//        minecraft.gameRenderer.getMainCamera().setPosition(x + 0.5, y + 0.5, z + 0.5);
        minecraft.player.absMoveTo(x + 0.5, y - minecraft.player.getEyeHeight() + 0.5, z + 0.5);
    }
    
    public static void setLookCenter(int x, int y, int z) {
        final var camera = minecraft.gameRenderer.getMainCamera();
        final var cameraPosition = camera.getPosition();
        final var lookPosition = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        
        double diffX = lookPosition.x - cameraPosition.x;
        double diffY = lookPosition.y - cameraPosition.y;
        double diffZ = lookPosition.z - cameraPosition.z;
        double XZDiffDot = Math.sqrt(diffX * diffX + diffZ * diffZ);
        final var xRot = Mth.wrapDegrees((float)(-(Mth.atan2(diffY, XZDiffDot) * 180.0F / (float)Math.PI)));
        final var yRot = Mth.wrapDegrees((float)(Mth.atan2(diffZ, diffX) * 180.0F / (float)Math.PI) - 90.0F);
//        minecraft.gameRenderer.getMainCamera().setRotation(xRot, yRot);
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(x + 0.5, y + 0.5, z + 0.5));
    }
    
    public static void teleportToInOverworld(int x, int y, int z) {
        var server = minecraft.getSingleplayerServer();
        assert server != null;
        assert minecraft.player != null;
        final var serverPlayer = server.getPlayerList().getPlayer(minecraft.player.getUUID());
        assert serverPlayer != null;
        var level = server.getLevel(Level.OVERWORLD);
        assert level != null;
        serverPlayer.teleportTo(level, x + 0.5, z + 0.5, z + 0.5, 0, 0);
    }
    
    public static void setBlock(int x, int y, int z, Block block) {
        setBlock(new BlockPos(x, y, z), block);
    }
    
    public static void setBlock(BlockPos pos, Block block) {
        setBlock(pos, block.defaultBlockState());
    }
    
    public static void setBlock(int x, int y, int z, BlockState blockState) {
        setBlock(new BlockPos(x, y, z), blockState);
    }
    
    public static void setBlock(BlockPos pos, BlockState blockState) {
        var player = minecraft.player;
        assert player != null;
        // normal flags and modified by player, makes double sure that the renderchunk will get updated ASAP
        player.level().setBlock(pos, blockState, 11);
    }
    
    public static void setVolume(BlockPos starting, BlockPos ending, Block block) {
        setVolume(starting, ending, block.defaultBlockState());
    }
    
    public static void setVolume(BlockPos starting, BlockPos ending, BlockState blockState) {
        final var mutable = starting.mutable();
        for (int i = starting.getX(); i <= ending.getX(); i++) {
            for (int j = starting.getY(); j < ending.getY(); j++) {
                for (int k = starting.getZ(); k < ending.getZ(); k++) {
                    mutable.set(i, j, k);
                    setBlock(mutable, blockState);
                }
            }
        }
    }
    
    public static NativeImage screenshot() {
        if (false) {
            Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), p_90917_ -> minecraft.execute(() -> minecraft.gui.getChat().addMessage(p_90917_)));
        }
        return Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
    }
    
    public static void sendChatMessage(String message) {
        assert minecraft.player != null;
        minecraft.player.sendSystemMessage(Component.literal(message));
        
    }
}
