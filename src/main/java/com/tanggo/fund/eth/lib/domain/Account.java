package com.tanggo.fund.eth.lib.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * 以太坊账户
 *
 * 以太坊有两种账户类型：
 * 1. 外部账户（EOA - Externally Owned Account）：由私钥控制
 * 2. 合约账户（Contract Account）：由合约代码控制
 *
 * 账户状态包含四个字段（存储在状态树中）：
 * - nonce: 交易计数器（EOA）或创建的合约数量（合约账户）
 * - balance: Wei余额
 * - storageRoot: 存储树根哈希（仅合约账户）
 * - codeHash: 合约代码的Keccak256哈希
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    /**
     * 账户地址 (20 bytes)
     * 唯一标识账户
     */
    private byte[] address;

    /**
     * Nonce
     * - EOA: 从该账户发送的交易数量
     * - 合约账户: 该账户创建的合约数量
     */
    @Builder.Default
    private BigInteger nonce = BigInteger.ZERO;

    /**
     * 余额
     * 账户拥有的Wei数量（1 ETH = 10^18 Wei）
     */
    @Builder.Default
    private BigInteger balance = BigInteger.ZERO;

    /**
     * 存储根 (32 bytes)
     * 账户存储树的默克尔根哈希
     * - EOA: 空树的哈希
     * - 合约账户: 存储数据的树根
     */
    private byte[] storageRoot;

    /**
     * 代码哈希 (32 bytes)
     * 账户代码的Keccak256哈希
     * - EOA: 空字符串的哈希 (0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470)
     * - 合约账户: 合约字节码的哈希
     */
    private byte[] codeHash;

    /**
     * 空代码哈希常量
     * keccak256("") = 0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470
     */
    public static final byte[] EMPTY_CODE_HASH = hexToBytes(
        "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
    );

    /**
     * 空存储根常量
     * 空MPT树的根哈希
     */
    public static final byte[] EMPTY_STORAGE_ROOT = hexToBytes(
        "56e81f171bcc55a6ff8345e692c0f86e5b1e1b19c5d0b2c0ccb39e6e3f34e4ca"
    );

    /**
     * 验证账户状态的有效性
     * @return 是否有效
     */
    public boolean validate() {
        // 地址必须是20字节
        if (address == null || address.length != 20) {
            return false;
        }

        // Nonce必须非负
        if (nonce == null || nonce.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // 余额必须非负
        if (balance == null || balance.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }

        // 存储根必须是32字节（如果存在）
        if (storageRoot != null && storageRoot.length != 32) {
            return false;
        }

        // 代码哈希必须是32字节（如果存在）
        if (codeHash != null && codeHash.length != 32) {
            return false;
        }

        return true;
    }

    /**
     * 判断是否为外部账户（EOA）
     * EOA的codeHash为空字符串的哈希
     * @return 是否为EOA
     */
    public boolean isExternallyOwnedAccount() {
        if (codeHash == null) {
            return true; // 默认为EOA
        }
        return Arrays.equals(codeHash, EMPTY_CODE_HASH);
    }

    /**
     * 判断是否为合约账户
     * @return 是否为合约账户
     */
    public boolean isContractAccount() {
        return !isExternallyOwnedAccount();
    }

    /**
     * 判断账户是否为空
     * 空账户定义：nonce=0, balance=0, codeHash为空
     * @return 是否为空账户
     */
    public boolean isEmpty() {
        boolean nonceZero = nonce == null || nonce.equals(BigInteger.ZERO);
        boolean balanceZero = balance == null || balance.equals(BigInteger.ZERO);
        boolean codeEmpty = codeHash == null || Arrays.equals(codeHash, EMPTY_CODE_HASH);

        return nonceZero && balanceZero && codeEmpty;
    }

    /**
     * 增加余额
     * @param amount 增加的金额（Wei）
     * @return 新余额
     */
    public BigInteger credit(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Credit amount must be non-negative");
        }

        if (balance == null) {
            balance = BigInteger.ZERO;
        }

        balance = balance.add(amount);
        return balance;
    }

    /**
     * 减少余额
     * @param amount 减少的金额（Wei）
     * @return 新余额
     * @throws IllegalArgumentException 如果余额不足
     */
    public BigInteger debit(BigInteger amount) {
        if (amount == null || amount.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Debit amount must be non-negative");
        }

        if (balance == null) {
            balance = BigInteger.ZERO;
        }

        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        balance = balance.subtract(amount);
        return balance;
    }

    /**
     * 递增nonce
     * @return 新的nonce值
     */
    public BigInteger incrementNonce() {
        if (nonce == null) {
            nonce = BigInteger.ZERO;
        }
        nonce = nonce.add(BigInteger.ONE);
        return nonce;
    }

    /**
     * 检查是否有足够的余额
     * @param amount 需要的金额
     * @return 是否有足够余额
     */
    public boolean hasSufficientBalance(BigInteger amount) {
        if (amount == null || balance == null) {
            return false;
        }
        return balance.compareTo(amount) >= 0;
    }

    /**
     * 获取余额（以Ether为单位）
     * @return Ether余额
     */
    public double getBalanceInEther() {
        if (balance == null) {
            return 0.0;
        }
        // 1 ETH = 10^18 Wei
        BigInteger weiPerEther = new BigInteger("1000000000000000000");
        return balance.doubleValue() / weiPerEther.doubleValue();
    }

    /**
     * 获取余额（以Gwei为单位）
     * @return Gwei余额
     */
    public BigInteger getBalanceInGwei() {
        if (balance == null) {
            return BigInteger.ZERO;
        }
        // 1 Gwei = 10^9 Wei
        return balance.divide(BigInteger.valueOf(1_000_000_000L));
    }

    /**
     * 创建一个新的EOA账户
     * @param address 账户地址
     * @return EOA账户
     */
    public static Account createEOA(byte[] address) {
        return Account.builder()
            .address(address)
            .nonce(BigInteger.ZERO)
            .balance(BigInteger.ZERO)
            .storageRoot(EMPTY_STORAGE_ROOT)
            .codeHash(EMPTY_CODE_HASH)
            .build();
    }

    /**
     * 创建一个新的合约账户
     * @param address 合约地址
     * @param codeHash 合约代码哈希
     * @return 合约账户
     */
    public static Account createContract(byte[] address, byte[] codeHash) {
        return Account.builder()
            .address(address)
            .nonce(BigInteger.ONE) // 合约创建时nonce为1
            .balance(BigInteger.ZERO)
            .storageRoot(EMPTY_STORAGE_ROOT)
            .codeHash(codeHash)
            .build();
    }

    /**
     * 计算合约地址（CREATE操作码）
     * address = keccak256(rlp([sender_address, sender_nonce]))[12:]
     * @param senderAddress 发送者地址
     * @param senderNonce 发送者nonce
     * @return 合约地址（20字节）
     */
    public static byte[] calculateContractAddress(byte[] senderAddress, BigInteger senderNonce) {
        // TODO: 实现RLP编码和Keccak256哈希
        // 暂时返回占位值
        return new byte[20];
    }

    /**
     * 计算CREATE2合约地址（CREATE2操作码）
     * address = keccak256(0xff ++ sender_address ++ salt ++ keccak256(init_code))[12:]
     * @param senderAddress 发送者地址
     * @param salt 32字节盐值
     * @param initCodeHash 初始化代码的哈希
     * @return 合约地址（20字节）
     */
    public static byte[] calculateCreate2Address(byte[] senderAddress, byte[] salt, byte[] initCodeHash) {
        // TODO: 实现CREATE2地址计算
        // 暂时返回占位值
        return new byte[20];
    }

    /**
     * 辅助方法：十六进制字符串转字节数组
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 获取地址的十六进制字符串表示
     * @return 0x开头的地址字符串
     */
    public String getAddressHex() {
        if (address == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : address) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 克隆账户（用于状态转换）
     * @return 账户副本
     */
    public Account copy() {
        return Account.builder()
            .address(address != null ? Arrays.copyOf(address, address.length) : null)
            .nonce(nonce)
            .balance(balance)
            .storageRoot(storageRoot != null ? Arrays.copyOf(storageRoot, storageRoot.length) : null)
            .codeHash(codeHash != null ? Arrays.copyOf(codeHash, codeHash.length) : null)
            .build();
    }
}
