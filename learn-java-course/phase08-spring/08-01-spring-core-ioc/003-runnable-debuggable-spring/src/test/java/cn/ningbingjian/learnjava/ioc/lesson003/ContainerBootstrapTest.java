package cn.ningbingjian.learnjava.ioc.lesson003;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class ContainerBootstrapTest {
    @Test
    void registrationAloneDoesNotPrepareTheContextForLookup() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            assertFalse(context.isActive());
            assertFalse(context.containsBeanDefinition("notifier"));
            assertThrows(IllegalStateException.class,
                    () -> context.getBean(OrderNotificationService.class));
        }
    }

    @Test
    void refreshCreatesAndInitializesOurNonLazySingletonBeforeBusinessLookup() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            context.refresh();
            assertTrue(context.isActive());
            assertTrue(context.containsBeanDefinition("notifier"));
            assertTrue(context.getBeanFactory().containsSingleton("notifier"));
            assertTrue(context.getBeanFactory().containsSingleton("orderNotificationService"));
            assertTrue(context.getBean(ManagedConsoleNotifier.class).isOpen());
            assertDoesNotThrow(() -> context.getBean(OrderNotificationService.class).notifyAccepted("O-001"));
        }
    }

    @Test
    void repeatedLookupReturnsTheSameDefaultSingletonByNameAndType() {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            var first = context.getBean(OrderNotificationService.class);
            assertSame(first, context.getBean(OrderNotificationService.class));
            assertSame(first, context.getBean("orderNotificationService"));
            assertSame(context.getBean(Notifier.class), context.getBean("notifier"));
        }
    }

    @Test
    void separateContextsOwnSeparateObjectGraphs() {
        try (var first = new AnnotationConfigApplicationContext(AppConfig.class);
             var second = new AnnotationConfigApplicationContext(AppConfig.class)) {
            assertNotSame(first.getBean(Notifier.class), second.getBean(Notifier.class));
            assertNotSame(first.getBean(OrderNotificationService.class),
                    second.getBean(OrderNotificationService.class));
        }
    }

    @Test
    void closeReleasesManagedResourceAndRejectsFurtherContextLookup() {
        var context = new AnnotationConfigApplicationContext(AppConfig.class);
        ManagedConsoleNotifier notifier;
        try (context) {
            notifier = context.getBean(ManagedConsoleNotifier.class);
            assertTrue(notifier.isOpen());
        }
        assertFalse(context.isActive());
        assertFalse(notifier.isOpen());
        assertThrows(IllegalStateException.class,
                () -> context.getBean(OrderNotificationService.class));
    }

    @Test
    void alreadyHeldServiceReferenceSurvivesCloseButItsCollaboratorIsClosed() {
        OrderNotificationService service;
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            service = context.getBean(OrderNotificationService.class);
        }
        var failure = assertThrows(IllegalStateException.class, () -> service.notifyAccepted("O-002"));
        assertEquals("notifier is not open", failure.getMessage());
    }

    @Test
    void tryWithResourcesClosesTheContextWhenBusinessCodeThrows() {
        var context = new AnnotationConfigApplicationContext(AppConfig.class);
        var notifier = context.getBean(ManagedConsoleNotifier.class);
        var businessFailure = new IllegalArgumentException("simulated business failure");
        var actual = assertThrows(IllegalArgumentException.class, () -> {
            try (context) {
                throw businessFailure;
            }
        });
        assertSame(businessFailure, actual);
        assertFalse(context.isActive());
        assertFalse(notifier.isOpen());
    }

    @Test
    void manuallyCreatedServiceCanStillWorkWithoutAnySpringContext() {
        var messages = new java.util.ArrayList<String>();
        var service = new OrderNotificationService(messages::add);
        service.notifyAccepted("O-001");
        assertEquals(java.util.List.of("order=O-001 accepted"), messages);
    }
}
