package cn.ningbingjian.learnjava.ioc.lesson007;

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
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

public final class DemoApplication {
    private DemoApplication() {
    }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "scan" : args[0];
        switch (mode) {
            case "scan" -> configured(DefaultScanConfig.class, true);
            case "exclude" -> configured(ExcludeUtilityConfig.class, true);
            case "include-only" -> configured(IncludeOnlyConfig.class, false);
            case "narrow" -> narrow();
            case "repeat" -> repeatedScan();
            case "collision" -> collision(false);
            case "qualified" -> collision(true);
            case "explicit" -> explicit();
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void configured(Class<?> config, boolean invokeBusiness) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(config);
            System.out.println("before-refresh.has-service=" + context.containsBeanDefinition("orderNotificationService"));
            context.refresh();
            System.out.println("app-beans=" + applicationBeanNames(context));
            if (invokeBusiness) {
                System.out.println("accepted=" + context.getBean(OrderNotificationService.class).notifyAccepted("O-001"));
            } else {
                System.out.println("helper=" + context.getBean(PlainHelper.class).label());
            }
            System.out.println("outside-included=" + context.containsBeanDefinition("auditReporter"));
        }
    }

    private static void narrow() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(NarrowScanConfig.class);
            try {
                context.refresh();
            } catch (UnsatisfiedDependencyException expected) {
                System.out.println("failed-bean=" + expected.getBeanName());
                System.out.println("cause=" + expected.getClass().getSimpleName());
                if (expected.getMostSpecificCause() instanceof NoSuchBeanDefinitionException missing) {
                    System.out.println("missing-type=" + missing.getResolvableType());
                }
                System.out.println("active=" + context.isActive());
            }
        }
    }

    private static void repeatedScan() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan(AppComponents.class.getPackageName());
            var firstDefinition = context.getBeanFactory().getBeanDefinition("orderNotificationService");
            System.out.println("first-scan.count=" + applicationBeanNames(context).size());
            context.scan(AppComponents.class.getPackageName());
            System.out.println("second-scan.count=" + applicationBeanNames(context).size());
            System.out.println("same-definition=" + (firstDefinition == context.getBeanFactory()
                    .getBeanDefinition("orderNotificationService")));
            System.out.println("before-refresh.has-singleton=" + context.getBeanFactory()
                    .containsSingleton("orderNotificationService"));
            context.refresh();
            var service = context.getBean(OrderNotificationService.class);
            System.out.println("same-service=" + (service == context.getBean(OrderNotificationService.class)));
        }
    }

    private static void collision(boolean qualifiedNames) {
        String collisionPackage = DemoApplication.class.getPackageName() + ".collision";
        try (var context = new AnnotationConfigApplicationContext()) {
            if (qualifiedNames) {
                context.setBeanNameGenerator(FullyQualifiedAnnotationBeanNameGenerator.INSTANCE);
            }
            try {
                context.scan(collisionPackage);
            } catch (IllegalStateException expected) {
                System.out.println("scan-error=" + expected.getClass().getSimpleName());
                System.out.println("mentions-both-classes=" + (expected.getMessage().contains(collisionPackage + ".one.NotificationClient")
                        && expected.getMessage().contains(collisionPackage + ".two.NotificationClient")));
                System.out.println("active=" + context.isActive());
                return;
            }
            context.refresh();
            var first = context.getBean(cn.ningbingjian.learnjava.ioc.lesson007.collision.one.NotificationClient.class);
            var second = context.getBean(cn.ningbingjian.learnjava.ioc.lesson007.collision.two.NotificationClient.class);
            System.out.println("first-name-exists=" + context.containsBean(first.getClass().getName()));
            System.out.println("second-name-exists=" + context.containsBean(second.getClass().getName()));
            System.out.println("same-instance=" + ((Object) first == second));
        }
    }

    private static void explicit() {
        try (var context = new AnnotationConfigApplicationContext(OrderNotificationService.class,
                InMemoryOrderRepository.class, ConsoleNotifier.class, URLCodec.class)) {
            System.out.println("app-beans=" + applicationBeanNames(context));
            System.out.println("accepted=" + context.getBean(OrderNotificationService.class).notifyAccepted("O-001"));
        }
    }

    private static List<String> applicationBeanNames(AnnotationConfigApplicationContext context) {
        String prefix = AppComponents.class.getPackageName() + ".";
        return Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> {
                    String className = context.getBeanFactory().getBeanDefinition(name).getBeanClassName();
                    return className != null && className.startsWith(prefix);
                })
                .sorted()
                .toList();
    }
}
