package cn.ningbingjian.learnjava.ioc.lesson005;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class ContainerFailureTest {
    @Test
    void missingChannelFailsDuringRefreshAndIdentifiesTheInjectionPoint() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(BusinessConfig.class);

            var failure = assertThrows(UnsatisfiedDependencyException.class, context::refresh);

            assertEquals("orderNotificationService", failure.getBeanName());
            assertNotNull(failure.getInjectionPoint());
            assertEquals("orderNotificationService", failure.getInjectionPoint().getMember().getName());
            assertNotNull(failure.getInjectionPoint().getMethodParameter());
            assertEquals(0, failure.getInjectionPoint().getMethodParameter().getParameterIndex());
            var missing = assertInstanceOf(NoSuchBeanDefinitionException.class, failure.getMostSpecificCause());
            assertNotNull(missing.getResolvableType());
            assertEquals(Notifier.class, missing.getResolvableType().resolve());
            assertFalse(context.isActive());
        }
    }

    @Test
    void explicitLookupOfAnUnknownNameFailsAfterAnOtherwiseSuccessfulStartup() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, ConsoleChannelConfig.class)) {
            var failure = assertThrows(NoSuchBeanDefinitionException.class,
                    () -> context.getBean("unknownNotifier"));

            assertEquals("unknownNotifier", failure.getBeanName());
            assertTrue(context.isActive());
            assertDoesNotThrow(() -> context.getBean(OrderNotificationService.class).notifyAccepted("O-001"));
        }
    }
}
