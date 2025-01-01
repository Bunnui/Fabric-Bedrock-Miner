package yan.lx.bedrockminer.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import yan.lx.bedrockminer.Test;
import yan.lx.bedrockminer.command.commands.BehaviorCommand;
import yan.lx.bedrockminer.command.commands.DebugCommand;
import yan.lx.bedrockminer.command.commands.DisableCommand;
import yan.lx.bedrockminer.command.commands.TaskCommand;
import yan.lx.bedrockminer.task.TaskManager;

import java.util.Arrays;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static yan.lx.bedrockminer.BedrockMiner.COMMAND_PREFIX;
import static yan.lx.bedrockminer.BedrockMiner.TEST;

public class CommandManager {
    private static final List<CommandBase> commands = Arrays.asList(
            new BehaviorCommand(),
            new DebugCommand(),
            new TaskCommand(),
            new DisableCommand()
    );


    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (var command : commands) {
                command.register(dispatcher, registryAccess);
            }
            var root = literal(COMMAND_PREFIX).executes(CommandManager::executes);
            if (TEST) {
                Test.register(root);
            }
            dispatcher.register(root);
        });
    }

    private static int executes(CommandContext<FabricClientCommandSource> context) {
        TaskManager.setWorking(!TaskManager.isWorking());
        return 0;
    }


}
