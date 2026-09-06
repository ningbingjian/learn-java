package cn.ningbingjian.learnjava.ioc.lesson004;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class JavaConfigAssemblyTest {
    @Test
    void explicitNameAndAliasResolveOneBeanWithoutKeepingTheFactoryMethodName() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, EmailChannelConfig.class)) {
            var notifier = context.getBean(Notifier.class);
            assertSame(notifier, context.getBean("orderNotifier", Notifier.class));
            assertSame(notifier, context.getBean("notificationChannel"));
            assertArrayEquals(new String[]{"notificationChannel"}, context.getAliases("orderNotifier"));
            assertFalse(context.containsBean("emailNotifier"));
            assertEquals(1, context.getBeansOfType(Notifier.class).size());
        }
    }

    @Test
    void twoBusinessServicesUseTheSameContainerManagedEmailChannel() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, EmailChannelConfig.class)) {
            var channel = assertInstanceOf(ConsoleEmailNotifier.class, context.getBean(Notifier.class));
            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
            assertEquals(1, channel.sentCount());
            context.getBean(ReceiptNotificationService.class).notifyReady("R-001");
            assertEquals(2, channel.sentCount());
        }
    }

    @Test
    void selectingSmsConfigurationReusesBothBusinessServicesWithAnotherImplementation() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, SmsChannelConfig.class)) {
            var channel = assertInstanceOf(ConsoleSmsNotifier.class, context.getBean(Notifier.class));
            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
            context.getBean(ReceiptNotificationService.class).notifyReady("R-001");
            assertEquals(2, channel.sentCount());
            assertTrue(context.getBeansOfType(ConsoleEmailNotifier.class).isEmpty());
        }
    }

    @Test
    void reversingTheseConfigurationArgumentsStillResolvesTheUniqueDependency() {
        try (var context = new AnnotationConfigApplicationContext(EmailChannelConfig.class, BusinessConfig.class)) {
            var channel = assertInstanceOf(ConsoleEmailNotifier.class, context.getBean(Notifier.class));
            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
            context.getBean(ReceiptNotificationService.class).notifyReady("R-001");
            assertEquals(2, channel.sentCount());
        }
    }

    @Test
    void sameClassCanHaveTwoIndependentDefaultSingletonDefinitions() {
        try (var context = new AnnotationConfigApplicationContext(TwoChannelsConfig.class)) {
            var order = context.getBean("orderChannel", ConsoleEmailNotifier.class);
            var audit = context.getBean("auditChannel", ConsoleEmailNotifier.class);
            assertEquals(order.getClass(), audit.getClass());
            assertNotSame(order, audit);
            assertSame(order, context.getBean("orderChannel"));
            assertSame(audit, context.getBean("auditChannel"));
            order.send("order-only");
            assertEquals(1, order.sentCount());
            assertEquals(0, audit.sentCount());
        }
    }

    @Test
    void typeOnlyLookupCannotChooseBetweenTwoUnqualifiedCandidates() {
        try (var context = new AnnotationConfigApplicationContext(TwoChannelsConfig.class)) {
            var failure = assertThrows(NoUniqueBeanDefinitionException.class,
                    () -> context.getBean(Notifier.class));
            assertEquals(2, failure.getNumberOfBeansFound());
        }
    }

    @Test
    void nameAndTypeLookupStillChecksTheRequiredType() {
        try (var context = new AnnotationConfigApplicationContext(EmailChannelConfig.class)) {
            assertThrows(BeanNotOfRequiredTypeException.class,
                    () -> context.getBean("orderNotifier", ConsoleSmsNotifier.class));
        }
    }

    @Test
    void configurationOnClasspathIsNotAutomaticallyIncludedWithoutRegistration() {
        try (var context = new AnnotationConfigApplicationContext(EmailChannelConfig.class)) {
            assertEquals(1, context.getBeansOfType(Notifier.class).size());
            assertTrue(context.getBeansOfType(OrderNotificationService.class).isEmpty());
            assertTrue(context.getBeansOfType(ReceiptNotificationService.class).isEmpty());
        }
    }
}
