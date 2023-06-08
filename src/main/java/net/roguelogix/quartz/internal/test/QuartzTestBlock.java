package net.roguelogix.quartz.internal.test;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.roguelogix.phosphophyllite.registry.CreativeTabBlock;
import net.roguelogix.phosphophyllite.registry.RegisterBlock;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class QuartzTestBlock extends Block implements EntityBlock {
    
    @CreativeTabBlock
    @RegisterBlock(name = "quartz_test_block",  tileEntityClass = QuartzTestBlockTile.class)
    public static final QuartzTestBlock INSTANCE = new QuartzTestBlock();
    
    public QuartzTestBlock() {
        super(Properties.of().noLootTable().destroyTime(3.0F).explosionResistance(3.0F));
    }
    
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return QuartzTestBlockTile.SUPPLIER.create(pPos, pState);
    }
}
