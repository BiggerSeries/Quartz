package net.roguelogix.quartz;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.roguelogix.phosphophyllite.Phosphophyllite;
import net.roguelogix.phosphophyllite.registry.Registry;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.internal.QuartzCore;
import org.joml.Vector3ic;

import java.util.function.Consumer;

@Mod(Quartz.modid)
@NonnullDefault
public final class Quartz {
    public static final String modid = "quartz";
    
    public Quartz(){
        new Registry(ReferenceArrayList.of(new ResourceLocation(Phosphophyllite.modid, "creative_tab")), new ReferenceArrayList<>());
    }
    
    public static IEventBus EVENT_BUS = BusBuilder.builder().allowPerPhasePost().build();
    
    public static Mesh createStaticMesh(BlockState blockState) {
        return createStaticMesh(builder -> {
            final var minecraft = Minecraft.getInstance();
            final var blockColors = minecraft.getBlockColors();
            final var renderer = minecraft.getBlockRenderer();
            final var modelRenderer = renderer.getModelRenderer();
            
            final var blockModel = renderer.getBlockModel(blockState);
            final var renderTypes = blockModel.getRenderTypes(blockState, RandomSource.create(42), ModelData.EMPTY);
            
            final int color = blockColors.getColor(blockState, null, null, 0);
            final float r = (float) (color >> 16 & 255) / 255.0F;
            final float g = (float) (color >> 8 & 255) / 255.0F;
            final float b = (float) (color & 255) / 255.0F;
            
            final var topOfStack = builder.matrixStack().last();
            final var bufferSource = builder.bufferSource();
            for (final var rt : renderTypes) {
                modelRenderer.renderModel(topOfStack, bufferSource.getBuffer(rt), blockState, blockModel, r, g, b, 0, 0, ModelData.EMPTY, rt);
            }
        });
    }
    
    public static Mesh createStaticMesh(ResourceLocation modelLocation) {
        QuartzCore.registerModel(modelLocation);
        return createStaticMesh(builder -> {
            final var minecraft = Minecraft.getInstance();
            final var renderer = minecraft.getBlockRenderer();
            final var modelRenderer = renderer.getModelRenderer();
            
            final var model = minecraft.getModelManager().getModel(modelLocation);
            
            // yes this is a nonnull thing, this throwing isn't something i care about
            final var renderTypes = model.getRenderTypes(Blocks.STONE.defaultBlockState(), RandomSource.create(42), ModelData.EMPTY);
            
            final var topOfStack = builder.matrixStack().last();
            final var bufferSource = builder.bufferSource();
            for (final var rt : renderTypes) {
                modelRenderer.renderModel(topOfStack, bufferSource.getBuffer(rt), null, model, 1, 1, 1, 0, 0, ModelData.EMPTY, rt);
            }
        });
    }
    
    public static Mesh createStaticMesh(Consumer<Mesh.Builder> buildFunc) {
        return QuartzCore.INSTANCE.meshManager.createMesh(buildFunc);
    }
    
    public static DrawBatch getDrawBatchForBlock(BlockPos blockPos) {
        return getDrawBatcherForSection(SectionPos.asLong(blockPos));
    }
    
    public static DrawBatch getDrawBatcherForBlock(Vector3ic blockPos) {
        return getDrawBatcherForSection(SectionPos.asLong(blockPos.x() >> 4, blockPos.y() >> 4, blockPos.z() >> 4));
    }
    
    public static DrawBatch getDrawBatcherForSection(long sectionPos) {
        return QuartzCore.INSTANCE.getWorldEngine().getBatcherForSection(sectionPos);
    }
    
    public static DrawBatch getDrawBatcherForAABB(AABB aabb) {
        return QuartzCore.INSTANCE.getWorldEngine().getBatcherForAABB(aabb);
    }
    
    public static DrawBatch getEntityBatcher() {
        return QuartzCore.INSTANCE.getEntityBatcher();
    }
}
