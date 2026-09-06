package cn.ningbingjian.learnjava.ioc.lesson001;

/** 仅打印到控制台，模拟短信渠道；不会发送真实短信。 */
public final class ConsoleSmsNotifier implements Notifier {
    @Override
    public void orderAccepted(Order order) {
        System.out.println("SMS order=" + order.getId() + " quantity=" + order.getQuantity());
    }
}
