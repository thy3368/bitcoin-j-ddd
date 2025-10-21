package com.tanggo.fund.eth.lib.domain.transaction;

import java.math.BigInteger;

/**
 * 以太坊交易基础接口
 * 定义所有交易类型的共同行为
 */
public interface Transaction {

    /**
     * 获取交易类型
     * Type 0: Legacy Transaction
     * Type 1: EIP-2930 (Access List)
     * Type 2: EIP-1559 (Dynamic Fee)
     * Type 3: EIP-4844 (Blob Transaction)
     * @return 交易类型
     */
    TransactionType getType();

    /**
     * 获取链ID
     * @return 链ID
     */
    BigInteger getChainId();

    /**
     * 获取nonce
     * 发送者账户的交易序号
     * @return nonce
     */
    BigInteger getNonce();

    /**
     * 获取接收者地址
     * @return 接收者地址（20字节），null表示合约创建
     */
    byte[] getTo();

    /**
     * 获取转账金额
     * @return 转账金额（Wei）
     */
    BigInteger getValue();

    /**
     * 获取交易数据
     * @return 交易数据（合约调用数据或合约创建代码）
     */
    byte[] getData();

    /**
     * 获取Gas限制
     * @return Gas限制
     */
    BigInteger getGasLimit();

    /**
     * 获取签名的V值
     * @return V值
     */
    BigInteger getV();

    /**
     * 获取签名的R值
     * @return R值
     */
    BigInteger getR();

    /**
     * 获取签名的S值
     * @return S值
     */
    BigInteger getS();

    /**
     * 验证交易的基本有效性
     * @return 是否有效
     */
    boolean validate();

    /**
     * 验证交易签名
     * @return 签名是否有效
     */
    boolean verifySignature();

    /**
     * 恢复交易发送者地址
     * @return 发送者地址（20字节）
     */
    byte[] recoverSender();

    /**
     * 判断是否为合约创建交易
     * @return 是否为合约创建
     */
    default boolean isContractCreation() {
        return getTo() == null || getTo().length == 0;
    }

    /**
     * 获取交易哈希
     * @return 交易哈希（32字节）
     */
    byte[] getTransactionHash();

    /**
     * 序列化交易（RLP编码）
     * @return RLP编码的交易数据
     */
    byte[] encode();

    /**
     * 计算交易的内在Gas成本
     * 内在成本 = 21000（基础） + calldata成本 + 合约创建成本
     * @return 内在Gas成本
     */
    default BigInteger getIntrinsicGas() {
        // 基础交易成本：21000 gas
        BigInteger intrinsicGas = BigInteger.valueOf(21_000);

        // Calldata成本
        byte[] data = getData();
        if (data != null && data.length > 0) {
            for (byte b : data) {
                if (b == 0) {
                    intrinsicGas = intrinsicGas.add(BigInteger.valueOf(4)); // 零字节：4 gas
                } else {
                    intrinsicGas = intrinsicGas.add(BigInteger.valueOf(16)); // 非零字节：16 gas
                }
            }
        }

        // 合约创建额外成本：32000 gas
        if (isContractCreation()) {
            intrinsicGas = intrinsicGas.add(BigInteger.valueOf(32_000));
        }

        return intrinsicGas;
    }
}