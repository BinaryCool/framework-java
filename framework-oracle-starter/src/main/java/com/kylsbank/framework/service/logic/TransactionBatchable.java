package com.kylsbank.framework.service.logic;

/**
 * 可批量事务处理
 */
public interface TransactionBatchable {
    /**
     * 处理批量事务
     * @param i 当前第几页
     */
    void transactionBatch(int i);
}
