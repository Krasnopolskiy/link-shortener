package io.github.krasnopolskiy.shortener.cli;

import java.util.List;

record Command(String name, String usage, String summary, List<String> examples, Handler handler) {
    @FunctionalInterface
    interface Handler {
        void run(List<String> arguments);
    }
}
