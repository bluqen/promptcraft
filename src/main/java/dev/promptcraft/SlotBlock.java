package dev.promptcraft;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A pre-registered block driven by the creation in its slot. Block states cache light and hardness
 * at startup, so light lives in a state property set on placement and hardness is applied by hand.
 */
public class SlotBlock extends Block {
	public static final IntegerProperty LIGHT = BlockStateProperties.LEVEL;
	private final String slot;

	public SlotBlock(String slot, Properties properties) {
		super(properties);
		this.slot = slot;
		registerDefaultState(stateDefinition.any().setValue(LIGHT, 0));
	}

	public ModDef def() {
		return Defs.get(slot);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIGHT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		ModDef d = def();
		return defaultBlockState().setValue(LIGHT, d == null ? 0 : Mth.clamp(d.props.light, 0, 15));
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		ModDef d = def();
		float hardness = d == null ? 1f : d.props.hardness;
		if (hardness < 0) return 0;
		if (hardness == 0) return 1;
		return player.getDestroySpeed(state) / hardness / 30f;
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		ModDef d = def();
		if (d != null && !level.isClientSide) {
			Behaviors.launch(entity, d);
			if (level.getGameTime() % 10 == 0) Behaviors.run(d.on("on_step"), entity.createCommandSourceStack());
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
		ModDef d = def();
		if (d != null && d.nativeProps.bouncy && !entity.isSuppressingBounce()) {
			entity.causeFallDamage(fallDistance, 0f, level.damageSources().fall());
		} else {
			super.fallOn(level, state, pos, entity, fallDistance);
		}
	}

	@Override
	public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
		ModDef d = def();
		Vec3 v = entity.getDeltaMovement();
		if (d != null && d.nativeProps.bouncy && !entity.isSuppressingBounce() && v.y < 0) {
			entity.setDeltaMovement(v.x, -v.y * 0.95, v.z);
		} else {
			super.updateEntityAfterFallOn(level, entity);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		ModDef d = def();
		if (d == null || d.on("on_right_click").isEmpty()) return InteractionResult.PASS;
		if (!level.isClientSide) Behaviors.run(d.on("on_right_click"), player.createCommandSourceStack().withPosition(Vec3.atCenterOf(pos)));
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		ModDef d = def();
		if (d != null && !level.isClientSide) Behaviors.run(d.on("on_break"), player.createCommandSourceStack().withPosition(Vec3.atCenterOf(pos)));
		return super.playerWillDestroy(level, pos, state, player);
	}
}
