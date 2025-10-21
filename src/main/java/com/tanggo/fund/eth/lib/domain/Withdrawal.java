package com.tanggo.fund.eth.lib.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * 以太坊提款对象 - EIP-4895
 * 用于验证者从信标链提款到执行层
 * 在上海升级（2023年4月）后引入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Withdrawal {

    /**
     * 提款索引
     * 单调递增的全局提款计数器
     */
    private BigInteger index;

    /**
     * 验证者索引
     * 进行提款的验证者在信标链上的索引
     */
    private BigInteger validatorIndex;

    /**
     * 接收地址 (20 bytes)
     * 接收提款的以太坊地址
     */
    private byte[] address;

    /**
     * 提款金额
     * 单位：Gwei（1 Gwei = 10^9 Wei）
     */
    private BigInteger amount;

    /**
     * 验证提款对象的有效性
     * @return 是否有效
     */
    public boolean validate() {
        // 索引不能为null且必须非负
        if (index == null || index.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // 验证者索引不能为null且必须非负
        if (validatorIndex == null || validatorIndex.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // 地址必须是20字节
        if (address == null || address.length != 20) {
            return false;
        }

        // 金额不能为null且必须非负
        if (amount == null || amount.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        return true;
    }

    /**
     * 将Gwei转换为Wei
     * @return Wei金额
     */
    public BigInteger getAmountInWei() {
        if (amount == null) {
            return BigInteger.ZERO;
        }
        // 1 Gwei = 10^9 Wei
        return amount.multiply(BigInteger.valueOf(1_000_000_000L));
    }

    /**
     * 判断是否为全额提款（32 ETH或更多）
     * 全额提款表示验证者完全退出
     * @return 是否为全额提款
     */
    public boolean isFullWithdrawal() {
        if (amount == null) {
            return false;
        }
        // 32 ETH = 32,000,000,000 Gwei
        BigInteger fullWithdrawalThreshold = BigInteger.valueOf(32_000_000_000L);
        return amount.compareTo(fullWithdrawalThreshold) >= 0;
    }

    /**
     * 判断是否为部分提款（少于32 ETH）
     * 部分提款是验证者余额超过32 ETH后的奖励提取
     * @return 是否为部分提款
     */
    public boolean isPartialWithdrawal() {
        return !isFullWithdrawal() && amount != null && amount.compareTo(BigInteger.ZERO) > 0;
    }
}