package yan.lx.bedrockminer.task;

import com.google.common.collect.Queues;
import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import yan.lx.bedrockminer.Debug;
import yan.lx.bedrockminer.config.Config;
import yan.lx.bedrockminer.utils.InteractionUtils;
import yan.lx.bedrockminer.utils.InventoryManagerUtils;

import java.util.Queue;

import static yan.lx.bedrockminer.BedrockMiner.player;

public class TaskHandler {
    public final ClientWorld world;
    public final Block block;
    public final BlockPos pos;

    private TaskState state;
    private @Nullable TaskState nextState;
    public @Nullable Direction direction;
    public @Nullable TaskBlockInfo piston;
    public @Nullable TaskBlockInfo redstoneTorch;
    public @Nullable TaskBlockInfo slimeBlock;
    public final Queue<BlockPos> blockItemRecycleList;
    public boolean executeModify;
    private int ticks;
    private int ticksTotalMax;
    private int ticksTimeoutMax;
    private int tickWaitMax;
    public int retryCount;
    public int retryMax;
    public boolean executed;
    public boolean timeout;

    public TaskHandler(ClientWorld world, Block block, BlockPos pos) {
        this.pos = pos;
        this.debug("[构造函数] 开始\r\n");
        this.world = world;
        this.block = block;
        this.blockItemRecycleList = Queues.newConcurrentLinkedQueue();
        this.onInitialize(false);
        this.debug("[构造函数] 结束\r\n");
    }

    private void onInitialize(boolean reset) {
        this.ticks = 0;
        this.tickWaitMax = 0;
        this.ticksTotalMax = Config.INSTANCE.taskTicksTotalMax;
        this.ticksTimeoutMax = Config.INSTANCE.taskTicksTimeoutMax;
        this.retryMax = Config.INSTANCE.taskRetryMax;
        if (!reset) this.retryCount = 0;
        this.blockItemRecycleList.clear();
        this.executed = false;
        this.timeout = false;
        this.state = TaskState.WAIT_GAME_UPDATE;
        this.nextState = null;
        this.tick(true);
    }

    private void onWaitGameUpdate() {

    }

    private void onWaitCustomUpdate() {
        if (--this.tickWaitMax <= 0) {
            this.state = this.nextState == null ? TaskState.WAIT_GAME_UPDATE : this.nextState;
            this.tickWaitMax = 0;
            this.debug("等待已结束, 状态设置为: %s", this.state);
            this.tick(true);    // 由于设置等待自定义更新占用了一个TICK, 所以直接内部调用TICK
        } else {
            ++this.ticksTotalMax;
            ++this.ticksTimeoutMax;
            debug("剩余等待TICK: %s", tickWaitMax);
        }
    }

    private void onPlace() {
    }

    private void onExecute() {
    }

    private void onBlockItemRecycle() {
        if (!this.blockItemRecycleList.isEmpty()) {
            var blockPos = this.blockItemRecycleList.peek();
            var blockState = this.world.getBlockState(blockPos);
            // 如果目标方块为基岩或者无法破坏的方块则直接移除
            if (blockState.getHardness(this.world, blockPos) < 0 || blockState.isReplaceable()) {
                this.blockItemRecycleList.remove(blockPos);
                return;
            } else {
                // 切换到合适的工具
                if (blockState.calcBlockBreakingDelta(player, this.world, blockPos) < 1F) {
                    InventoryManagerUtils.autoSwitch(blockState);
                }
                // 开始破坏方块
                InteractionUtils.updateBlockBreakingProgress(this.pos);
            }
            // 如果方块已经被破坏则移除
            if (blockState.isReplaceable()) {
                this.blockItemRecycleList.remove(blockPos);
                return;
            }
            if (!this.blockItemRecycleList.isEmpty()) {
                return;
            }
        }
        // 物品回收完成后, 尝试重新执行
        if (retryCount < retryMax) {
            this.retryCount++;
            this.state = TaskState.INITIALIZE;
        } else {
            this.state = TaskState.COMPLETE;
        }
    }


    private void tick(boolean internalCallbacks) {
        if (internalCallbacks) {
            debug("内部调用开始");
        } else {
            debug("——> 开始");
        }
        if (this.state == TaskState.COMPLETE) {
            return;
        }
        if (this.ticks >= this.ticksTotalMax) {
            this.state = TaskState.COMPLETE;
        }
        if (!this.timeout && this.ticks >= this.ticksTimeoutMax) {
            this.timeout = true;
            this.state = TaskState.TIMEOUT;
        }
        switch (this.state) {
            case INITIALIZE -> this.onInitialize(true);
            case WAIT_GAME_UPDATE -> this.onWaitGameUpdate();
            case WAIT_CUSTOM_UPDATE -> this.onWaitCustomUpdate();
            case PLACE -> this.onPlace();
            case EXECUTE -> this.onExecute();
            case TIMEOUT -> {
                this.debug("任务超时");
                this.state = TaskState.BLOCK_ITEM_RECYCLE;
                this.tick(true);
            }
            case FAIL -> {
                this.debug("任务失败");
                this.state = TaskState.BLOCK_ITEM_RECYCLE;
                this.tick(true);
            }
            case BLOCK_ITEM_RECYCLE -> this.onBlockItemRecycle();
            case COMPLETE -> debug("任务已结束！！！");
        }

        if (internalCallbacks) {
            debug("内部调用结束");
        } else {
            debug("——> 结束\r\n");
            ++ticks;
        }
    }

    public void tick() {
        this.tick(false);
    }

    private void addBlockItemRecycle(BlockPos pos) {
        if (!this.blockItemRecycleList.contains(pos)) {
            this.blockItemRecycleList.add(pos);
        }
    }

    public boolean isComplete() {
        return state == TaskState.COMPLETE || ticks >= ticksTotalMax;
    }

    private void setWait(@Nullable TaskState nextState) {
        this.setWait(nextState, 1);
    }

    private void setWait(@Nullable TaskState nextState, int tickWaitMax) {
        this.nextState = nextState;
        this.tickWaitMax = Math.max(tickWaitMax, 1);
        this.state = TaskState.WAIT_CUSTOM_UPDATE;
    }

    private void setModifyLook(TaskBlockInfo blockInfo) {
        if (blockInfo != null) {
            setModifyLook(blockInfo.facing());
        }
    }

    private void setModifyLook(Direction facing) {
        TaskPlayerLookManager.set(facing, this);
    }

    private void resetModifyLook() {
        TaskPlayerLookManager.reset();
    }

    private void debug(String var1, Object... var2) {
        Debug.write("[{}] [{}] [{}] [{}] {}",
                this.retryCount,
                this.ticks,
                this.pos.toShortString(),
                this.state,
                String.format(var1, var2));
    }

    private void debugUpdateStates(String var1, Object... var2) {
        Debug.write("[{}] [{}] [{}] [{}] [状态更新] {}",
                this.retryCount,
                this.ticks,
                this.pos.toShortString(),
                this.state,
                String.format(var1, var2));
    }
}
