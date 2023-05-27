package net.roguelogix.quartz.internal.test;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.roguelogix.phosphophyllite.modular.tile.PhosphophylliteTile;
import net.roguelogix.phosphophyllite.registry.RegisterTile;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.Quartz;
import net.roguelogix.quartz.QuartzEvent;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class QuartzTestBlockTile extends PhosphophylliteTile {
    
    @RegisterTile("quartz_test_block")
    public static final BlockEntityType.BlockEntitySupplier<QuartzTestBlockTile> SUPPLIER = new RegisterTile.Producer<>(QuartzTestBlockTile::new);
    
    public QuartzTestBlockTile(BlockEntityType<?> TYPE, BlockPos pWorldPosition, BlockState pBlockState) {
        super(TYPE, pWorldPosition, pBlockState);
    }
    
    static {
        Quartz.EVENT_BUS.addListener(QuartzTestBlockTile::onQuartzStartup);
    }

    private static Mesh mesh;

    private static void onQuartzStartup(QuartzEvent.Startup quartzStartup) {
        mesh = Quartz.createStaticMesh(Blocks.STONE.defaultBlockState());
    }

    private DrawBatch.Instance instance = null;

    @Override
    public void onAdded() {
        assert level != null;
        if (!level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 0);
            return;
        }
        if (mesh != null && level.isClientSide()) {
            final var modelPos = new Vector3i(getBlockPos().getX(), getBlockPos().getY() + 1, getBlockPos().getZ());
            final var batcher = Quartz.getDrawBatcherForBlock(modelPos);
            final var initialValueMatrix = new Matrix4f();
            initialValueMatrix.scale(0.5f);
            initialValueMatrix.translate(0.5f, 0.5f, 0.5f);
            final var quartzMatrix = batcher.createDynamicMatrix(initialValueMatrix, (matrix, nanoSinceLastFrame, partialTicks, playerBlock, playerPartialBlock) -> {
                final var rotation = nanoSinceLastFrame / 1_000_000_000f;
                matrix.translate(0.5f, 0.5f, 0.5f);
                matrix.rotate(rotation, new Vector3f(1, 1, 1).normalize());
                matrix.translate(-0.5f, -0.5f, -0.5f);
            });
            instance = batcher.createInstance(modelPos, mesh, quartzMatrix, null, null);
        }
    }

    @Override
    public void onRemoved(boolean chunkUnload) {
        assert level != null;
        if (mesh != null && level.isClientSide()) {
            instance.delete();
            instance = null;
        }
    }
}
