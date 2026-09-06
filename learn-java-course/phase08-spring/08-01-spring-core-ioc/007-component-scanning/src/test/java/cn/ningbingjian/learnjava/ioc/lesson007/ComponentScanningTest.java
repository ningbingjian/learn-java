package cn.ningbingjian.learnjava.ioc.lesson007;

import cn.ningbingjian.learnjava.ioc.lesson007.api.Notifier;
import cn.ningbingjian.learnjava.ioc.lesson007.api.OrderRepository;
import cn.ningbingjian.learnjava.ioc.lesson007.app.AppComponents;
import cn.ningbingjian.learnjava.ioc.lesson007.app.data.InMemoryOrderRepository;
import cn.ningbingjian.learnjava.ioc.lesson007.app.delivery.ConsoleNotifier;
import cn.ningbingjian.learnjava.ioc.lesson007.app.service.OrderNotificationService;
import cn.ningbingjian.learnjava.ioc.lesson007.app.utility.PlainHelper;
import cn.ningbingjian.learnjava.ioc.lesson007.app.utility.URLCodec;
import cn.ningbingjian.learnjava.ioc.lesson007.config.DefaultScanConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.config.ExcludeUtilityConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.config.IncludeOnlyConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.config.NarrowScanConfig;
import cn.ningbingjian.learnjava.ioc.lesson007.outside.AuditReporter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ComponentScanningTest {
    @Test
    void configurationDrivenScanRegistersNamedComponentsAndAssemblesTheService() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(DefaultScanConfig.class);
            assertFalse(context.containsBeanDefinition("orderNotificationService"));
            context.refresh();
            var service = context.getBean(OrderNotificationService.class);
            assertTrue(service.notifyAccepted("O-001"));
            assertEquals(List.of("order=O-001 accepted"), context.getBean(ConsoleNotifier.class).messages());
            assertSame(context.getBean(Notifier.class), context.getBean("emailNotifier"));
            assertSame(context.getBean(OrderRepository.class), context.getBean("inMemoryOrderRepository"));
            assertTrue(context.containsBean("URLCodec"));
            assertFalse(context.containsBean("uRLCodec"));
            assertNotNull(context.getBeanFactory().getBeanDefinition("orderNotificationService").getResourceDescription());
        }
    }

    @Test
    void defaultScanDoesNotIncludePlainClassesOrComponentsOutsideItsPackageTree() {
        try (var context = new AnnotationConfigApplicationContext(DefaultScanConfig.class)) {
            assertTrue(context.getBeansOfType(PlainHelper.class).isEmpty());
            assertTrue(context.getBeansOfType(AuditReporter.class).isEmpty());
            assertEquals(1, context.getBeansOfType(Notifier.class).size());
        }
    }

    @Test
    void excludeFilterRemovesTheUtilityWithoutBreakingBusinessDependencies() {
        try (var context = new AnnotationConfigApplicationContext(ExcludeUtilityConfig.class)) {
            assertTrue(context.getBeansOfType(URLCodec.class).isEmpty());
            assertTrue(context.getBean(OrderNotificationService.class).notifyAccepted("O-001"));
            assertEquals(List.of("order=O-001 accepted"), context.getBean(ConsoleNotifier.class).messages());
        }
    }

    @Test
    void explicitIncludeWithDefaultFiltersDisabledCanSelectAnUnannotatedClass() {
        try (var context = new AnnotationConfigApplicationContext(IncludeOnlyConfig.class)) {
            assertEquals("plain-helper", context.getBean(PlainHelper.class).label());
            assertNotNull(context.getBean(Notifier.class));
            assertTrue(context.getBeansOfType(OrderNotificationService.class).isEmpty());
            assertTrue(context.getBeansOfType(OrderRepository.class).isEmpty());
            assertTrue(context.getBeansOfType(URLCodec.class).isEmpty());
        }
    }

    @Test
    void includeFilterWithoutDisablingDefaultsAddsToTheDefaultCandidates() {
        try (var context = new AnnotationConfigApplicationContext(AdditiveIncludeConfig.class)) {
            assertNotNull(context.getBean(PlainHelper.class));
            assertNotNull(context.getBean(URLCodec.class));
            assertTrue(context.getBean(OrderNotificationService.class).notifyAccepted("O-001"));
        }
    }

    @Test
    void scanningOnlyTheServicePackageFailsAtItsMissingRepositoryDependency() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(NarrowScanConfig.class);
            var failure = assertThrows(UnsatisfiedDependencyException.class, context::refresh);
            assertEquals("orderNotificationService", failure.getBeanName());
            var missing = assertInstanceOf(NoSuchBeanDefinitionException.class, failure.getMostSpecificCause());
            assertNotNull(missing.getResolvableType());
            assertEquals(OrderRepository.class, missing.getResolvableType().resolve());
            assertFalse(context.isActive());
        }
    }

    @Test
    void repeatedIdenticalScanReusesTheCompatibleDefinitionBeforeInstantiation() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan(AppComponents.class.getPackageName());
            var firstDefinition = context.getBeanFactory().getBeanDefinition("orderNotificationService");
            int firstCount = context.getBeanDefinitionCount();
            context.scan(AppComponents.class.getPackageName());
            assertEquals(firstCount, context.getBeanDefinitionCount());
            assertSame(firstDefinition, context.getBeanFactory().getBeanDefinition("orderNotificationService"));
            assertFalse(context.getBeanFactory().containsSingleton("orderNotificationService"));
            context.refresh();
            var service = context.getBean(OrderNotificationService.class);
            assertSame(service, context.getBean(OrderNotificationService.class));
            assertEquals(1, context.getBeansOfType(OrderNotificationService.class).size());
        }
    }

    @Test
    void differentScannedClassesWithTheSameDefaultNameConflictDuringScanning() {
        String basePackage = DemoApplication.class.getPackageName() + ".collision";
        try (var context = new AnnotationConfigApplicationContext()) {
            var failure = assertThrows(IllegalStateException.class, () -> context.scan(basePackage));
            assertEquals("ConflictingBeanDefinitionException", failure.getClass().getSimpleName());
            assertTrue(failure.getMessage().contains(basePackage + ".one.NotificationClient"));
            assertTrue(failure.getMessage().contains(basePackage + ".two.NotificationClient"));
            assertFalse(context.isActive());
        }
    }

    @Test
    void fullyQualifiedDefaultNamesAllowBothDistinctClassesToBeRegistered() {
        String basePackage = DemoApplication.class.getPackageName() + ".collision";
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setBeanNameGenerator(FullyQualifiedAnnotationBeanNameGenerator.INSTANCE);
            context.scan(basePackage);
            context.refresh();
            Object first = context.getBean(basePackage + ".one.NotificationClient");
            Object second = context.getBean(basePackage + ".two.NotificationClient");
            assertNotSame(first, second);
            assertEquals(basePackage + ".one.NotificationClient", first.getClass().getName());
            assertEquals(basePackage + ".two.NotificationClient", second.getClass().getName());
        }
    }

    @Test
    void explicitlyRegisteringTheSameComponentClassesPreservesTheirNamesAndWiring() {
        try (var context = new AnnotationConfigApplicationContext(OrderNotificationService.class,
                InMemoryOrderRepository.class, ConsoleNotifier.class, URLCodec.class)) {
            assertTrue(context.getBean(OrderNotificationService.class).notifyAccepted("O-001"));
            assertSame(context.getBean(ConsoleNotifier.class), context.getBean("emailNotifier"));
            assertTrue(context.containsBean("URLCodec"));
            assertEquals(List.of("order=O-001 accepted"), context.getBean(ConsoleNotifier.class).messages());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = AppComponents.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PlainHelper.class))
    static class AdditiveIncludeConfig {
    }
}
