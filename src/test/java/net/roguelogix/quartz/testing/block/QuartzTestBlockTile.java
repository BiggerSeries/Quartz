package net.roguelogix.quartz.testing.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.roguelogix.phosphophyllite.modular.tile.PhosphophylliteTile;
import net.roguelogix.phosphophyllite.registry.RegisterTile;
import net.roguelogix.phosphophyllite.util.NonnullDefault;
import net.roguelogix.quartz.DrawBatch;
import net.roguelogix.quartz.Mesh;
import net.roguelogix.quartz.Quartz;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

@NonnullDefault
public class QuartzTestBlockTile extends PhosphophylliteTile {
    
    @RegisterTile("quartz_test_runner_block")
    public static final BlockEntityType.BlockEntitySupplier<QuartzTestBlockTile> SUPPLIER = new RegisterTile.Producer<>(QuartzTestBlockTile::new);
    
    public QuartzTestBlockTile(BlockEntityType<?> TYPE, BlockPos pWorldPosition, BlockState pBlockState) {
        super(TYPE, pWorldPosition, pBlockState);
    }
    
    @Nullable
    private Mesh mesh;
    @Nullable
    private DrawBatch.Instance instance = null;
    
    @Override
    public void onAdded() {
        assert level != null;
        if (!level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 0);
            return;
        }
        if (mesh != null && level.isClientSide()) {
            final var modelPos = new Vector3i(getBlockPos().getX(), getBlockPos().getY() + 2, getBlockPos().getZ());
            final var batcher = Quartz.getDrawBatcherForBlock(modelPos);
            instance = batcher.createInstance(modelPos, mesh, null, null, null);
        }
    }
    
    @Override
    public void onRemoved(boolean chunkUnload) {
        assert level != null;
        if (mesh != null && level.isClientSide()) {
            if (instance != null) {
                instance.delete();
            }
            instance = null;
        }
    }
    
    void setBlock(Block block) {
        assert level != null;
        if(level.isClientSide()) {
            onRemoved(false);
            mesh = Quartz.createStaticMesh(block.defaultBlockState());
            mesh.rebuild();
            onAdded();
        }
    }
}
