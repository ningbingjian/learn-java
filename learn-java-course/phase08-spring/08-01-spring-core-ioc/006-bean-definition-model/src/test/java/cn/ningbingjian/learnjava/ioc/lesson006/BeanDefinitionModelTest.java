package cn.ningbingjian.learnjava.ioc.lesson006;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.NotWritablePropertyException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class BeanDefinitionModelTest {
    @Test
    void registeringAndReadingADefinitionDoesNotConstructTheBusinessObject() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBeanDefinition("notifier", DefinitionSamples.notifierDefinition(probe));
            var definition = context.getBeanFactory().getBeanDefinition("notifier");
            assertEquals(TrackedNotifier.class.getName(), definition.getBeanClassName());
            assertTrue(context.containsBeanDefinition("notifier"));
            assertFalse(context.getBeanFactory().containsSingleton("notifier"));
            assertTrue(probe.events().isEmpty());
            assertFalse(context.isActive());
        }
        assertTrue(probe.events().isEmpty());
    }

    @Test
    void eagerSingletonAppliesPropertiesBeforeInitializationAndClosesOnContextExit() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBeanDefinition("notifier", DefinitionSamples.notifierDefinition(probe));
            context.refresh();
            assertEquals(List.of("constructed", "configured:[MAIL]", "opened"), probe.events());
            context.getBean(Notifier.class).send("hello");
            assertEquals(List.of("[MAIL] email:hello"), probe.messages());
        }
        assertEquals(List.of("constructed", "configured:[MAIL]", "opened", "closed"), probe.events());
    }

    @Test
    void unusedLazySingletonIsCreatedOnFirstLookupAndReusedAfterwards() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            var definition = DefinitionSamples.notifierDefinition(probe);
            definition.setLazyInit(true);
            context.registerBeanDefinition("notifier", definition);
            context.refresh();
            assertEquals(0, probe.count("constructed"));
            assertFalse(context.getBeanFactory().containsSingleton("notifier"));
            var first = context.getBean(Notifier.class);
            assertSame(first, context.getBean(Notifier.class));
            assertEquals(1, probe.count("constructed"));
            assertEquals(1, probe.count("opened"));
        }
        assertEquals(1, probe.count("closed"));
    }

    @Test
    void lazySingletonNeverRequestedHasNoInitializationOrDestructionEvents() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            var definition = DefinitionSamples.notifierDefinition(probe);
            definition.setLazyInit(true);
            context.registerBeanDefinition("notifier", definition);
            context.refresh();
        }
        assertTrue(probe.events().isEmpty());
    }

    @Test
    void eagerDependentResolvesTheBeanReferenceAndCreatesTheLazyCollaborator() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            var definition = DefinitionSamples.notifierDefinition(probe);
            definition.setLazyInit(true);
            context.registerBeanDefinition("notifier", definition);
            context.registerBeanDefinition("orderService", DefinitionSamples.orderServiceDefinition());
            context.refresh();
            assertEquals(1, probe.count("constructed"));
            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
            assertEquals(List.of("[MAIL] email:order=O-001 accepted"), probe.messages());
        }
    }

    @Test
    void prototypeDefinitionCreatesTwoObjectsWhoseCleanupBelongsToTheCaller() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            var definition = DefinitionSamples.notifierDefinition(probe);
            definition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            context.registerBeanDefinition("notifier", definition);
            context.refresh();
            assertEquals(0, probe.count("constructed"));
            try (var first = context.getBean("notifier", TrackedNotifier.class);
                 var second = context.getBean("notifier", TrackedNotifier.class)) {
                assertNotSame(first, second);
                assertTrue(first.isOpen());
                assertTrue(second.isOpen());
                assertEquals(2, probe.count("constructed"));
                assertFalse(context.getBeanFactory().containsSingleton("notifier"));
                context.close();
                assertEquals(0, probe.count("closed"));
            }
            assertEquals(2, probe.count("closed"));
        }
    }

    @Test
    void invalidPropertyCanBeRegisteredButFailsWhenTheBeanIsCreated() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            var definition = DefinitionSamples.notifierDefinition(probe);
            definition.getPropertyValues().add("missingProperty", "value");
            context.registerBeanDefinition("notifier", definition);
            assertEquals(0, probe.count("constructed"));
            var failure = assertThrows(BeanCreationException.class, context::refresh);
            assertEquals("notifier", failure.getBeanName());
            assertInstanceOf(NotWritablePropertyException.class, failure.getMostSpecificCause());
            assertEquals(1, probe.count("constructed"));
            assertEquals(0, probe.count("opened"));
        }
    }

    @Test
    void beanMethodDefinitionDescribesAFactoryRatherThanCopyingItsMethodBody() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(JavaConfig.class);
            assertFalse(context.containsBeanDefinition("factoryNotifier"));
            context.refresh();
            var definition = context.getBeanFactory().getBeanDefinition("factoryNotifier");
            assertEquals("javaConfig", definition.getFactoryBeanName());
            assertEquals("notifier", definition.getFactoryMethodName());
            assertNotNull(definition.getResourceDescription());
            assertTrue(definition.getPropertyValues().isEmpty());
            var notifier = assertInstanceOf(TrackedNotifier.class, context.getBean(Notifier.class));
            notifier.send("hello");
            assertEquals(List.of("[JAVA] email:hello"), context.getBean(LifecycleProbe.class).messages());
        }
    }

    @Test
    void changingPropertyMetadataBeforeRefreshChangesTheCreatedObjectsConfiguration() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBeanDefinition("notifier", DefinitionSamples.notifierDefinition(probe));
            context.getBeanFactory().getBeanDefinition("notifier").getPropertyValues().add("prefix", "[CHANGED]");
            context.refresh();
            context.getBean(Notifier.class).send("hello");
            assertEquals(List.of("[CHANGED] email:hello"), probe.messages());
        }
    }
}
