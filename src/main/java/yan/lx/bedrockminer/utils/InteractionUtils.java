package yan.lx.bedrockminer.utils;

import net.minecraft.block.BlockState;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.item.Item;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static yan.lx.bedrockminer.BedrockMiner.*;
import static yan.lx.bedrockminer.BedrockMiner.interactionManager;

public class InteractionUtils {
    private static @Nullable Consumer<BlockPos> beforeBlockDestroyPacket;

    public static void setBeforeBlockDestroyPacket(@Nullable Consumer<BlockPos> beforeBlockDestroyPacket) {
        InteractionUtils.beforeBlockDestroyPacket = beforeBlockDestroyPacket;
    }

    private static void onBeforeBlockDestroyPacket(BlockPos blockPos) {
        if (beforeBlockDestroyPacket != null) {
            beforeBlockDestroyPacket.accept(blockPos);
            beforeBlockDestroyPacket = null;
        }
    }

    public static boolean breakBlock(BlockPos pos) {
        onBeforeBlockDestroyPacket(pos);
        return interactionManager.breakBlock(pos);
    }

    public static boolean attackBlock(BlockPos pos, Direction direction) {
        if (player.isBlockBreakingRestricted(world, pos, gameMode)) {
            return false;
        } else if (!world.getWorldBorder().contains(pos)) {
            return false;
        } else {
            if (gameMode.isCreative()) {
                BlockState blockState = world.getBlockState(pos);
                mc.getTutorialManager().onBlockBreaking(world, pos, blockState, 1.0F);
                interactionManager.sendSequencedPacket(world, (sequence) -> {
                    breakBlock(pos);
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
            } else if (!interactionManager.breakingBlock || !interactionManager.isCurrentlyBreaking(pos)) {
                if (interactionManager.breakingBlock) {
                    networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, interactionManager.currentBreakingPos, direction));
                }
                BlockState blockState = world.getBlockState(pos);
                mc.getTutorialManager().onBlockBreaking(world, pos, blockState, 0.0F);
                interactionManager.sendSequencedPacket(world, (sequence) -> {
                    boolean bl = !blockState.isAir();
                    if (bl && interactionManager.currentBreakingProgress == 0.0F) {
                        blockState.onBlockBreakStart(world, pos, player);
                    }
                    if (bl && blockState.calcBlockBreakingDelta(player, player.getWorld(), pos) >= 1.0F) {
                        breakBlock(pos);
                    } else {
                        interactionManager.breakingBlock = true;
                        interactionManager.currentBreakingPos = pos;
                        interactionManager.selectedStack = player.getMainHandStack();
                        interactionManager.currentBreakingProgress = 0.0F;
                        interactionManager.blockBreakingSoundCooldown = 0.0F;
                        world.setBlockBreakingInfo(player.getId(), interactionManager.currentBreakingPos, interactionManager.getBlockBreakingProgress());
                    }
                    return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
            }
            return true;
        }
    }

    public static boolean attackBlock(BlockPos pos) {
        return attackBlock(pos, getClosestFace(pos));
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction) {
        interactionManager.syncSelectedSlot();
        if (gameMode.isCreative() && world.getWorldBorder().contains(pos)) {
            BlockState blockState = world.getBlockState(pos);
            mc.getTutorialManager().onBlockBreaking(world, pos, blockState, 1.0F);
            interactionManager.sendSequencedPacket(world, (sequence) -> {
                breakBlock(pos);
                return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
            });
            return true;
        } else if (interactionManager.isCurrentlyBreaking(pos)) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.isAir()) {
                interactionManager.breakingBlock = false;
                return false;
            } else {
                interactionManager.currentBreakingProgress += blockState.calcBlockBreakingDelta(player, player.getWorld(), pos);
                if (interactionManager.blockBreakingSoundCooldown % 4.0F == 0.0F) {
                    BlockSoundGroup blockSoundGroup = blockState.getSoundGroup();
                    mc.getSoundManager().play(new PositionedSoundInstance(blockSoundGroup.getHitSound(), SoundCategory.BLOCKS, (blockSoundGroup.getVolume() + 1.0F) / 8.0F, blockSoundGroup.getPitch() * 0.5F, SoundInstance.createRandom(), pos));
                }
                ++interactionManager.blockBreakingSoundCooldown;
                mc.getTutorialManager().onBlockBreaking(world, pos, blockState, MathHelper.clamp(interactionManager.currentBreakingProgress, 0.0F, 1.0F));
                if (interactionManager.currentBreakingProgress >= 1.0F) {
                    interactionManager.breakingBlock = false;
                    interactionManager.sendSequencedPacket(world, (sequence) -> {
                        breakBlock(pos);
                        return new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                    });
                    interactionManager.currentBreakingProgress = 0.0F;
                    interactionManager.blockBreakingSoundCooldown = 0.0F;
                }
                world.setBlockBreakingInfo(player.getId(), interactionManager.currentBreakingPos, interactionManager.getBlockBreakingProgress());
                return true;
            }
        } else {
            return interactionManager.attackBlock(pos, direction);
        }
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos) {
        return updateBlockBreakingProgress(pos, getClosestFace(pos));
    }

