package com.tanggo.fund.eth.lib.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 以太坊交易回执（Receipt）
 * 包含交易执行后的所有信息，包括状态、Gas使用、日志等
 * 遵循以太坊黄皮书和各种EIP规范
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Receipt {

    /**
     * 交易哈希 (32 bytes)
     * 对应交易的唯一标识
     */
    private byte[] transactionHash;

    /**
     * 交易在区块中的索引位置
     * 从0开始
     */
    private BigInteger transactionIndex;

    /**
     * 区块哈希 (32 bytes)
     * 包含此交易的区块哈希
     */
    private byte[] blockHash;

    /**
     * 区块号
     * 包含此交易的区块号
     */
    private BigInteger blockNumber;

    /**
     * 发送方地址 (20 bytes)
     * 交易的发起者地址
     */
    private byte[] from;

    /**
     * 接收方地址 (20 bytes)
     * 交易的接收者地址
     * 合约创建交易时为null
     */
    private byte[] to;

    /**
     * 累计Gas使用量
     * 区块中从第一笔交易到当前交易（包括）的累计Gas使用量
     */
    private BigInteger cumulativeGasUsed;

    /**
     * 本次交易实际使用的Gas
     * gasUsed = cumulativeGasUsed - 前一笔交易的cumulativeGasUsed
     */
    private BigInteger gasUsed;

    /**
     * 合约地址 (20 bytes)
     * 如果是合约创建交易，这里是新创建的合约地址
     * 普通交易时为null
     */
    private byte[] contractAddress;

    /**
     * 事件日志列表
     * 交易执行期间触发的所有事件
     */
    @Builder.Default
    private List<Log> logs = new ArrayList<>();

    /**
     * 日志布隆过滤器 (256 bytes)
     * 用于快速查找和过滤日志
     */
    private byte[] logsBloom;

    /**
     * 交易状态
     * 1 = 成功
     * 0 = 失败
     * null表示拜占庭升级之前的区块（使用root字段）
     */
    private Integer status;

    /**
     * 状态根 (32 bytes)
     * 拜占庭升级之前使用，表示交易后的状态根
     * 拜占庭升级后使用status字段，此字段为null
     */
    private byte[] root;

    /**
     * 实际生效的Gas价格
     * EIP-1559后：baseFeePerGas + min(maxPriorityFeePerGas, maxFeePerGas - baseFeePerGas)
     * EIP-1559前：交易的gasPrice
     */
    private BigInteger effectiveGasPrice;

    /**
     * 交易类型（EIP-2718）
     * 0 = Legacy交易
     * 1 = EIP-2930 (访问列表)
     * 2 = EIP-1559 (动态费用)
     * 3 = EIP-4844 (Blob交易)
     */
    private Integer type;

    /**
     * 验证回执的有效性
     * @return 是否有效
     */
    public boolean validate() {
        // 基本字段检查
        if (transactionHash == null || transactionHash.length != 32) {
            return false;
        }
        if (transactionIndex == null || transactionIndex.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (blockHash == null || blockHash.length != 32) {
            return false;
        }
        if (blockNumber == null || blockNumber.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (from == null || from.length != 20) {
            return false;
        }
        // to可以为null（合约创建）
        if (to != null && to.length != 20) {
            return false;
        }

        // Gas相关检查
        if (cumulativeGasUsed == null || cumulativeGasUsed.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (gasUsed == null || gasUsed.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        if (gasUsed.compareTo(cumulativeGasUsed) > 0) {
            return false; // gasUsed不能超过cumulativeGasUsed
        }

        // 合约地址检查
        if (contractAddress != null && contractAddress.length != 20) {
            return false;
        }

        // 日志布隆过滤器检查
        if (logsBloom == null || logsBloom.length != 256) {
            return false;
        }

        // 状态检查：拜占庭升级后使用status，之前使用root
        // 两者不能同时存在
        if (status != null && root != null) {
            return false;
        }
        if (status == null && root == null) {
            return false;
        }

        // status只能是0或1
        if (status != null && (status != 0 && status != 1)) {
            return false;
        }

        // root必须是32字节
        if (root != null && root.length != 32) {
            return false;
        }

        // effectiveGasPrice检查
        if (effectiveGasPrice != null && effectiveGasPrice.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // 交易类型检查
        if (type != null && (type < 0 || type > 3)) {
            return false;
        }

        // 验证所有日志
        if (logs != null) {
            for (Log log : logs) {
                if (log == null || !log.validate()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 判断交易是否成功
     * @return 是否成功
     */
    public boolean isSuccess() {
        // 拜占庭升级后使用status判断
        if (status != null) {
            return status == 1;
        }
        // 拜占庭升级前，如果有root字段，认为是成功的
        // （失败的交易在早期也会有root，需要结合其他信息判断）
        return root != null;
    }

    /**
     * 判断是否为合约创建交易
     * @return 是否为合约创建交易
     */
    public boolean isContractCreation() {
        return contractAddress != null;
    }

    /**
     * 判断是否有日志
     * @return 是否有日志
     */
    public boolean hasLogs() {
        return logs != null && !logs.isEmpty();
    }

    /**
     * 获取日志数量
     * @return 日志数量
     */
    public int getLogCount() {
        return logs != null ? logs.size() : 0;
    }

    /**
     * 判断是否为拜占庭升级后的回执
     * @return 是否为拜占庭升级后
     */
    public boolean isPostByzantium() {
        return status != null;
    }

    /**
     * 判断是否为Legacy交易（类型0）
     * @return 是否为Legacy交易
     */
    public boolean isLegacyTransaction() {
        return type == null || type == 0;
    }

    /**
     * 判断是否为EIP-1559交易（类型2）
     * @return 是否为EIP-1559交易
     */
    public boolean isEip1559Transaction() {
        return type != null && type == 2;
    }

    /**
     * 判断是否为Blob交易（类型3，EIP-4844）
     * @return 是否为Blob交易
     */
    public boolean isBlobTransaction() {
        return type != null && type == 3;
    }

    /**
     * 事件日志
     * 表示合约执行期间触发的事件
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Log {

        /**
         * 日志所属合约地址 (20 bytes)
         */
        private byte[] address;

        /**
         * 日志主题列表
         * 第一个主题通常是事件签名的keccak256哈希
         * 后续主题是indexed参数的值
         * 最多4个主题
         */
        @Builder.Default
        private List<byte[]> topics = new ArrayList<>();

        /**
         * 日志数据
         * 包含非indexed参数的ABI编码数据
         */
        private byte[] data;

        /**
         * 区块号
         * 日志所在区块的编号
         */
        private BigInteger blockNumber;

        /**
         * 区块哈希 (32 bytes)
         * 日志所在区块的哈希
         */
        private byte[] blockHash;

        /**
         * 交易哈希 (32 bytes)
         * 触发此日志的交易哈希
         */
        private byte[] transactionHash;

        /**
         * 交易索引
         * 交易在区块中的位置
         */
        private BigInteger transactionIndex;

        /**
         * 日志索引
         * 日志在区块中的全局索引
         */
        private BigInteger logIndex;

        /**
         * 是否被移除
         * 链重组时，日志可能被标记为removed
         */
        @Builder.Default
        private boolean removed = false;

        /**
         * 验证日志的有效性
         * @return 是否有效
         */
        public boolean validate() {
            // 地址必须是20字节
            if (address == null || address.length != 20) {
                return false;
            }

            // 主题检查
            if (topics == null) {
                return false;
            }
            // 最多4个主题
            if (topics.size() > 4) {
                return false;
            }
            // 每个主题必须是32字节
            for (byte[] topic : topics) {
                if (topic == null || topic.length != 32) {
                    return false;
                }
            }

            // data可以为空，但不能为null
            if (data == null) {
                return false;
            }

            // 区块号检查
            if (blockNumber != null && blockNumber.compareTo(BigInteger.ZERO) < 0) {
                return false;
            }

            // 区块哈希检查
            if (blockHash != null && blockHash.length != 32) {
                return false;
            }

            // 交易哈希检查
            if (transactionHash != null && transactionHash.length != 32) {
                return false;
            }

            // 交易索引检查
            if (transactionIndex != null && transactionIndex.compareTo(BigInteger.ZERO) < 0) {
                return false;
            }

            // 日志索引检查
            if (logIndex != null && logIndex.compareTo(BigInteger.ZERO) < 0) {
                return false;
            }

            return true;
        }

        /**
         * 获取主题数量
         * @return 主题数量
         */
        public int getTopicCount() {
            return topics != null ? topics.size() : 0;
        }

        /**
         * 判断是否有数据
         * @return 是否有数据
         */
        public boolean hasData() {
            return data != null && data.length > 0;
        }

        /**
         * 获取事件签名（第一个主题）
         * @return 事件签名，如果没有主题则返回null
         */
        public byte[] getEventSignature() {
            return (topics != null && !topics.isEmpty()) ? topics.get(0) : null;
        }
    }
}