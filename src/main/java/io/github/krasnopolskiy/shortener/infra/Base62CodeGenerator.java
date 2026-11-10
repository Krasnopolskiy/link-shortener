package io.github.krasnopolskiy.shortener.infra;

import io.github.krasnopolskiy.shortener.core.port.CodeGenerator;
import java.security.SecureRandom;

public class Base62CodeGenerator implements CodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final int length;
    private final SecureRandom random = new SecureRandom();

    public Base62CodeGenerator(int length) {
        this.length = length;
    }

    @Override
    public String next() {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
