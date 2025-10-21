package com.tanggo.fund.eth.lib.service;

import com.tanggo.fund.eth.lib.domain.Account;
import com.tanggo.fund.eth.lib.domain.Block;
import com.tanggo.fund.eth.lib.domain.Receipt;
import com.tanggo.fund.eth.lib.domain.transaction.Eip1559Transaction;
import com.tanggo.fund.eth.lib.domain.transaction.Transaction;
import com.tanggo.fund.eth.lib.outbound.AccountRepo;
import com.tanggo.fund.eth.lib.outbound.ReceiptRepo;
import com.tanggo.fund.eth.lib.outbound.TxPoolRepo;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;

/**
 * 交易应用服务（用例层）
 *
 * 协调交易、账户、区块等领域对象，实现交易处理用例
 *
 * 职责：
 * - 交易验证（无状态和有状态）
 * - 交易执行
 * - 交易池管理
 * - 收据生成
 *
 * 交易处理流程：
 * 1. 创建并添加到交易池
 * 2. 无状态验证（签名、格式）
 * 3. 有状态验证（余额、nonce）
 * 4. 执行交易（状态转换）
 * 5. 生成收据
 * 6. 打包到区块
 */
@RequiredArgsConstructor
public class TransactionService {

    private final TxPoolRepo txPoolRepo;
    private final AccountRepo accountRepo;
    private final ReceiptRepo receiptRepo;

    /**
     * 创建交易并添加到交易池
     *
     * @param transaction 待处理的交易
     * @return 是否成功添加到交易池
     */
    public boolean submitTransaction(Transaction transaction) {
        // 1. 无状态验证
        if (!statelessValidation(transaction)) {
            throw new IllegalArgumentException("Transaction failed stateless validation");
        }

        // 2. 验证签名
        if (!transaction.verifySignature()) {
            throw new IllegalArgumentException("Invalid transaction signature");
        }

        // 3. 添加到交易池
        if (transaction instanceof Eip1559Transaction) {
            txPoolRepo.add((Eip1559Transaction) transaction);
            return true;
        }

        return false;
    }

    /**
     * 无状态验证
     *
     * 验证不依赖区块链状态的交易属性：
     * - 交易格式正确性
     * - 签名有效性
     * - 字段值合法性
     * - Gas限制足够支付内在成本
     *
     * @param transaction 待验证的交易
     * @return 是否通过验证
     */
    public boolean statelessValidation(Transaction transaction) {
        if (transaction == null) {
            return false;
        }

        // 基本字段验证
        if (!transaction.validate()) {
            return false;
        }

        // Gas限制必须足够支付内在成本
        BigInteger intrinsicGas = transaction.getIntrinsicGas();
        if (transaction.getGasLimit().compareTo(intrinsicGas) < 0) {
            return false;
        }

        // 验证签名格式
        if (transaction.getV() == null || transaction.getR() == null || transaction.getS() == null) {
            return false;
        }

        return true;
    }

    /**
     * 有状态验证
     *
     * 验证依赖区块链状态的交易属性：
     * - 发送者账户存在
     * - 发送者余额充足
     * - Nonce正确
     * - 如果是合约调用，接收者必须是合约账户
     *
     * @param transaction 待验证的交易
     * @return 是否通过验证
     */
    public boolean statefulValidation(Transaction transaction) {
        if (transaction == null) {
            return false;
        }

        // 恢复发送者地址
        byte[] senderAddress = transaction.recoverSender();
        if (senderAddress == null || senderAddress.length != 20) {
            return false;
        }

        // 查询发送者账户
        Account sender = accountRepo.query(bytesToHex(senderAddress));
        if (sender == null) {
            // 账户不存在，创建新账户（余额为0）
            sender = Account.createEOA(senderAddress);
        }

        // 验证nonce（必须等于账户当前nonce）
        if (!transaction.getNonce().equals(sender.getNonce())) {
            return false;
        }

        // 验证余额充足
        BigInteger maxCost = transaction.getValue();

        // 计算最大Gas成本（取决于交易类型）
        if (transaction instanceof Eip1559Transaction) {
            Eip1559Transaction eip1559 = (Eip1559Transaction) transaction;
            BigInteger maxGasCost = eip1559.getGasLimit().multiply(eip1559.getMaxFeePerGas());
            maxCost = maxCost.add(maxGasCost);
        }

        if (!sender.hasSufficientBalance(maxCost)) {
            return false;
        }

        // 如果不是合约创建，验证接收者
        if (!transaction.isContractCreation()) {
            byte[] recipientAddress = transaction.getTo();
            Account recipient = accountRepo.query(bytesToHex(recipientAddress));

            // 接收者可以不存在（新账户）或已存在
            // 如果存在且是合约账户，需要确保data字段包含有效的调用数据
        }

        return true;
    }

