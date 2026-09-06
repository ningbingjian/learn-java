package cn.ningbingjian.learnjava.ioc.lesson001;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {
    @Test
    void acceptedOrderReducesStockAndNotifiesExactlyOnce() {
        InMemoryInventory inventory = new InMemoryInventory(2);
        RecordingNotifier notifier = new RecordingNotifier();
        OrderService service = new OrderService(inventory, notifier);

        assertTrue(service.place(new Order("O-001", 1)));
        assertEquals(1, inventory.remainingStock());
        assertEquals(List.of("O-001"), notifier.orderIds);
    }

    @Test
    void insufficientStockDoesNotChangeStockOrNotify() {
        InMemoryInventory inventory = new InMemoryInventory(1);
        RecordingNotifier notifier = new RecordingNotifier();
        OrderService service = new OrderService(inventory, notifier);

        assertFalse(service.place(new Order("O-001", 2)));
        assertEquals(1, inventory.remainingStock());
        assertTrue(notifier.orderIds.isEmpty());
    }

    @Test
    void servicesSharingInventoryCannotEachSpendTheSameUnit() {
        InMemoryInventory inventory = new InMemoryInventory(1);
        RecordingNotifier notifier = new RecordingNotifier();
        OrderService webOrders = new OrderService(inventory, notifier);
        OrderService partnerOrders = new OrderService(inventory, notifier);

        assertTrue(webOrders.place(new Order("WEB-001", 1)));
        assertFalse(partnerOrders.place(new Order("PARTNER-001", 1)));
        assertEquals(0, inventory.remainingStock());
        assertEquals(List.of("WEB-001"), notifier.orderIds);
    }

    @Test
    void separateInventoryInstancesStillCreateSeparateBalances() {
        // 构造器注入不会自动共享对象；错误的组装仍然会产生错误的业务模型。
        RecordingNotifier notifier = new RecordingNotifier();
        OrderService webOrders = new OrderService(new InMemoryInventory(1), notifier);
        OrderService partnerOrders = new OrderService(new InMemoryInventory(1), notifier);

        assertTrue(webOrders.place(new Order("WEB-001", 1)));
        assertTrue(partnerOrders.place(new Order("PARTNER-001", 1)));
        assertEquals(List.of("WEB-001", "PARTNER-001"), notifier.orderIds);
    }

    @Test
    void notificationFailureDoesNotMagicallyRollBackTheReservation() {
        InMemoryInventory inventory = new InMemoryInventory(1);
        Notifier failingNotifier = new Notifier() {
            @Override
            public void orderAccepted(Order order) {
                throw new IllegalStateException("notification unavailable");
            }
        };
        OrderService service = new OrderService(inventory, failingNotifier);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.place(new Order("O-001", 1)));
        assertEquals("notification unavailable", error.getMessage());
        assertEquals(0, inventory.remainingStock());
    }

    @Test
    void independentApplicationGraphsDoNotLeakStateToEachOther() {
        InMemoryInventory firstInventory = new InMemoryInventory(1);
        InMemoryInventory secondInventory = new InMemoryInventory(1);
        RecordingNotifier firstNotifier = new RecordingNotifier();
        RecordingNotifier secondNotifier = new RecordingNotifier();

        OrderService firstApplication = new OrderService(firstInventory, firstNotifier);
        OrderService secondApplication = new OrderService(secondInventory, secondNotifier);
        assertTrue(firstApplication.place(new Order("O-001", 1)));
        assertEquals(0, firstInventory.remainingStock());
        assertEquals(1, secondInventory.remainingStock());
        assertTrue(secondNotifier.orderIds.isEmpty());
        assertTrue(secondApplication.place(new Order("O-002", 1)));
        assertEquals(List.of("O-001"), firstNotifier.orderIds);
        assertEquals(List.of("O-002"), secondNotifier.orderIds);
    }

    private static final class RecordingNotifier implements Notifier {
        private final List<String> orderIds = new ArrayList<>();

        @Override
        public void orderAccepted(Order order) {
            orderIds.add(order.getId());
        }
    }
}
