package cn.ningbingjian.learnjava.ioc.lesson008;

import java.util.List;
import java.util.TreeMap;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

public final class DemoApplication {
    private DemoApplication() { }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "xml" : args[0];
        switch (mode) {
            case "xml" -> runBusiness(XmlContexts.load("xml-basics.xml"));
            case "java-config" -> runBusiness(new AnnotationConfigApplicationContext(JavaConfig.class));
            case "hybrid" -> runBusiness(new AnnotationConfigApplicationContext(ImportXmlConfig.class));
            case "definitions" -> definitions();
            case "inheritance" -> inheritance();
            case "factory" -> factories();
            case "missing-ref" -> failedRefresh("xml-missing-ref.xml");
            case "incompatible-class" -> failedRefresh("xml-incompatible-class.xml");
            case "abstract-template" -> abstractTemplate();
            case "hierarchy" -> hierarchy();
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void runBusiness(GenericApplicationContext context) {
        DeliveryLog log;
        try (context) {
            if (!context.isActive()) { context.refresh(); }
            log = context.getBean(DeliveryLog.class);
            System.out.println("lifecycle.before=" + log.lifecycle());
            context.getBean(OrderService.class).accept("O-008");
            System.out.println("deliveries=" + log.deliveries());
            System.out.println("same-reference=" + (context.getBean(OrderService.class).notifier()
                    == context.getBean("notifier")));
        }
        System.out.println("lifecycle.after=" + log.lifecycle());
    }

    private static void definitions() {
        try (var context = XmlContexts.load("xml-inheritance.xml")) {
            var factory = context.getBeanFactory();
            var raw = factory.getBeanDefinition("emailNotifier");
            var merged = factory.getMergedBeanDefinition("emailNotifier");
            System.out.println("raw.class=" + raw.getBeanClassName());
            System.out.println("raw.parent=" + raw.getParentName());
            System.out.println("raw.recipients=" + ((List<?>) raw.getPropertyValues().get("recipients")).size());
            System.out.println("merged.class=" + merged.getBeanClassName());
            System.out.println("merged.recipients=" + ((List<?>) merged.getPropertyValues().get("recipients")).size());
            System.out.println("merged.abstract=" + merged.isAbstract());
            System.out.println("merged.lazy=" + merged.isLazyInit());
            System.out.println("has-log-instance=" + factory.containsSingleton("deliveryLog"));
            System.out.println("has-email-instance=" + factory.containsSingleton("emailNotifier"));
        }
    }

    private static void inheritance() {
        DeliveryLog log;
        try (var context = XmlContexts.load("xml-inheritance.xml")) {
            context.refresh();
            log = context.getBean(DeliveryLog.class);
            var email = context.getBean("emailNotifier", RouteNotifier.class);
            var sms = context.getBean("smsNotifier", RouteNotifier.class);
            System.out.println("email.channel=" + email.channel());
            System.out.println("email.prefix=" + email.prefix());
            System.out.println("email.recipients=" + email.recipients());
            System.out.println("email.headers=" + new TreeMap<>(email.headers()));
            System.out.println("sms.prefix=" + sms.prefix());
            System.out.println("sms.recipients=" + sms.recipients());
            System.out.println("same-java-class=" + (email.getClass() == sms.getClass()));
            System.out.println("template-has-instance=" + context.getBeanFactory().containsSingleton("notifierTemplate"));
            System.out.println("initializations=" + log.lifecycle().stream().sorted().toList());
        }
        System.out.println("close-count=" + log.lifecycle().stream().filter(e -> e.startsWith("close:")).count());
    }

    private static void factories() {
        try (var context = XmlContexts.load("xml-factories.xml")) {
            context.refresh();
            var factory = context.getBeanFactory();
            var first = context.getBean("staticNotifier", RouteNotifier.class);
            var second = context.getBean("instanceNotifier", RouteNotifier.class);
            System.out.println("static.method=" + factory.getBeanDefinition("staticNotifier").getFactoryMethodName());
            System.out.println("instance.factory=" + factory.getBeanDefinition("instanceNotifier").getFactoryBeanName());
            System.out.println("instance.definition-class=" + factory.getBeanDefinition("instanceNotifier").getBeanClassName());
            System.out.println("product-type=" + second.getClass().getSimpleName());
            System.out.println("products-ready=" + (first.ready() && second.ready()));
            System.out.println("shared-log=" + (first.log() == second.log()));
        }
    }

    private static void failedRefresh(String resource) {
        try (var context = XmlContexts.load(resource)) {
            System.out.println("definitions-loaded=true");
            try {
                context.refresh();
            } catch (BeanCreationException expected) {
                System.out.println("failed-bean=" + expected.getBeanName());
                System.out.println("root-cause=" + expected.getMostSpecificCause().getClass().getSimpleName());
                System.out.println("active=" + context.isActive());
            }
        }
    }

    private static void abstractTemplate() {
        try (var context = XmlContexts.load("xml-inheritance.xml")) {
            context.refresh();
            System.out.println("template-definition-exists=" + context.containsBeanDefinition("notifierTemplate"));
            try {
                context.getBean("notifierTemplate");
            } catch (BeanIsAbstractException expected) {
                System.out.println("get-template-error=" + expected.getClass().getSimpleName());
            }
            System.out.println("child-ready=" + context.getBean("emailNotifier", RouteNotifier.class).ready());
        }
    }

    private static void hierarchy() {
        try (var parent = XmlContexts.load("xml-basics.xml")) {
            parent.refresh();
            var notifier = parent.getBean(RouteNotifier.class);
            try (var child = XmlContexts.load("xml-child-context.xml")) {
                child.setParent(parent);
                child.refresh();
                System.out.println("child-local-notifier=" + child.containsBeanDefinition("notifier"));
                System.out.println("child-can-find-notifier=" + child.containsBean("notifier"));
                System.out.println("child-uses-parent-instance=" + (child.getBean("childService", OrderService.class)
                        .notifier() == notifier));
                System.out.println("parent-can-find-child-service=" + parent.containsBean("childService"));
            }
            System.out.println("parent-notifier-ready-after-child-close=" + notifier.ready());
        }
    }
}
