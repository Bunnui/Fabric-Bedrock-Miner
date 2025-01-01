package yan.lx.bedrockminer.task;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record TaskBlockInfo(BlockPos pos, Direction facing) {
    public boolean isNeedModifyPlayerLook() {
        return facing.getAxis().isHorizontal();
    }
}
