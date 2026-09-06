package cn.ningbingjian.learnjava.ioc.lesson001;

/** 用于单线程演示的单商品库存；每个实例有自己的 remaining 字段。 */
public final class InMemoryInventory implements Inventory {
    private int remaining;

    public InMemoryInventory(int initialStock) {
        if (initialStock < 0) {
            throw new IllegalArgumentException("initialStock must not be negative");
        }
        this.remaining = initialStock;
    }

    @Override
    public boolean reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (remaining < quantity) {
            return false;
        }
        remaining -= quantity;
        return true;
    }

    public int remainingStock() {
        return remaining;
    }
}
