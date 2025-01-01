package yan.lx.bedrockminer.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import yan.lx.bedrockminer.command.CommandBase;
import yan.lx.bedrockminer.command.argument.BlockPosArgumentType;
import yan.lx.bedrockminer.task.TaskManager;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TaskCommand extends CommandBase {

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder, CommandRegistryAccess registryAccess) {
        builder
                .then(literal("add")
                        .then(argument("blockPos", BlockPosArgumentType.blockPos())
                                        .executes(this::add)
//                                .then(argument("blockPos2", BlockPosArgumentType.blockPos()).executes(this::selection))))
                        ))
                .then(literal("clear").executes(this::clear));
    }

    private int selection(CommandContext<FabricClientCommandSource> context) {
        var blockPos1 = BlockPosArgumentType.getBlockPos(context, "blockPos");
        var blockPos2 = BlockPosArgumentType.getBlockPos(context, "blockPos");
        return 0;
    }

    private Text getModeText(boolean mode, Direction... directions) {
        List<String> list = new ArrayList<>();
        for (Direction direction : directions) {
            list.add(direction.getName());
        }
        return Text.literal(String.format("%s: %s", String.join(", ", list), mode));
    }

    private int add(CommandContext<FabricClientCommandSource> context) {
        var blockPos = BlockPosArgumentType.getBlockPos(context, "blockPos");
        var client = MinecraftClient.getInstance();
        var world = client.world;
        if (world != null) {
            var blockState = world.getBlockState(blockPos);
            var block = blockState.getBlock();
            TaskManager.addTask(block, blockPos, world);
        }
        return 0;
    }

    private int clear(CommandContext<FabricClientCommandSource> context) {
        TaskManager.clearTask();
        return 0;
    }

}
