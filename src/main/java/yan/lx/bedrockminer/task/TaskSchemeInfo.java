package yan.lx.bedrockminer.task;

import net.minecraft.util.math.Direction;

public class TaskSchemeInfo {
    public final Direction direction;
    public final TaskBlockInfo piston;
    public final TaskBlockInfo redstoneTorch;
    public final TaskBlockInfo slimeBlock;

    public TaskSchemeInfo(Direction direction, TaskBlockInfo piston, TaskBlockInfo redstoneTorch, TaskBlockInfo slimeBlock) {
        this.direction = direction;
        this.piston = piston;
        this.redstoneTorch = redstoneTorch;
        this.slimeBlock = slimeBlock;
    }
}
