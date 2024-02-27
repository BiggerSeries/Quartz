package net.roguelogix.quartz.testing.tests.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.roguelogix.phosphophyllite.registry.OnModLoad;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.internal.QuartzInternalEvent;
import net.roguelogix.quartz.testing.tests.Util;
import org.joml.Vector3i;

import javax.annotation.Nullable;

import static net.roguelogix.quartz.testing.QuartzTestRegistry.registerTest;


@NonnullDefault
public class BlockMimicryTest extends ScreenshotCompareTest {
    
    private Block block;
    private Mesh mesh;
    @Nullable
    private DrawBatch.Instance instance;
    
    public BlockMimicryTest(Block block) {
        super("block_mimicry{" + BuiltInRegistries.BLOCK.getKey(block) + "}", new Vector3i(1, 1, 0));
        this.block = block;
        mesh = Quartz.createStaticMesh(block.defaultBlockState());
    }
    
    @Override
    protected void setupReference() {
        Util.setBlock(testPositionBlockPos, block);
    }
    
    @Override
    protected void cleanupReference() {
        Util.setBlock(testPositionBlockPos, Blocks.AIR);
    }
    
    @Override
    protected void setupQuartz() {
        instance = Quartz.getDrawBatcherForBlock(testPosition).createInstance(testPosition, mesh, null, null, null);
        if(instance == null){
            fail("Instance creation failed");
        }
    }
    
    @Override
    protected void cleanupQuartz() {
        if (instance != null) {
            instance.delete();
        }
    }
 
    @OnModLoad
    public static void onModLoad() {
        Quartz.EVENT_BUS.addListener(BlockMimicryTest::createTestsEvent);
    }
    
    public static void createTestsEvent(QuartzInternalEvent.CreateTests event){
        registerTest(new BlockMimicryTest(Blocks.STONE), true);
        registerTest(new BlockMimicryTest(Blocks.DIORITE), true);
        registerTest(new BlockMimicryTest(Blocks.POLISHED_DIORITE), true);
        registerTest(new BlockMimicryTest(Blocks.ANDESITE), true);
        registerTest(new BlockMimicryTest(Blocks.POLISHED_ANDESITE), true);
//        registerTest(new BlockMimicryTest(Blocks.GRASS_BLOCK), true); // biome coloration issues
        registerTest(new BlockMimicryTest(Blocks.DIRT), true);
        registerTest(new BlockMimicryTest(Blocks.COARSE_DIRT), true);
        registerTest(new BlockMimicryTest(Blocks.PODZOL), true);
        registerTest(new BlockMimicryTest(Blocks.COBBLESTONE), true);
        registerTest(new BlockMimicryTest(Blocks.OAK_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.SPRUCE_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.BIRCH_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.JUNGLE_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.ACACIA_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.CHERRY_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.DARK_OAK_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.MANGROVE_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.BAMBOO_PLANKS), true);
        registerTest(new BlockMimicryTest(Blocks.BAMBOO_MOSAIC), true);
        // ambient occlusion is force enabled by Quartz
        // saplings have it force disabled because their quads aren't axis aligned
//        registerTest(new BlockMimicryTest(Blocks.OAK_SAPLING), true);
//        registerTest(new BlockMimicryTest(Blocks.SPRUCE_SAPLING), true);
//        registerTest(new BlockMimicryTest(Blocks.BIRCH_SAPLING), true);
//        registerTest(new BlockMimicryTest(Blocks.JUNGLE_SAPLING), true);
//        registerTest(new BlockMimicryTest(Blocks.ACACIA_SAPLING), true);
//        registerTest(new BlockMimicryTest(Blocks.CHERRY_SAPLING), true);
//        registerTest(new BlockMimicryTest(Blocks.DARK_OAK_SAPLING), true);
//        registerTest(new BlockMimicryTest(Blocks.MANGROVE_PROPAGULE), true);
//        registerTest(new BlockMimicryTest(Blocks.BEDROCK), true);
        // there are some fall blocks here, they are ignored
        registerTest(new BlockMimicryTest(Blocks.GOLD_ORE), true);
        registerTest(new BlockMimicryTest(Blocks.DEEPSLATE_GOLD_ORE), true);
        registerTest(new BlockMimicryTest(Blocks.IRON_ORE), true);
        registerTest(new BlockMimicryTest(Blocks.DEEPSLATE_IRON_ORE), true);
        registerTest(new BlockMimicryTest(Blocks.COAL_ORE), true);
        registerTest(new BlockMimicryTest(Blocks.DEEPSLATE_COAL_ORE), true);
        registerTest(new BlockMimicryTest(Blocks.NETHER_GOLD_ORE), true);
        registerTest(new BlockMimicryTest(Blocks.OAK_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.SPRUCE_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.BIRCH_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.JUNGLE_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.ACACIA_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.CHERRY_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.DARK_OAK_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.MANGROVE_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.MANGROVE_ROOTS), true);
        registerTest(new BlockMimicryTest(Blocks.MUDDY_MANGROVE_ROOTS), true);
        registerTest(new BlockMimicryTest(Blocks.BAMBOO_BLOCK), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_SPRUCE_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_BIRCH_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_JUNGLE_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_ACACIA_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_CHERRY_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_DARK_OAK_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_OAK_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_MANGROVE_LOG), true);
        registerTest(new BlockMimicryTest(Blocks.STRIPPED_BAMBOO_BLOCK), true);
    }
}
