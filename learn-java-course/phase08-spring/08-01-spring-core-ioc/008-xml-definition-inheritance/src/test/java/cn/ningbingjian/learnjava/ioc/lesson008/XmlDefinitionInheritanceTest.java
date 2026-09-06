package cn.ningbingjian.learnjava.ioc.lesson008;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.NotWritablePropertyException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class XmlDefinitionInheritanceTest {
    @Test
    void xmlReferencesAndCollectionsAssembleTheBusiness() {
        try (var context = XmlContexts.load("xml-basics.xml")) {
            context.refresh();
            var service = context.getBean(OrderService.class);
            var notifier = context.getBean(RouteNotifier.class);
            assertSame(notifier, service.notifier());
            assertSame(context.getBean(DeliveryLog.class), notifier.log());
            service.accept("O-008");
            assertEquals(List.of("email [orders] order=O-008 -> [ops@example.test] headers={region=cn}"),
                    notifier.log().deliveries());
        }
    }

    @Test
    void xmlInitializationAndDestructionBelongToTheManagedInstance() {
        DeliveryLog log;
        RouteNotifier notifier;
        try (var context = XmlContexts.load("xml-basics.xml")) {
            context.refresh();
            log = context.getBean(DeliveryLog.class);
            notifier = context.getBean(RouteNotifier.class);
            assertTrue(notifier.ready());
            assertEquals(List.of("init:email"), log.lifecycle());
        }
        assertFalse(notifier.ready());
        assertEquals(List.of("init:email", "close:email"), log.lifecycle());
        assertThrows(IllegalStateException.class, () -> notifier.send("O-008"));
    }

    @Test
    void rawAndMergedDefinitionsDifferWithoutCreatingBusinessObjects() {
        try (var context = XmlContexts.load("xml-inheritance.xml")) {
            var factory = context.getBeanFactory();
            var raw = factory.getBeanDefinition("emailNotifier");
            assertNull(raw.getBeanClassName());
            assertEquals("notifierTemplate", raw.getParentName());
            assertEquals(1, ((List<?>) raw.getPropertyValues().get("recipients")).size());
            var merged = factory.getMergedBeanDefinition("emailNotifier");
            assertEquals(RouteNotifier.class.getName(), merged.getBeanClassName());
            assertEquals(2, ((List<?>) merged.getPropertyValues().get("recipients")).size());
            assertFalse(merged.isAbstract());
            assertTrue(factory.getBeanDefinition("notifierTemplate").isLazyInit());
            assertFalse(merged.isLazyInit());
            var args = merged.getConstructorArgumentValues().getIndexedArgumentValues();
            assertEquals("email", ((TypedStringValue) args.get(0).getValue()).getValue());
            assertEquals("deliveryLog", ((RuntimeBeanReference) args.get(1).getValue()).getBeanName());
            assertEquals("initialize", merged.getInitMethodName());
            assertEquals("shutdown", merged.getDestroyMethodName());
            assertFalse(factory.containsSingleton("deliveryLog"));
            assertFalse(factory.containsSingleton("emailNotifier"));
            assertEquals(1, ((List<?>) raw.getPropertyValues().get("recipients")).size());
        }
    }

    @Test
    void childOverridesScalarAndConstructorValuesAndMergesCollections() {
        try (var context = XmlContexts.load("xml-inheritance.xml")) {
            context.refresh();
            var email = context.getBean("emailNotifier", RouteNotifier.class);
            assertEquals("email", email.channel());
            assertEquals("[priority]", email.prefix());
            assertEquals(List.of("ops@example.test", "owner@example.test"), email.recipients());
            assertEquals(Map.of("region", "us", "source", "course", "priority", "high"), email.headers());
            assertTrue(email.ready());
        }
    }

    @Test
    void collectionWithoutMergeReplacesParentAndTemplateIsNeverAnInstance() {
        DeliveryLog log;
        try (var context = XmlContexts.load("xml-inheritance.xml")) {
            context.refresh();
            var email = context.getBean("emailNotifier", RouteNotifier.class);
            var sms = context.getBean("smsNotifier", RouteNotifier.class);
            log = context.getBean(DeliveryLog.class);
            assertEquals(List.of("on-call"), sms.recipients());
            assertEquals("[orders]", sms.prefix());
            assertEquals(Map.of("region", "cn", "source", "course"), sms.headers());
            assertSame(email.getClass(), sms.getClass());
            assertNotSame(email, sms);
            assertEquals(List.of("init:email", "init:sms"), log.lifecycle().stream().sorted().toList());
            assertFalse(context.getBeanFactory().containsSingleton("notifierTemplate"));
        }
        assertEquals(List.of("close:email", "close:sms"), log.lifecycle().stream()
                .filter(e -> e.startsWith("close:")).sorted().toList());
    }

    @Test
    void requestingAnAbstractDefinitionFailsEvenThoughItsJavaClassIsConcrete() {
        try (var context = XmlContexts.load("xml-inheritance.xml")) {
            context.refresh();
            assertTrue(context.containsBeanDefinition("notifierTemplate"));
            var error = assertThrows(BeanIsAbstractException.class, () -> context.getBean("notifierTemplate"));
            assertEquals("notifierTemplate", error.getBeanName());
            assertTrue(context.getBean("emailNotifier", RouteNotifier.class).ready());
        }
    }

    @Test
    void unresolvedReferenceIsDetectedDuringCreationRatherThanXmlLoading() {
        try (var context = XmlContexts.load("xml-missing-ref.xml")) {
            assertTrue(context.containsBeanDefinition("orderService"));
            var error = assertThrows(BeanCreationException.class, context::refresh);
            assertEquals("orderService", error.getBeanName());
            var root = assertInstanceOf(NoSuchBeanDefinitionException.class, error.getMostSpecificCause());
            assertEquals("missingNotifier", root.getBeanName());
            assertFalse(context.isActive());
        }
    }

    @Test
    void childClassMustAcceptTheInheritedProperties() {
        try (var context = XmlContexts.load("xml-incompatible-class.xml")) {
            var merged = context.getBeanFactory().getMergedBeanDefinition("invalidTarget");
            assertEquals(Object.class.getName(), merged.getBeanClassName());
            assertTrue(merged.getPropertyValues().contains("prefix"));
            var error = assertThrows(BeanCreationException.class, context::refresh);
            assertEquals("invalidTarget", error.getBeanName());
            var root = assertInstanceOf(NotWritablePropertyException.class, error.getMostSpecificCause());
            assertEquals("prefix", root.getPropertyName());
            assertFalse(context.isActive());
        }
    }

    @Test
    void staticAndInstanceFactoriesProduceManagedInitializedObjects() {
        DeliveryLog log;
        try (var context = XmlContexts.load("xml-factories.xml")) {
            context.refresh();
            log = context.getBean(DeliveryLog.class);
            var first = context.getBean("staticNotifier", RouteNotifier.class);
            var second = context.getBean("instanceNotifier", RouteNotifier.class);
            assertEquals("static", first.channel());
            assertEquals("instance", second.channel());
            assertTrue(first.ready() && second.ready());
            assertSame(log, first.log());
            assertSame(log, second.log());
            assertNotSame(first, second);
            var definition = context.getBeanFactory().getBeanDefinition("instanceNotifier");
            assertNull(definition.getBeanClassName());
            assertEquals("notifierFactory", definition.getFactoryBeanName());
            assertEquals("create", definition.getFactoryMethodName());
        }
        assertEquals(List.of("close:instance", "close:static"), log.lifecycle().stream()
                .filter(e -> e.startsWith("close:")).sorted().toList());
    }

    @Test
    void javaConfigPreservesBehaviorWhileItsDefinitionUsesAFactoryMethod() {
        DeliveryLog xmlLog;
        DeliveryLog javaLog;
        try (var xml = XmlContexts.load("xml-basics.xml");
             var java = new AnnotationConfigApplicationContext(JavaConfig.class)) {
            xml.refresh();
            xml.getBean(OrderService.class).accept("O-008");
            java.getBean(OrderService.class).accept("O-008");
            xmlLog = xml.getBean(DeliveryLog.class);
            javaLog = java.getBean(DeliveryLog.class);
            assertEquals(xmlLog.deliveries(), javaLog.deliveries());
            assertEquals(xmlLog.lifecycle(), javaLog.lifecycle());
            var xmlDefinition = xml.getBeanFactory().getBeanDefinition("notifier");
            var javaDefinition = java.getBeanFactory().getBeanDefinition("notifier");
            assertNull(xmlDefinition.getFactoryMethodName());
            assertEquals("notifier", javaDefinition.getFactoryMethodName());
            assertTrue(xmlDefinition.getPropertyValues().contains("prefix"));
            assertFalse(javaDefinition.getPropertyValues().contains("prefix"));
        }
        assertEquals(List.of("init:email", "close:email"), xmlLog.lifecycle());
        assertEquals(xmlLog.lifecycle(), javaLog.lifecycle());
    }

    @Test
    void importResourceSupportsAnIncrementalJavaConfigurationEntryPoint() {
        try (var context = new AnnotationConfigApplicationContext(ImportXmlConfig.class)) {
            context.getBean(OrderService.class).accept("O-008");
            assertEquals(1, context.getBeansOfType(RouteNotifier.class).size());
            assertEquals(List.of("email [orders] order=O-008 -> [ops@example.test] headers={region=cn}"),
                    context.getBean(DeliveryLog.class).deliveries());
        }
    }

    @Test
    void contextHierarchyResolvesUpwardAndChildCloseDoesNotDestroyParentBean() {
        DeliveryLog log;
        try (var parent = XmlContexts.load("xml-basics.xml")) {
            parent.refresh();
            log = parent.getBean(DeliveryLog.class);
            var notifier = parent.getBean(RouteNotifier.class);
            try (var child = XmlContexts.load("xml-child-context.xml")) {
                child.setParent(parent);
                child.refresh();
                assertFalse(child.containsBeanDefinition("notifier"));
                assertTrue(child.containsBean("notifier"));
                assertSame(notifier, child.getBean("childService", OrderService.class).notifier());
                assertFalse(parent.containsBean("childService"));
                assertThrows(NoSuchBeanDefinitionException.class, () -> parent.getBean("childService"));
            }
            assertTrue(notifier.ready());
            assertEquals(List.of("init:email"), log.lifecycle());
        }
        assertEquals(List.of("init:email", "close:email"), log.lifecycle());
    }
}
