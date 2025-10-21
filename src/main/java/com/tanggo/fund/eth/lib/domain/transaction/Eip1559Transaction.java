package com.tanggo.fund.eth.lib.domain.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * EIP-1559交易 (Type 2)
 * London升级引入的动态费用交易
 *
 * 特性：
 * - 引入baseFeePerGas（由协议自动调整）
 * - 引入maxPriorityFeePerGas（给矿工的小费）
 * - maxFeePerGas = baseFeePerGas + maxPriorityFeePerGas
 * - 支持访问列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Eip1559Transaction implements Transaction {

    /**
     * 链ID
     * 用于防止重放攻击
     */
    private BigInteger chainId;

    /**
     * Nonce
     * 发送者账户的交易序号
     */
    private BigInteger nonce;

    /**
     * 最大优先费用（小费）
     * 愿意支付给矿工的每个Gas的最大小费，单位Wei
     */
    private BigInteger maxPriorityFeePerGas;

    /**
     * 最大费用
     * 愿意支付的每个Gas的最大总费用，单位Wei
     * maxFeePerGas >= baseFeePerGas + maxPriorityFeePerGas
     */
    private BigInteger maxFeePerGas;

    /**
     * Gas限制
     * 交易允许消耗的最大Gas量
     */
    private BigInteger gasLimit;

    /**
     * 接收者地址 (20 bytes)
     * null或空数组表示合约创建交易
     */
    private byte[] to;

    /**
     * 转账金额
     * 单位Wei
     */
    private BigInteger value;

    /**
     * 交易数据
     * 合约调用的输入数据或合约创建的字节码
     */
    private byte[] data;

    /**
     * 访问列表
     * 预声明将访问的账户和存储槽，优化Gas成本
     */
    @Builder.Default
    private List<AccessListEntry> accessList = new ArrayList<>();

    /**
     * 签名V值
     * 恢复ID，取值0或1（EIP-155后）
     */
    private BigInteger v;

    /**
     * 签名R值
     * ECDSA签名的R分量
     */
    private BigInteger r;

    /**
     * 签名S值
     * ECDSA签名的S分量
     */
    private BigInteger s;

    /**
     * 缓存的交易哈希
     */
    private transient byte[] cachedHash;

    /**
     * 缓存的发送者地址
     */
    private transient byte[] cachedSender;

    @Override
    public TransactionType getType() {
        return TransactionType.EIP1559;
    }

    @Override
    public boolean validate() {
        // 链ID必须为正数
        if (chainId == null || chainId.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }

        // Nonce必须非负
        if (nonce == null || nonce.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // 费用字段验证
        if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (maxFeePerGas == null || maxFeePerGas.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // maxFeePerGas必须 >= maxPriorityFeePerGas
        if (maxFeePerGas.compareTo(maxPriorityFeePerGas) < 0) {
            return false;
        }

        // Gas限制必须为正数
        if (gasLimit == null || gasLimit.compareTo(BigInteger.ZERO) <= 0) {
            return false;
        }

        // Gas限制必须足够支付内在成本
        if (gasLimit.compareTo(getIntrinsicGas()) < 0) {
            return false;
        }

        // 接收者地址验证（如果不是合约创建）
        if (to != null && to.length > 0 && to.length != 20) {
            return false;
        }

        // 转账金额必须非负
        if (value == null || value.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // 验证访问列表
        if (accessList != null) {
            for (AccessListEntry entry : accessList) {
                if (entry == null || !entry.validate()) {
                    return false;
                }
            }
        }

        // 签名验证
        if (v == null || r == null || s == null) {
            return false;
        }

        // V值必须是0或1（对于EIP-1559）
        if (!v.equals(BigInteger.ZERO) && !v.equals(BigInteger.ONE)) {
            return false;
        }

        // R和S必须在有效范围内
        BigInteger secp256k1N = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);

        if (r.compareTo(BigInteger.ZERO) <= 0 || r.compareTo(secp256k1N) >= 0) {
            return false;
        }
        if (s.compareTo(BigInteger.ZERO) <= 0 || s.compareTo(secp256k1N) >= 0) {
            return false;
        }

        // EIP-2检查：S值必须 <= secp256k1n/2（防止签名延展性）
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

        // 恢复发送者地址
        byte[] sender = recoverSender();
        if (sender == null || sender.length != 20) {
            return false;
        }

        // 缓存发送者地址
        cachedSender = sender;
        return true;
    }

    @Override
    public byte[] recoverSender() {
        if (cachedSender != null) {
            return cachedSender;
        }

        // TODO: 实现ECDSA签名恢复
        // 1. 构造签名消息哈希
        // 2. 使用v, r, s恢复公钥
        // 3. 从公钥计算地址

        // 占位实现，实际需要使用ECDSA库
        return new byte[20];
    }

    @Override
    public byte[] getTransactionHash() {
        if (cachedHash != null) {
            return cachedHash;
        }

        // TODO: 实现Keccak256哈希计算
        // hash = keccak256(0x02 || rlp([chainId, nonce, maxPriorityFeePerGas,
        //                              maxFeePerGas, gasLimit, to, value, data,
        //                              accessList, v, r, s]))

        // 占位实现
        cachedHash = new byte[32];
        return cachedHash;
    }

    @Override
    public byte[] encode() {
        // TODO: 实现RLP编码
        // 格式：0x02 || rlp([chainId, nonce, maxPriorityFeePerGas,
        //                    maxFeePerGas, gasLimit, to, value, data,
        //                    accessList, v, r, s])
        return new byte[0];
    }

    /**
     * 计算实际支付的Gas价格
     * effectiveGasPrice = min(maxFeePerGas, baseFeePerGas + maxPriorityFeePerGas)
     *
     * @param baseFeePerGas 区块的基础费用
     * @return 实际Gas价格
     */
    public BigInteger getEffectiveGasPrice(BigInteger baseFeePerGas) {
        if (baseFeePerGas == null) {
            return maxFeePerGas;
        }

        BigInteger priorityFee = maxPriorityFeePerGas;
        BigInteger effectivePriorityFee = maxFeePerGas.subtract(baseFeePerGas);

        if (effectivePriorityFee.compareTo(priorityFee) > 0) {
            effectivePriorityFee = priorityFee;
        }

        return baseFeePerGas.add(effectivePriorityFee);
    }

    /**
     * 计算实际支付给矿工的小费
     * effectivePriorityFee = min(maxPriorityFeePerGas, maxFeePerGas - baseFeePerGas)
     *
     * @param baseFeePerGas 区块的基础费用
     * @return 实际小费
     */
    public BigInteger getEffectivePriorityFee(BigInteger baseFeePerGas) {
        if (baseFeePerGas == null) {
            return maxPriorityFeePerGas;
        }

        BigInteger maxPriorityFee = maxFeePerGas.subtract(baseFeePerGas);
        return maxPriorityFee.compareTo(maxPriorityFeePerGas) < 0
            ? maxPriorityFee
            : maxPriorityFeePerGas;
    }

    /**
     * 计算交易的最大成本
     * maxCost = value + (gasLimit * maxFeePerGas)
     *
     * @return 最大成本（Wei）
     */
    public BigInteger getMaxTransactionCost() {
        BigInteger gasCost = gasLimit.multiply(maxFeePerGas);
        return value.add(gasCost);
    }

    /**
     * 判断交易是否可以包含在具有给定baseFee的区块中
     *
     * @param baseFeePerGas 区块的基础费用
     * @return 是否可以包含
     */
    public boolean canBeIncludedIn(BigInteger baseFeePerGas) {
        if (baseFeePerGas == null) {
            return true;
        }
        // maxFeePerGas必须 >= baseFeePerGas
        return maxFeePerGas.compareTo(baseFeePerGas) >= 0;
    }

    /**
     * 获取访问列表中的地址数量
     * @return 地址数量
     */
    public int getAccessListAddressCount() {
        return accessList != null ? accessList.size() : 0;
    }

    /**
     * 获取访问列表中的总存储键数量
     * @return 存储键总数
     */
    public int getAccessListStorageKeyCount() {
        if (accessList == null || accessList.isEmpty()) {
            return 0;
        }
        return accessList.stream()
            .mapToInt(AccessListEntry::getStorageKeyCount)
            .sum();
    }

    /**
     * 计算访问列表的Gas成本
     * 每个地址：2400 gas
     * 每个存储键：1900 gas
     *
     * @return 访问列表Gas成本
     */
    public BigInteger getAccessListGasCost() {
        int addressCount = getAccessListAddressCount();
        int storageKeyCount = getAccessListStorageKeyCount();

        long addressCost = addressCount * 2400L;
        long storageKeyCost = storageKeyCount * 1900L;

        return BigInteger.valueOf(addressCost + storageKeyCost);
    }

    @Override
    public BigInteger getIntrinsicGas() {
        // 基础内在成本
        BigInteger intrinsic = Transaction.super.getIntrinsicGas();

        // 添加访问列表成本
        intrinsic = intrinsic.add(getAccessListGasCost());

        return intrinsic;
    }
}
