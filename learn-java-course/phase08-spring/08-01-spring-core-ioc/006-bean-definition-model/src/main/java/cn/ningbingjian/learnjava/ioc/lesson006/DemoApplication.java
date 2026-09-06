package cn.ningbingjian.learnjava.ioc.lesson006;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.type.MethodMetadata;

public final class DemoApplication {
    private DemoApplication() {
    }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "metadata" : args[0];
        switch (mode) {
            case "metadata" -> metadata();
            case "eager" -> creation(false, false);
            case "lazy" -> creation(true, false);
            case "lazy-dependency" -> creation(true, true);
            case "prototype" -> prototype();
            case "java-config" -> javaConfig();
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void metadata() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBeanDefinition("notifier", DefinitionSamples.notifierDefinition(probe));
            BeanDefinition definition = context.getBeanFactory().getBeanDefinition("notifier");
            System.out.println("has-definition=" + context.containsBeanDefinition("notifier"));
            System.out.println("has-singleton=" + context.getBeanFactory().containsSingleton("notifier"));
            System.out.println("bean-name=notifier");
            System.out.println("bean-class=" + definition.getBeanClassName());
            System.out.println("scope=" + definition.getScope());
            System.out.println("lazy=" + definition.isLazyInit());
            System.out.println("constructor-arg-0=" + definition.getConstructorArgumentValues()
                    .getIndexedArgumentValues().get(0).getValue());
            System.out.println("property-prefix=" + definition.getPropertyValues().get("prefix"));
            System.out.println("init-method=" + definition.getInitMethodName());
            System.out.println("destroy-method=" + definition.getDestroyMethodName());
            System.out.println("origin=" + definition.getResourceDescription());
            System.out.println("constructed=" + probe.count("constructed"));
        }
    }

    private static void creation(boolean lazy, boolean dependent) {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            var definition = DefinitionSamples.notifierDefinition(probe);
            definition.setLazyInit(lazy);
            context.registerBeanDefinition("notifier", definition);
            if (dependent) {
                context.registerBeanDefinition("orderService", DefinitionSamples.orderServiceDefinition());
            }
            System.out.println("registered.constructed=" + probe.count("constructed"));
            context.refresh();
            System.out.println("refreshed.constructed=" + probe.count("constructed"));
            System.out.println("refreshed.has-singleton=" + context.getBeanFactory().containsSingleton("notifier"));
            var first = context.getBean("notifier", TrackedNotifier.class);
            var second = context.getBean("notifier", TrackedNotifier.class);
            System.out.println("after-get.constructed=" + probe.count("constructed"));
            System.out.println("same-instance=" + (first == second));
            if (dependent) {
                context.getBean(OrderNotificationService.class).notifyAccepted("O-001");
            } else {
                first.send("hello");
            }
            System.out.println("messages=" + probe.messages());
        }
        System.out.println("events=" + probe.events());
    }

    private static void prototype() {
        var probe = new LifecycleProbe();
        try (var context = new AnnotationConfigApplicationContext()) {
            var definition = DefinitionSamples.notifierDefinition(probe);
            definition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
            context.registerBeanDefinition("notifier", definition);
            context.refresh();
            System.out.println("refreshed.constructed=" + probe.count("constructed"));
            try (var first = context.getBean("notifier", TrackedNotifier.class);
                 var second = context.getBean("notifier", TrackedNotifier.class)) {
                System.out.println("same-instance=" + (first == second));
                System.out.println("after-get.constructed=" + probe.count("constructed"));
                System.out.println("has-definition=" + context.containsBeanDefinition("notifier"));
                System.out.println("has-singleton=" + context.getBeanFactory().containsSingleton("notifier"));
                context.close();
                System.out.println("after-context-close.closed=" + probe.count("closed"));
            }
            System.out.println("after-caller-close.closed=" + probe.count("closed"));
        }
    }

    private static void javaConfig() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(JavaConfig.class);
            System.out.println("before-refresh.has-factory-definition=" + context.containsBeanDefinition("factoryNotifier"));
            context.refresh();
            var definition = context.getBeanFactory().getBeanDefinition("factoryNotifier");
            System.out.println("after-refresh.has-factory-definition=" + context.containsBeanDefinition("factoryNotifier"));
            System.out.println("bean-class=" + definition.getBeanClassName());
            System.out.println("factory-bean=" + definition.getFactoryBeanName());
            System.out.println("factory-method=" + definition.getFactoryMethodName());
            System.out.println("has-resource=" + (definition.getResourceDescription() != null));
            if (definition.getSource() instanceof MethodMetadata source) {
                System.out.println("source-method=" + source.getMethodName());
            }
            System.out.println("property-count=" + definition.getPropertyValues().size());
            var notifier = context.getBean(Notifier.class);
            System.out.println("runtime-class=" + notifier.getClass().getSimpleName());
            notifier.send("hello");
            System.out.println("messages=" + context.getBean(LifecycleProbe.class).messages());
        }
    }
}
