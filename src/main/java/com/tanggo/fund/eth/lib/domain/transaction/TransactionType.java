package com.tanggo.fund.eth.lib.domain.transaction;

/**
 * 以太坊交易类型枚举
 */
public enum TransactionType {

    /**
     * Type 0: Legacy Transaction (Homestead之前)
     * 使用gasPrice
     */
    LEGACY(0, "Legacy"),

    /**
     * Type 1: EIP-2930 Access List Transaction (Berlin升级)
     * 引入访问列表优化Gas成本
     */
    EIP2930(1, "EIP-2930"),

    /**
     * Type 2: EIP-1559 Dynamic Fee Transaction (London升级)
     * 引入baseFeePerGas和maxPriorityFeePerGas
     */
    EIP1559(2, "EIP-1559"),

    /**
     * Type 3: EIP-4844 Blob Transaction (Cancun升级)
     * 引入blob承载数据，降低L2成本
     */
    EIP4844(3, "EIP-4844");

    private final int typeId;
    private final String name;

    TransactionType(int typeId, String name) {
        this.typeId = typeId;
        this.name = name;
    }

    public int getTypeId() {
        return typeId;
    }

    public String getName() {
        return name;
    }

    /**
     * 从类型ID获取交易类型
     * @param typeId 类型ID
     * @return 交易类型
     */
    public static TransactionType fromTypeId(int typeId) {
        for (TransactionType type : values()) {
            if (type.typeId == typeId) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown transaction type: " + typeId);
    }

    /**
     * 判断是否支持访问列表
     * @return 是否支持访问列表
     */
    public boolean supportsAccessList() {
        return this == EIP2930 || this == EIP1559 || this == EIP4844;
    }

    /**
     * 判断是否使用EIP-1559费用机制
     * @return 是否使用EIP-1559费用
     */
    public boolean supportsEip1559Fees() {
        return this == EIP1559 || this == EIP4844;
    }

    /**
     * 判断是否支持Blob数据
     * @return 是否支持Blob
     */
    public boolean supportsBlobs() {
        return this == EIP4844;
    }

    @Override
    public String toString() {
        return String.format("Type %d: %s", typeId, name);
    }
}