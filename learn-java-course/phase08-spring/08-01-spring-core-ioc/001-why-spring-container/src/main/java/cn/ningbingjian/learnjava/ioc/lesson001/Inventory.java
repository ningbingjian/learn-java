package cn.ningbingjian.learnjava.ioc.lesson001;

/** 成功时扣减库存并返回 true；库存不足时保持原值并返回 false。 */
public interface Inventory {
    boolean reserve(int quantity);
}
