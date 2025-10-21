package com.tanggo.fund.eth.lib.domain.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * EIP-2930交易 (Type 1)
 * Berlin升级引入的访问列表交易
 *
 * 特性：
 * - 引入访问列表（Access List）
 * - 预声明将访问的账户和存储槽，降低Gas成本
 * - 仍使用传统的gasPrice（不是EIP-1559的动态费用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Eip2930Transaction implements Transaction {

    /**
     * 链ID
     */
    private BigInteger chainId;

    /**
     * Nonce
     */
    private BigInteger nonce;

    /**
     * Gas价格
     * 每个Gas的价格，单位Wei
     */
    private BigInteger gasPrice;

    /**
     * Gas限制
     */
    private BigInteger gasLimit;

    /**
     * 接收者地址 (20 bytes)
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

    private transient byte[] cachedHash;
    private transient byte[] cachedSender;

    @Override
    public TransactionType getType() {
        return TransactionType.EIP2930;
    }

    @Override
    public boolean validate() {
        if (chainId == null || chainId.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }

        if (nonce == null || nonce.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        if (gasPrice == null || gasPrice.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        if (gasLimit == null || gasLimit.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }

        if (gasLimit.compareTo(getIntrinsicGas()) < 0) {
            return false;
        }

        if (to != null && to.length > 0 && to.length != 20) {
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
        // hash = keccak256(0x01 || rlp([chainId, nonce, gasPrice,
        //                              gasLimit, to, value, data,
        //                              accessList, v, r, s]))
        cachedHash = new byte[32];
        return cachedHash;
    }

    @Override
    public byte[] encode() {
        // TODO: 实现RLP编码
        return new byte[0];
    }

    /**
     * 计算访问列表的Gas成本
     * @return Gas成本
     */
    public BigInteger getAccessListGasCost() {
        if (accessList == null || accessList.isEmpty()) {
            return BigInteger.ZERO;
        }

        int addressCount = accessList.size();
        int storageKeyCount = accessList.stream()
            .mapToInt(AccessListEntry::getStorageKeyCount)
            .sum();

        long addressCost = addressCount * 2400L;
        long storageKeyCost = storageKeyCount * 1900L;

        return BigInteger.valueOf(addressCost + storageKeyCost);
    }

    @Override
    public BigInteger getIntrinsicGas() {
        BigInteger intrinsic = Transaction.super.getIntrinsicGas();
        intrinsic = intrinsic.add(getAccessListGasCost());
        return intrinsic;
    }

    /**
     * 获取Gas价格
     * EIP-2930使用固定gasPrice
     * @return Gas价格
     */
    public BigInteger getGasPrice() {
        return gasPrice;
    }
}
