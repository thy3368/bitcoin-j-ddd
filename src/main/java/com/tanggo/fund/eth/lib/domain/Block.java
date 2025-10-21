package com.tanggo.fund.eth.lib.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * 以太坊区块
 * 完整的以太坊区块结构，包含区块头和区块体
 * 遵循以太坊黄皮书规范和各种EIP升级
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Block {

    /**
     * 区块头
     * 包含区块的元数据和各种默克尔根
     */
    private Header header;

    /**
     * 区块体
     * 包含交易、叔块和提款列表
     */
    private BlockBody blockBody;

    /**
     * 验证完整区块的有效性
     * 包括区块头和区块体的验证
     * @return 是否有效
     */
    public boolean validate() {
        // 区块头不能为null且必须有效
        if (header == null || !header.validate()) {
            return false;
        }

        // 区块体不能为null且必须有效
        if (blockBody == null || !blockBody.validate()) {
            return false;
        }

        // 验证叔块数量与区块头的一致性
        // 叔块哈希应该是叔块列表的RLP编码的Keccak256哈希
        // 这里简化验证，只检查叔块数量限制
        if (blockBody.getOmmerCount() > 2) {
            return false;
        }

        // 验证提款与区块头的一致性
        // 如果区块头有withdrawalsRoot，则区块体必须有withdrawals列表
        if (header.supportsWithdrawals() && blockBody.getWithdrawals() == null) {
            return false;
        }

        // 如果区块头没有withdrawalsRoot，则区块体不应该有withdrawals
        if (!header.supportsWithdrawals() && blockBody.hasWithdrawals()) {
            return false;
        }

        return true;
    }

    /**
     * 获取区块号
     * @return 区块号
     */
    public BigInteger getBlockNumber() {
        return header != null ? header.getNumber() : null;
    }

    /**
     * 获取区块时间戳
     * @return Unix时间戳（秒）
     */
    public long getTimestamp() {
        return header != null ? header.getTimestamp() : 0L;
    }

    /**
     * 获取交易数量
     * @return 交易数量
     */
    public int getTransactionCount() {
        return blockBody != null ? blockBody.getTransactionCount() : 0;
    }

    /**
     * 获取叔块数量
     * @return 叔块数量
     */
    public int getOmmerCount() {
        return blockBody != null ? blockBody.getOmmerCount() : 0;
    }

    /**
     * 判断是否为创世区块
     * @return 是否为创世区块
     */
    public boolean isGenesis() {
        return header != null && header.isGenesis();
    }

    /**
     * 判断是否为空区块（没有交易）
     * @return 是否为空区块
     */
    public boolean isEmpty() {
        return getTransactionCount() == 0;
    }

    /**
     * 判断是否支持EIP-1559
     * @return 是否支持EIP-1559
     */
    public boolean supportsEip1559() {
        return header != null && header.supportsEip1559();
    }

    /**
     * 判断是否支持提款（上海升级）
     * @return 是否支持提款
     */
    public boolean supportsWithdrawals() {
        return header != null && header.supportsWithdrawals();
    }

    /**
     * 判断是否支持Blob交易（Cancun升级）
     * @return 是否支持Blob交易
     */
    public boolean supportsBlobTransactions() {
        return header != null && header.supportsBlobTransactions();
    }

    /**
     * 获取区块的Gas利用率
     * @return Gas利用率（0.0 - 1.0），如果gasLimit为0则返回0
     */
    public double getGasUtilization() {
        if (header == null || header.getGasLimit() == null ||
            header.getGasLimit().equals(BigInteger.ZERO)) {
            return 0.0;
        }

        if (header.getGasUsed() == null) {
            return 0.0;
        }

        return header.getGasUsed().doubleValue() / header.getGasLimit().doubleValue();
    }

    /**
     * 获取基础费用（如果支持EIP-1559）
     * @return 基础费用，不支持则返回null
     */
    public BigInteger getBaseFeePerGas() {
        return header != null ? header.getBaseFeePerGas() : null;
    }

    /**
     * 判断是否为PoS区块（合并后）
     * PoS区块的难度为0
     * @return 是否为PoS区块
     */
    public boolean isProofOfStake() {
        return header != null &&
               header.getDifficulty() != null &&
               header.getDifficulty().equals(BigInteger.ZERO);
    }

    /**
     * 判断是否为PoW区块（合并前）
     * @return 是否为PoW区块
     */
    public boolean isProofOfWork() {
        return !isProofOfStake();
    }
}