    public static void interactBlock(BlockPos blockPos) {
        var side = getClosestFace(blockPos);
        if (isBlockWithinReach(blockPos, side)) {
            var hitPos = blockPos.offset(side.getOpposite());
            var hitVec3d = hitPos.toCenterPos().offset(side, 0.5F);   // 选中面中心坐标
            var hitResult = new BlockHitResult(hitVec3d, side, blockPos, false);
            interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        }
    }

    public static ActionResult placement(BlockPos blockPos, Direction facing, @Nullable Item... items) {
        if (isBlockWithinReach(blockPos, facing)) {
            return ActionResult.FAIL;
        }
        if (items != null) {
            InventoryManagerUtils.switchToItem(items);
        }
        var hitPos = blockPos.offset(facing.getOpposite());
        var hitVec3d = hitPos.toCenterPos().offset(facing, 0.5F);   // 选中面中心坐标
        var hitResult = new BlockHitResult(hitVec3d, facing, blockPos, false);

        if (!world.getWorldBorder().contains(hitResult.getBlockPos())) {
            return ActionResult.FAIL;
        } else {
            var mutableObject = new MutableObject<ActionResult>();
            interactionManager.sendSequencedPacket(world, (sequence) -> {
                var yaw = switch (facing) {
                    case SOUTH -> 180F;
                    case EAST -> 90F;
                    case NORTH -> 0F;
                    case WEST -> -90F;
                    default -> player.getYaw();
                };
                var pitch = switch (facing) {
                    case UP -> 90F;
                    case DOWN -> -90F;
                    default -> 0F;
                };
                networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround()));
                mutableObject.setValue(interactionManager.interactBlockInternal(player, Hand.MAIN_HAND, hitResult));
                return new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, sequence);
            });
            return mutableObject.getValue();
        }
    }

    private static boolean isCurrentlyBreaking(BlockPos pos) {
        return pos.equals(interactionManager.currentBreakingPos);
    }

    public static Direction getClosestFace(BlockPos targetPos) {
        Vec3d playerPos = player.getEyePos();
        Vec3d targetCenterPos = targetPos.toCenterPos();
        Direction closestFace = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            double offsetX = direction.getOffsetX() * 0.5;
            double offsetY = direction.getOffsetY() * 0.5;
            double offsetZ = direction.getOffsetZ() * 0.5;
            Vec3d facePos = targetCenterPos.add(offsetX, offsetY, offsetZ);
            double distanceSquared = playerPos.squaredDistanceTo(facePos);
            // 更新最近的面
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestFace = direction;
            }
        }
        return closestFace;
    }

    public static boolean isBlockWithinReach(BlockPos targetPos) {
        return isBlockWithinReach(targetPos, getClosestFace(targetPos));
    }

    public static boolean isBlockWithinReach(BlockPos targetPos, Direction side) {
        return isBlockWithinReach(targetPos, side, 0);
    }

    public static boolean isBlockWithinReach(BlockPos targetPos, Direction side, double deltaReachDistance) {
        var reachDistance = getPlayerBlockInteractionRange() + deltaReachDistance;
        var playerPos = player.getEyePos();
        var targetCenterPos = targetPos.toCenterPos();
        // 定义面上的关键点（四个角 + 中心点）
        var facePoints = getFacePoints(targetCenterPos, side);
        // 遍历该面所有关键点，找到最短距离
        for (Vec3d point : facePoints) {
            var distanceSquared = playerPos.squaredDistanceTo(point);
            if (distanceSquared <= reachDistance * reachDistance) {
                return true;
            }
        }
        return false;
    }

    // 获取目标面上的多个关键点
    private static List<Vec3d> getFacePoints(Vec3d center, Direction side) {
        var points = new ArrayList<Vec3d>();
        var halfSize = 0.5; // 方块的一半边长
        // 获取偏移方向
        var offsetX = side.getOffsetX() * halfSize;
        var offsetY = side.getOffsetY() * halfSize;
        var offsetZ = side.getOffsetZ() * halfSize;
        // 面的中心点
        Vec3d faceCenter = center.add(offsetX, offsetY, offsetZ);
        points.add(faceCenter);
        // 面的四个角
        if (side.getAxis() == Direction.Axis.Y) { // 顶部/底部面
            points.add(faceCenter.add(halfSize, 0, halfSize));
            points.add(faceCenter.add(halfSize, 0, -halfSize));
            points.add(faceCenter.add(-halfSize, 0, halfSize));
            points.add(faceCenter.add(-halfSize, 0, -halfSize));
        } else if (side.getAxis() == Direction.Axis.X) { // 左/右面
            points.add(faceCenter.add(0, halfSize, halfSize));
            points.add(faceCenter.add(0, halfSize, -halfSize));
            points.add(faceCenter.add(0, -halfSize, halfSize));
            points.add(faceCenter.add(0, -halfSize, -halfSize));
        } else if (side.getAxis() == Direction.Axis.Z) { // 前/后面
            points.add(faceCenter.add(halfSize, halfSize, 0));
            points.add(faceCenter.add(halfSize, -halfSize, 0));
            points.add(faceCenter.add(-halfSize, halfSize, 0));
            points.add(faceCenter.add(-halfSize, -halfSize, 0));
        }
        return points;
    }

    public static double getPlayerBlockInteractionRange() {
        return player.getBlockInteractionRange();
    }
}
