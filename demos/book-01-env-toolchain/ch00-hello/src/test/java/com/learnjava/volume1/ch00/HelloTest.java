package com.learnjava.volume1.ch00;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** ch00 自测：跑通即证明「编译 + 测试」链路 OK。 */
class HelloTest {

    @Test
    void message_returnsGreeting() {
        assertEquals("Hello from learn-java!", Hello.message());
    }

    @Test
    void message_isNotEmpty() {
        assertNotNull(Hello.message());
        assertTrue(Hello.message().length() > 0);
    }
}
