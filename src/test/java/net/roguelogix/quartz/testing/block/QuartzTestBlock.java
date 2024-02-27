package net.roguelogix.quartz.testing.block;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.roguelogix.phosphophyllite.registry.CreativeTabBlock;
import net.roguelogix.phosphophyllite.registry.RegisterBlock;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class QuartzTestBlock extends Block implements EntityBlock {
    
    @CreativeTabBlock
    @RegisterBlock(name = "quartz_test_runner_block", tileEntityClass = QuartzTestBlockTile.class)
    public static final QuartzTestBlock INSTANCE = new QuartzTestBlock();
    
    public QuartzTestBlock() {
        super(Properties.of().noLootTable().destroyTime(3.0F).explosionResistance(3.0F));
    }
    
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return QuartzTestBlockTile.SUPPLIER.create(pPos, pState);
    }
    
    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player player, InteractionHand handIn, BlockHitResult hit) {
        final var item = player.getMainHandItem().getItem();
        final var te = worldIn.getBlockEntity(pos);
        if (te instanceof QuartzTestBlockTile tile && item instanceof BlockItem blockItem) {
            tile.setBlock(blockItem.getBlock());
            return InteractionResult.SUCCESS;
        }
        return super.use(state, worldIn, pos, player, handIn, hit);
    }
}
