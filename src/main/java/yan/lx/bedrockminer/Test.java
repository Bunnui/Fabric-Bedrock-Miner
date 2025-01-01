package yan.lx.bedrockminer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import yan.lx.bedrockminer.command.argument.BlockPosArgumentType;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static yan.lx.bedrockminer.BedrockMiner.crosshairTarget;
import static yan.lx.bedrockminer.BedrockMiner.world;

public class Test {

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(literal("test").executes(Test::executes)
                .then(argument("blockPos", BlockPosArgumentType.blockPos()).executes(Test::executesBlockPos)));
    }

    public static int executes(CommandContext<FabricClientCommandSource> context) {
        // 获取玩家瞄准的方块
        if (crosshairTarget != null && crosshairTarget.getType() == HitResult.Type.BLOCK) {
            var blockHitResult = (BlockHitResult) crosshairTarget;
            var blockPos = blockHitResult.getBlockPos();
            var blockState = world.getBlockState(blockPos);
            var block = blockState.getBlock();
        }
        return 0;
    }

    private static int executesBlockPos(CommandContext<FabricClientCommandSource> context) {
        var blockPos = BlockPosArgumentType.getBlockPos(context, "blockPos");
        return 0;
    }


}
