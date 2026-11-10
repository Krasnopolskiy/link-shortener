package io.github.krasnopolskiy.shortener.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Base62CodeGeneratorTest {

    @Test
    void producesCodesOfConfiguredLengthAndAlphabet() {
        Base62CodeGenerator generator = new Base62CodeGenerator(9);
        for (int i = 0; i < 1000; i++) {
            String code = generator.next();
            assertEquals(9, code.length());
            assertTrue(code.matches("[0-9A-Za-z]+"), code);
        }
    }
}