    /**
     * 执行交易
     *
     * 执行流程：
     * 1. 验证交易（无状态+有状态）
     * 2. 扣除发送者余额（转账金额 + Gas费用）
     * 3. 增加接收者余额（转账金额）
     * 4. 递增发送者nonce
     * 5. 更新账户状态
     * 6. 生成收据
     *
     * @param transaction 待执行的交易
     * @param baseFeePerGas 区块的基础费用（EIP-1559）
     * @return 交易收据
     */
    public Receipt executeTransaction(Transaction transaction, BigInteger baseFeePerGas) {
        // 1. 验证交易
        if (!statelessValidation(transaction)) {
            throw new IllegalArgumentException("Transaction failed stateless validation");
        }

        if (!statefulValidation(transaction)) {
            throw new IllegalArgumentException("Transaction failed stateful validation");
        }

        // 2. 获取发送者和接收者账户
        byte[] senderAddress = transaction.recoverSender();
        Account sender = accountRepo.query(bytesToHex(senderAddress));

        if (sender == null) {
            throw new IllegalStateException("Sender account not found");
        }

        // 3. 计算实际Gas价格和费用
        BigInteger gasPrice;
        BigInteger gasUsed = transaction.getIntrinsicGas(); // 简化：仅计算内在成本

        if (transaction instanceof Eip1559Transaction) {
            Eip1559Transaction eip1559 = (Eip1559Transaction) transaction;
            gasPrice = eip1559.getEffectiveGasPrice(baseFeePerGas);
        } else {
            // 对于传统交易，使用gasPrice字段
            gasPrice = BigInteger.ZERO; // TODO: 从传统交易获取gasPrice
        }

        BigInteger txFee = gasUsed.multiply(gasPrice);

        // 4. 执行状态转换
        try {
            // 扣除发送者余额（转账金额 + Gas费用）
            BigInteger totalDebit = transaction.getValue().add(txFee);
            sender.debit(totalDebit);

            // 递增nonce
            sender.incrementNonce();

            // 更新发送者账户
            accountRepo.update(sender);

            // 如果不是合约创建，处理接收者
            if (!transaction.isContractCreation()) {
                byte[] recipientAddress = transaction.getTo();
                Account recipient = accountRepo.query(bytesToHex(recipientAddress));

                if (recipient == null) {
                    // 创建新的EOA账户
                    recipient = Account.createEOA(recipientAddress);
                }

                // 增加接收者余额
                recipient.credit(transaction.getValue());
                accountRepo.update(recipient);
            } else {
                // TODO: 处理合约创建
                // 1. 计算合约地址
                // 2. 创建合约账户
                // 3. 执行合约初始化代码
                // 4. 保存合约代码
            }

            // 5. 生成收据（简化版本）
            Receipt receipt = createReceipt(transaction, sender, gasUsed, true);
            receiptRepo.save(receipt);

            return receipt;

        } catch (Exception e) {
            // 交易执行失败，仍然消耗Gas但不执行状态转换
            sender.debit(txFee); // 仍然扣除Gas费用
            sender.incrementNonce();
            accountRepo.update(sender);

            // 生成失败收据
            Receipt receipt = createReceipt(transaction, sender, gasUsed, false);
            receiptRepo.save(receipt);

            throw new RuntimeException("Transaction execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * 将交易打包到区块
     *
     * @param transaction 已执行的交易
     * @param block 目标区块
     */
    public void packageToBlock(Transaction transaction, Block block) {
        if (transaction instanceof Eip1559Transaction) {
            block.getBlockBody().getTransactions().add((Eip1559Transaction) transaction);
        }
    }

    /**
     * 从交易池获取待处理交易
     *
     * @return 交易列表
     */
    public Eip1559Transaction getPendingTransaction() {
        return txPoolRepo.query();
    }

    /**
     * 创建交易收据
     *
     * @param transaction 交易
     * @param sender 发送者账户
     * @param gasUsed 消耗的Gas
     * @param success 是否成功
     * @return 交易收据
     */
    private Receipt createReceipt(Transaction transaction, Account sender,
                                   BigInteger gasUsed, boolean success) {
        // TODO: 实现完整的收据创建逻辑
        // 收据应包含：
        // - 交易哈希
        // - 区块哈希和区块号
        // - 交易索引
        // - 发送者和接收者地址
        // - Gas使用量
        // - 状态（成功/失败）
        // - 日志（合约事件）
        // - 布隆过滤器

        return null; // 占位
    }

    // ==================== 辅助方法 ====================

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
