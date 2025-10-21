package com.tanggo.fund.eth.lib.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * 以太坊区块头
 * 包含区块的元数据和关键哈希值
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Header {

    /**
     * 父区块哈希 (32 bytes)
     */
    private byte[] parentHash;

    /**
     * 叔块哈希 (32 bytes)
     * 叔块列表的RLP编码的Keccak256哈希
     */
    private byte[] ommersHash;

    /**
     * 受益人地址 (20 bytes)
     * 接收区块奖励的矿工地址
     */
    private byte[] beneficiary;

    /**
     * 状态树根 (32 bytes)
     * 状态树根哈希
     */
    private byte[] stateRoot;

    /**
     * 交易树根 (32 bytes)
     * 交易树根哈希
     */
    private byte[] transactionsRoot;

    /**
     * 收据树根 (32 bytes)
     * 收据树根哈希
     */
    private byte[] receiptsRoot;

    /**
     * 日志布隆过滤器 (256 bytes)
     * 用于快速查找日志
     */
    private byte[] logsBloom;

    /**
     * 难度值
     * PoW挖矿难度
     */
    private BigInteger difficulty;

    /**
     * 区块号
     * 区块在链上的高度
     */
    private BigInteger number;

    /**
     * Gas限制
     * 区块中所有交易的Gas总限制
     */
    private BigInteger gasLimit;

    /**
     * 已使用的Gas
     * 区块中所有交易实际消耗的Gas总和
     */
    private BigInteger gasUsed;

    /**
     * 时间戳
     * 区块创建的Unix时间戳（秒）
     */
    private long timestamp;

    /**
     * 额外数据
     * 矿工可以包含的任意数据（最大32字节）
     */
    private byte[] extraData;

    /**
     * Mix哈希 (32 bytes)
     * PoW相关，与nonce一起证明工作量
     */
    private byte[] mixHash;

    /**
     * Nonce (8 bytes)
     * PoW随机数，用于挖矿
     */
    private byte[] nonce;

    /**
     * 基础费用（EIP-1559）
     * 每个Gas的基础费用，单位wei
     * null表示EIP-1559之前的区块
     */
    private BigInteger baseFeePerGas;

    /**
     * 提款根 (32 bytes) - EIP-4895
     * 验证者提款的默克尔根
     * null表示上海升级之前的区块
     */
    private byte[] withdrawalsRoot;

    /**
     * Blob Gas使用量 - EIP-4844
     * 区块中实际使用的blob gas
     * null表示Cancun升级之前的区块
     */
    private BigInteger blobGasUsed;

    /**
     * 超额Blob Gas - EIP-4844
     * 用于计算blob基础费用
     * null表示Cancun升级之前的区块
     */
    private BigInteger excessBlobGas;

    /**
     * 验证区块头的基本有效性
     * @return 是否有效
     */
    public boolean validate() {
        // 基本字段非空检查
        if (parentHash == null || parentHash.length != 32) {
            return false;
        }
        if (ommersHash == null || ommersHash.length != 32) {
            return false;
        }
        if (beneficiary == null || beneficiary.length != 20) {
            return false;
        }
        if (stateRoot == null || stateRoot.length != 32) {
            return false;
        }
        if (transactionsRoot == null || transactionsRoot.length != 32) {
            return false;
        }
        if (receiptsRoot == null || receiptsRoot.length != 32) {
            return false;
        }
        if (logsBloom == null || logsBloom.length != 256) {
            return false;
        }

        // 数值字段检查
        if (difficulty == null || difficulty.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (number == null || number.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (gasLimit == null || gasLimit.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }
        if (gasUsed == null || gasUsed.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (gasUsed.compareTo(gasLimit) > 0) {
            return false; // 已使用Gas不能超过限制
        }

        if (timestamp <= 0) {
            return false;
        }

        // extraData最大32字节
        if (extraData != null && extraData.length > 32) {
            return false;
        }

        if (mixHash == null || mixHash.length != 32) {
            return false;
        }
        if (nonce == null || nonce.length != 8) {
            return false;
        }

        // EIP-1559检查
        if (baseFeePerGas != null && baseFeePerGas.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // EIP-4895检查
        if (withdrawalsRoot != null && withdrawalsRoot.length != 32) {
            return false;
        }

        // EIP-4844检查
        if (blobGasUsed != null && blobGasUsed.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (excessBlobGas != null && excessBlobGas.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        return true;
    }

    /**
     * 判断是否为创世区块
     * @return 是否为创世区块
     */
    public boolean isGenesis() {
        return number != null && number.equals(BigInteger.ZERO);
    }

    /**
     * 判断是否支持EIP-1559
     * @return 是否支持EIP-1559
     */
    public boolean supportsEip1559() {
        return baseFeePerGas != null;
    }

    /**
     * 判断是否支持提款（上海升级）
     * @return 是否支持提款
     */
    public boolean supportsWithdrawals() {
        return withdrawalsRoot != null;
    }

    /**
     * 判断是否支持Blob交易（Cancun升级）
     * @return 是否支持Blob交易
     */
    public boolean supportsBlobTransactions() {
        return blobGasUsed != null && excessBlobGas != null;
    }
}
