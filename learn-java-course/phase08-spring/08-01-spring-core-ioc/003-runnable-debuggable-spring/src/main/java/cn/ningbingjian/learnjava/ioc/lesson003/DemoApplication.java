package cn.ningbingjian.learnjava.ioc.lesson003;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class DemoApplication {
    private DemoApplication() {
    }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "lifecycle" : args[0];
        switch (mode) {
            case "lifecycle" -> lifecycle();
            case "shortcut" -> shortcut();
            case "not-refreshed" -> notRefreshed();
            case "business-failure" -> businessFailure();
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void lifecycle() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        ManagedConsoleNotifier notifier;
        try (context) {
            System.out.println("before-refresh.active=" + context.isActive());
            context.register(AppConfig.class);
            System.out.println("after-register.has-notifier-definition="
                    + context.containsBeanDefinition("notifier"));
            context.refresh();
            System.out.println("after-refresh.active=" + context.isActive());
            System.out.println("after-refresh.has-notifier-definition="
                    + context.containsBeanDefinition("notifier"));
            System.out.println("before-get.has-notifier-singleton="
                    + context.getBeanFactory().containsSingleton("notifier"));
            OrderNotificationService service = context.getBean(OrderNotificationService.class);
            OrderNotificationService again = context.getBean(OrderNotificationService.class);
            notifier = context.getBean(ManagedConsoleNotifier.class);
            System.out.println("same-service=" + (service == again));
            service.notifyAccepted("O-001");
        }
        System.out.println("after-close.active=" + context.isActive());
        System.out.println("after-close.notifier-open=" + notifier.isOpen());
        try {
            context.getBean(OrderNotificationService.class);
        } catch (IllegalStateException expected) {
            System.out.println("after-close.get=" + expected.getClass().getSimpleName());
        }
    }

    private static void shortcut() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("shortcut.active=" + context.isActive());
            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
        }
    }

    private static void notRefreshed() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AppConfig.class);
            try {
                context.getBean(OrderNotificationService.class);
            } catch (IllegalStateException expected) {
                System.out.println("before-refresh.get=" + expected.getClass().getSimpleName());
            }
        }
    }

    private static void businessFailure() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
            throw new IllegalStateException("simulated business failure");
        } catch (IllegalStateException expected) {
            System.out.println("caught=" + expected.getMessage());
        }
    }
}
