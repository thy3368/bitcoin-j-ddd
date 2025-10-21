package com.tanggo.fund.eth.lib.domain.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * EIP-4844交易 (Type 3)
 * Cancun升级引入的Blob交易（Shard Blob Transactions）
 *
 * 特性：
 * - 引入blob数据承载，为L2提供低成本数据可用性
 * - 继承EIP-1559的费用机制
 * - 新增blob费用市场（独立于普通Gas）
 * - 每个blob最大128KB，每个交易最多6个blob
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Eip4844Transaction implements Transaction {

    /**
     * 链ID
     */
    private BigInteger chainId;

    /**
     * Nonce
     */
    private BigInteger nonce;

    /**
     * 最大优先费用（小费）
     */
    private BigInteger maxPriorityFeePerGas;

    /**
     * 最大费用
     */
    private BigInteger maxFeePerGas;

    /**
     * Gas限制
     */
    private BigInteger gasLimit;

    /**
     * 接收者地址 (20 bytes)
     * Blob交易不能是合约创建交易（to不能为null）
     */
    private byte[] to;

    /**
     * 转账金额
     */
    private BigInteger value;

    /**
     * 交易数据
     */
    private byte[] data;

    /**
     * 访问列表
     */
    @Builder.Default
    private List<AccessListEntry> accessList = new ArrayList<>();

    /**
     * Blob版本哈希列表
     * 每个blob的KZG承诺的版本化哈希（32字节）
     * 最多6个blob
     */
    @Builder.Default
    private List<byte[]> blobVersionedHashes = new ArrayList<>();

    /**
     * 最大Blob费用
     * 愿意支付的每个blob gas的最大费用，单位Wei
     */
    private BigInteger maxFeePerBlobGas;

    /**
     * 签名V值
     */
    private BigInteger v;

    /**
     * 签名R值
     */
    private BigInteger r;

    /**
     * 签名S值
     */
    private BigInteger s;

    /**
     * Blob数据（仅在网络传输时需要）
     * 每个blob 128KB = 131,072字节 = 4096个字段元素
     * 执行层不存储blob数据，仅存储版本化哈希
     */
    private transient List<byte[]> blobs;

    /**
     * KZG承诺列表（仅在网络传输时需要）
     * 每个blob的KZG承诺（48字节）
     */
    private transient List<byte[]> kzgCommitments;

    /**
     * KZG证明列表（仅在网络传输时需要）
     * 每个blob的KZG证明（48字节）
     */
    private transient List<byte[]> kzgProofs;

    private transient byte[] cachedHash;
    private transient byte[] cachedSender;

    /**
     * 每个blob的Gas消耗
     */
    public static final int GAS_PER_BLOB = 131_072; // 2^17

    /**
     * 目标blob数量（每个区块）
     */
    public static final int TARGET_BLOB_COUNT = 3;

    /**
     * 最大blob数量（每个区块）
     */
    public static final int MAX_BLOB_COUNT_PER_BLOCK = 6;

    /**
     * 最大blob数量（每个交易）
     */
    public static final int MAX_BLOB_COUNT_PER_TX = 6;

    @Override
    public TransactionType getType() {
        return TransactionType.EIP4844;
    }

    @Override
    public boolean validate() {
        if (chainId == null || chainId.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }

        if (nonce == null || nonce.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        if (maxFeePerGas == null || maxFeePerGas.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        if (maxFeePerGas.compareTo(maxPriorityFeePerGas) < 0) {
            return false;
        }

        if (gasLimit == null || gasLimit.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }

        if (gasLimit.compareTo(getIntrinsicGas()) < 0) {
            return false;
        }

        // Blob交易不能是合约创建
        if (to == null || to.length == 0) {
            return false;
        }

        if (to.length != 20) {
            return false;
        }

        if (value == null || value.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        if (accessList != null) {
            for (AccessListEntry entry : accessList) {
                if (entry == null || !entry.validate()) {
                    return false;
                }
            }
        }

        // Blob验证
        if (blobVersionedHashes == null || blobVersionedHashes.isEmpty()) {
            return false; // Blob交易必须至少有一个blob
        }

        if (blobVersionedHashes.size() > MAX_BLOB_COUNT_PER_TX) {
            return false; // 最多6个blob
        }

        for (byte[] hash : blobVersionedHashes) {
            if (hash == null || hash.length != 32) {
                return false; // 每个版本化哈希必须是32字节
            }
            // 版本化哈希的第一个字节应该是版本号（当前为0x01）
            if (hash[0] != 0x01) {
                return false;
            }
        }

        if (maxFeePerBlobGas == null || maxFeePerBlobGas.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        if (v == null || r == null || s == null) {
            return false;
        }

        if (!v.equals(BigInteger.ZERO) && !v.equals(BigInteger.ONE)) {
            return false;
        }

        BigInteger secp256k1N = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);

        if (r.compareTo(BigInteger.ZERO) <= 0 || r.compareTo(secp256k1N) >= 0) {
            return false;
        }
        if (s.compareTo(BigInteger.ZERO) <= 0 || s.compareTo(secp256k1N) >= 0) {
            return false;
        }

        BigInteger halfN = secp256k1N.divide(BigInteger.TWO);
        if (s.compareTo(halfN) > 0) {
            return false;
        }

        return true;
    }

    @Override
    public boolean verifySignature() {
        if (!validate()) {
            return false;
        }

        byte[] sender = recoverSender();
        if (sender == null || sender.length != 20) {
            return false;
        }

        cachedSender = sender;
        return true;
    }

    @Override
    public byte[] recoverSender() {
        if (cachedSender != null) {
            return cachedSender;
        }
        // TODO: 实现ECDSA签名恢复
        return new byte[20];
    }

    @Override
    public byte[] getTransactionHash() {
        if (cachedHash != null) {
            return cachedHash;
        }
        // TODO: 实现Keccak256哈希
        // hash = keccak256(0x03 || rlp([chainId, nonce, maxPriorityFeePerGas,
        //                              maxFeePerGas, gasLimit, to, value, data,
        //                              accessList, maxFeePerBlobGas,
        //                              blobVersionedHashes, v, r, s]))
        cachedHash = new byte[32];
        return cachedHash;
    }

    @Override
    public byte[] encode() {
        // TODO: 实现RLP编码
        return new byte[0];
    }

    /**
     * 获取blob数量
     * @return blob数量
     */
    public int getBlobCount() {
        return blobVersionedHashes != null ? blobVersionedHashes.size() : 0;
    }

    /**
     * 计算总的blob gas
     * @return blob gas总量
     */
    public BigInteger getTotalBlobGas() {
        return BigInteger.valueOf((long) getBlobCount() * GAS_PER_BLOB);
    }

    /**
     * 计算blob的最大成本
     * @return blob最大成本（Wei）
     */
    public BigInteger getMaxBlobCost() {
        return getTotalBlobGas().multiply(maxFeePerBlobGas);
    }

    /**
     * 计算交易的总最大成本（包括blob费用）
     * @return 总最大成本（Wei）
     */
    public BigInteger getTotalMaxCost() {
        BigInteger gasCost = gasLimit.multiply(maxFeePerGas);
        BigInteger blobCost = getMaxBlobCost();
        return value.add(gasCost).add(blobCost);
    }

    /**
     * 判断交易是否可以包含在具有给定blob费用的区块中
     * @param blobBaseFee blob基础费用
     * @return 是否可以包含
     */
    public boolean canBeIncludedWithBlobFee(BigInteger blobBaseFee) {
        if (blobBaseFee == null) {
            return true;
        }
        return maxFeePerBlobGas.compareTo(blobBaseFee) >= 0;
    }

    /**
     * 验证KZG证明（如果有blob数据）
     * @return KZG证明是否有效
     */
    public boolean verifyKzgProofs() {
        if (blobs == null || kzgCommitments == null || kzgProofs == null) {
            return true; // 执行层不需要验证，共识层验证
        }

        if (blobs.size() != kzgCommitments.size() ||
            blobs.size() != kzgProofs.size() ||
            blobs.size() != blobVersionedHashes.size()) {
            return false;
        }

        // TODO: 实现KZG证明验证
        // 这需要BLS12-381椭圆曲线和KZG多项式承诺方案
        return true;
    }

    @Override
    public boolean isContractCreation() {
        // Blob交易不能是合约创建
        return false;
    }
}
