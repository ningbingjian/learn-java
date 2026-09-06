package cn.ningbingjian.learnjava.ioc.lesson001;

/** 应用入口负责选择实现并组装对象，业务服务只执行下单流程。 */
public final class DemoApplication {
    private DemoApplication() {
    }

    public static void main(String[] args) {
        String mode = args.length == 0 ? "manual" : args[0];
        switch (mode) {
            case "hardcoded" -> runHardcoded();
            case "manual" -> runManual(new ConsoleEmailNotifier());
            case "sms" -> runManual(new ConsoleSmsNotifier());
            default -> throw new IllegalArgumentException("mode must be hardcoded, manual or sms");
        }
    }

    private static void runHardcoded() {
        // 两个业务入口原本应该访问同一份库存，却各自创建了一份。
        HardcodedOrderService webOrders = new HardcodedOrderService();
        HardcodedOrderService partnerOrders = new HardcodedOrderService();
        System.out.println("first=" + webOrders.place(new Order("O-001", 1)));
        System.out.println("second=" + partnerOrders.place(new Order("O-002", 1)));
    }

    private static void runManual(Notifier notifier) {
        // 只创建一次库存，把同一个引用交给两个服务。
        InMemoryInventory inventory = new InMemoryInventory(1);
        OrderService webOrders = new OrderService(inventory, notifier);
        OrderService partnerOrders = new OrderService(inventory, notifier);
        System.out.println("first=" + webOrders.place(new Order("O-001", 1)));
        System.out.println("second=" + partnerOrders.place(new Order("O-002", 1)));
        System.out.println("remaining=" + inventory.remainingStock());
    }
}
