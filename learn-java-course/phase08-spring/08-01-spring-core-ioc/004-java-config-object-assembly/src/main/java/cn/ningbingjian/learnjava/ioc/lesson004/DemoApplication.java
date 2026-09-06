package cn.ningbingjian.learnjava.ioc.lesson004;

import java.util.Arrays;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class DemoApplication {
    private DemoApplication() {
    }

    public static void main(String[] args) throws NoSuchMethodException {
        String mode = args.length == 0 ? "email" : args[0];
        switch (mode) {
            case "email" -> business(EmailChannelConfig.class);
            case "sms" -> business(SmsChannelConfig.class);
            case "identity" -> identity();
            case "two-beans" -> twoBeans();
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static void business(Class<?> channelConfig) {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, channelConfig)) {
            var orderService = context.getBean(OrderNotificationService.class);
            var receiptService = context.getBean(ReceiptNotificationService.class);
            orderService.notifyAccepted("O-001");
            receiptService.notifyReady("R-001");
            System.out.println("notifier-count=" + context.getBeansOfType(Notifier.class).size());
            Notifier channel = context.getBean(Notifier.class);
            int count = channel instanceof ConsoleEmailNotifier email
                    ? email.sentCount() : ((ConsoleSmsNotifier) channel).sentCount();
            System.out.println("channel.sent-count=" + count);
        }
    }

    private static void identity() throws NoSuchMethodException {
        try (var context = new AnnotationConfigApplicationContext(BusinessConfig.class, EmailChannelConfig.class)) {
            Notifier byType = context.getBean(Notifier.class);
            Notifier byName = context.getBean("orderNotifier", Notifier.class);
            Object byAlias = context.getBean("notificationChannel");
            System.out.println("factory-method=emailNotifier");
            System.out.println("bean-name=orderNotifier");
            System.out.println("aliases=" + Arrays.toString(context.getAliases("orderNotifier")));
            System.out.println("declared-type=" + EmailChannelConfig.class
                    .getDeclaredMethod("emailNotifier").getReturnType().getSimpleName());
            System.out.println("runtime-type=" + byType.getClass().getSimpleName());
            System.out.println("same-name-and-type=" + (byName == byType));
            System.out.println("same-name-and-alias=" + (byName == byAlias));
            System.out.println("method-name-is-bean-name=" + context.containsBean("emailNotifier"));
            System.out.println("notifier-count=" + context.getBeansOfType(Notifier.class).size());
        }
    }

    private static void twoBeans() {
        try (var context = new AnnotationConfigApplicationContext(TwoChannelsConfig.class)) {
            var first = context.getBean("orderChannel", ConsoleEmailNotifier.class);
            var second = context.getBean("auditChannel", ConsoleEmailNotifier.class);
            System.out.println("same-runtime-class=" + (first.getClass() == second.getClass()));
            System.out.println("same-instance=" + (first == second));
            System.out.println("same-bean-again=" + (first == context.getBean("orderChannel")));
            first.send("order-only");
            System.out.println("order.sent-count=" + first.sentCount());
            System.out.println("audit.sent-count=" + second.sentCount());
            try {
                context.getBean(Notifier.class);
            } catch (NoUniqueBeanDefinitionException expected) {
                System.out.println("lookup-by-type=" + expected.getClass().getSimpleName());
                System.out.println("matching-beans=" + expected.getNumberOfBeansFound());
            }
        }
    }
}
