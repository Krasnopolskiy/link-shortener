package io.github.krasnopolskiy.shortener.cli;

import java.util.List;

class Arguments {

    private Arguments() {}

    static void requireCount(List<String> args, int expected) {
        if (args.size() != expected) {
            throw new UsageException("Expected " + expected + " argument(s), got " + args.size() + ".");
        }
    }

    static int parseNumber(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new UsageException("'" + text + "' is not a whole number.");
        }
    }
}
