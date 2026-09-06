package cn.ningbingjian.learnjava.ioc.lesson005;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class DemoApplication {
    private DemoApplication() {
    }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "console" : args[0];
        switch (mode) {
            case "console" -> console();
            case "business-failure" -> businessFailure();
            case "missing-bean" -> missingBean();
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void console() {
        ManagedConsoleNotifier notifier;
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, ConsoleChannelConfig.class)) {
            notifier = context.getBean(ManagedConsoleNotifier.class);
            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
        }
        System.out.println("after-close.open=" + notifier.isOpen());
        System.out.println("after-close.count=" + notifier.closeCount());
    }

    private static void businessFailure() {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, ConsoleChannelConfig.class)) {
            context.getBean(OrderNotificationService.class).notifyAccepted(" ");
        } catch (IllegalArgumentException expected) {
            System.out.println("caught=" + expected.getMessage());
        }
    }

    private static void missingBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(BusinessConfig.class);
            try {
                context.refresh();
            } catch (UnsatisfiedDependencyException expected) {
                System.out.println("failed-bean=" + expected.getBeanName());
                for (Throwable cause = expected; cause != null; cause = cause.getCause()) {
                    System.out.println("cause=" + cause.getClass().getSimpleName());
                }
                if (expected.getMostSpecificCause() instanceof NoSuchBeanDefinitionException missing) {
                    System.out.println("missing-type=" + missing.getResolvableType());
                }
                System.out.println("after-failure.active=" + context.isActive());
            }
        }
    }
}
