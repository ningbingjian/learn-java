package cn.ningbingjian.learnjava.ioc.lesson001;

import java.util.Objects;

/** 每次下单产生的数据对象；本课只处理一种商品。 */
public final class Order {
    private final String id;
    private final int quantity;

    public Order(String id, int quantity) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }

    public String getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }
}
