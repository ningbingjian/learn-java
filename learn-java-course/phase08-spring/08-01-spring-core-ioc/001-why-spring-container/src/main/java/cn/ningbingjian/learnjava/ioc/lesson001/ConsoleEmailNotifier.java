package cn.ningbingjian.learnjava.ioc.lesson001;

/** 仅打印到控制台，模拟邮件渠道；不会发送真实邮件。 */
public final class ConsoleEmailNotifier implements Notifier {
    @Override
    public void orderAccepted(Order order) {
        System.out.println("EMAIL order=" + order.getId() + " quantity=" + order.getQuantity());
    }
}
