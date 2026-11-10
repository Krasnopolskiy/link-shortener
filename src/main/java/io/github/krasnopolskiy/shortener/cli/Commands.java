package io.github.krasnopolskiy.shortener.cli;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class Commands {

    private static final int MAX_HELP_ARGUMENTS = 1;

    private final PrintWriter out;
    private final Map<String, Command> commands = new LinkedHashMap<>();

    Commands(LinkCommands linkCommands, UserCommands userCommands, PrintWriter out, Runnable exit) {
        this.out = out;
        linkCommands.all().forEach(this::add);
        userCommands.all().forEach(this::add);
        add(
            new Command(
                "help",
                "help [command]",
                "Show all commands, or details of one.",
                List.of("help edit"),
                this::help
            )
        );
        add(
            new Command("exit", "exit", "Leave the program. Your links are kept.", List.of("exit"), args -> {
                Arguments.requireCount(args, 0);
                exit.run();
            })
        );
    }

    Optional<Command> find(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    private void add(Command command) {
        commands.put(command.name(), command);
    }

    private void help(List<String> args) {
        if (args.size() > MAX_HELP_ARGUMENTS) {
            throw new UsageException("Expected at most one command name.");
        }
        if (!args.isEmpty()) {
            helpFor(args.get(0));
            return;
        }
        out.println("Commands:");
        commands.values().forEach(command -> out.printf("  %-16s %s%n", command.name(), command.summary()));
        out.println("Type 'help <command>' for usage and examples, or paste a short link to open it.");
    }

    private void helpFor(String name) {
        Command command = find(name).orElseThrow(() -> new UsageException("There is no command '" + name + "'."));
        out.println(command.summary());
        out.println("Usage: " + command.usage());
        out.println("Examples:");
        command.examples().forEach(example -> out.println("  " + example));
    }
}
