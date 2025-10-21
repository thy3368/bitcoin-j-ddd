package com.tanggo.fund.eth.lib.outbound;

import com.tanggo.fund.bitcoin.lib.domain.Transaction;
import com.tanggo.fund.eth.lib.domain.repo.ITransactionPool;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 简化的交易池类定义
public class TransactionPool implements ITransactionPool {
    private final Map<String, TreeMap<BigInteger, Transaction>> pending = new ConcurrentHashMap<>();
    private final Map<String, TreeMap<BigInteger, Transaction>> queue = new ConcurrentHashMap<>();
    private final Map<String, Transaction> allTransactions = new ConcurrentHashMap<>();

    private final PriorityQueue<Transaction> pricedTransactions=null;
//    private final PriorityQueue<Transaction> pricedTransactions = new PriorityQueue<>(Comparator.comparing(Transaction::getEffectiveGasPrice));

    @Override
    public boolean addTransaction(Transaction tx) {
        // 1. 基本验证 (签名、大小、Gas等)
        if (!validateTransaction(tx)) {
            return false;
        }

        String from = tx.getFrom();
        BigInteger nonce = tx.getNonce();
        BigInteger expectedNonce = getCurrentNonceFromState(from); // 从状态数据库获取账户当前Nonce

        // 2. 检查是否已存在
        if (allTransactions.containsKey(tx.getHash())) {
            return false; // 交易已存在
        }

        // 3. 判断交易应放入pending还是queue
        if (nonce.compareTo(expectedNonce) <= 0) {
            // Nonce小于或等于预期值，可能是无效或重复交易，需要更复杂的处理（如替换）
            // 此处简化处理
            return handleReplacementOrInvalid(tx, expectedNonce);
        } else if (nonce.compareTo(expectedNonce) == 1) {
            // Nonce不连续，放入queue
            return addToQueue(tx);
        } else {
            // Nonce连续，放入pending
            return addToPending(tx);
        }
    }

    private boolean handleReplacementOrInvalid(Transaction tx, BigInteger expectedNonce) {
        return false;
    }

    private BigInteger getCurrentNonceFromState(String from) {
        return new BigInteger(from);
    }

    private boolean validateTransaction(Transaction tx) {
        return false;
    }

    private boolean addToPending(Transaction tx) {
        String from = tx.getFrom();
        BigInteger nonce = tx.getNonce();

        TreeMap<BigInteger, Transaction> accountTxs = pending.computeIfAbsent(from, k -> new TreeMap<>());

        // 检查是否存在相同Nonce的交易，并进行价格比较替换（PriceBump机制）
        Transaction existingTx = accountTxs.get(nonce);
        if (existingTx != null) {
            if (tx.getGasPrice().compareTo(existingTx.getGasPrice()) <= 0) {
                return false; // 新交易Gas价格不够高，不替换
            }
            // 移除旧交易
            removeTransactionFromGlobalIndices(existingTx);
        }

        // 加入pending和全局索引
        accountTxs.put(nonce, tx);
        allTransactions.put(tx.getHash(), tx);
        pricedTransactions.offer(tx);

        // 尝试促进（promote）queue中因这个新交易而变得连续的交易
        promoteExecutables(from);
        return true;
    }

    private boolean addToQueue(Transaction tx) {
        // 逻辑类似addToPending，但交易被加入到queue中
        // 同时也会加入allTransactions和pricedTransactions
        // ...
        return true;
    }

    private void promoteExecutables(String accountAddress) {
        TreeMap<BigInteger, Transaction> queued = queue.get(accountAddress);
        if (queued == null) return;

        BigInteger nextExpectedNonce = getCurrentNonceFromState(accountAddress).add(BigInteger.ONE);
        List<Transaction> toPromote = new ArrayList<>();

        // 查找从nextExpectedNonce开始连续的交易
        for (BigInteger nonce = nextExpectedNonce; queued.containsKey(nonce); nonce = nonce.add(BigInteger.ONE)) {
            toPromote.add(queued.get(nonce));
        }

        // 将这些交易从queue移到pending
        for (Transaction tx : toPromote) {
            queued.remove(tx.getNonce());
            addToPending(tx); // 复用方法
        }

        if (queued.isEmpty()) {
            queue.remove(accountAddress);
        }
    }

    @Override
    public List<Transaction> getPendingTransactions(int limit) {
        List<Transaction> candidates = new ArrayList<>();
        // 从priced队列（已按GasPrice排序）中获取交易，直到满足数量或Gas上限
        // 注意：需要确保来自同一账户的交易按Nonce顺序取出
        Iterator<Transaction> priceIterator = pricedTransactions.iterator();

        while (priceIterator.hasNext() && candidates.size() < limit) {
            Transaction tx = priceIterator.next();
            // 这里需要复杂的逻辑来确保账户交易顺序，并避免重复，此处大幅简化
            if (isStillPending(tx)) { // 检查交易是否仍在pending池中
                candidates.add(tx);
            }
        }
        return candidates;
    }

    private boolean isStillPending(Transaction tx) {
        return tx.getGasPrice().compareTo(tx.getGasPrice()) == 0;
    }

    @Override
    public void removeTransaction(String txHash) {
        Transaction tx = allTransactions.remove(txHash);
        if (tx == null) return;

        String from = tx.getFrom();
        BigInteger nonce = tx.getNonce();

        // 从pending或queue中移除
        removeFromMap(pending, from, nonce);
        removeFromMap(queue, from, nonce);

        // 从价格排序队列中移除（实际实现可能需要更高效的方式）
        pricedTransactions.remove(tx);
    }

    private void removeFromMap(Map<String, TreeMap<BigInteger, Transaction>> map, String from, BigInteger nonce) {
        TreeMap<BigInteger, Transaction> accountTxs = map.get(from);
        if (accountTxs != null) {
            accountTxs.remove(nonce);
            if (accountTxs.isEmpty()) {
                map.remove(from);
            }
        }
    }
}
