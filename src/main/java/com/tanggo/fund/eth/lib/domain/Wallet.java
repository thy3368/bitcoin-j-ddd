package com.tanggo.fund.eth.lib.domain;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;

/**
 * 以太坊钱包领域模型（聚合根）
 *
 * 钱包负责管理私钥、地址派生和交易签名
 *
 * 支持两种钱包类型：
 * 1. 单密钥钱包（Simple Wallet）：单个私钥控制单个地址
 * 2. 分层确定性钱包（HD Wallet）：从助记词派生多个地址（BIP32/BIP39/BIP44）
 *
 * 核心职责：
 * - 密钥管理和安全存储
 * - 地址派生和管理
 * - 交易签名
 * - 余额查询（通过Repository）
 */
@Data
@Builder
public class Wallet {

    /**
     * 钱包ID（唯一标识）
     */
    private String walletId;

    /**
     * 钱包名称
     */
    private String name;

    /**
     * 钱包类型
     */
    private WalletType type;

    /**
     * 主私钥（单密钥钱包）或种子（HD钱包）
     * 敏感数据，需要加密存储
     */
    private byte[] masterSecret;

    /**
     * 助记词（仅HD钱包）
     * 敏感数据，需要加密存储
     */
    private String mnemonic;

    /**
     * HD钱包派生路径
     * 标准路径：m/44'/60'/0'/0（BIP44 - 以太坊）
     */
    private String derivationPath;

    /**
     * 管理的账户列表
     * Key: 地址索引
     * Value: 账户地址
     */
    @Builder.Default
    private Map<Integer, byte[]> accounts = new HashMap<>();

    /**
     * 当前活跃账户索引
     */
    @Builder.Default
    private int currentAccountIndex = 0;

    /**
     * 钱包创建时间
     */
    private long createdAt;

    /**
     * 最后访问时间
     */
    private long lastAccessedAt;

    /**
     * 是否锁定（需要密码解锁）
     */
    @Builder.Default
    private boolean locked = true;

    /**
     * 钱包类型枚举
     */
    public enum WalletType {
        /**
         * 单密钥钱包：一个私钥对应一个地址
         */
        SIMPLE,

        /**
         * HD钱包：分层确定性钱包，从助记词派生多个地址
         */
        HD,

        /**
         * 硬件钱包：私钥存储在硬件设备中
         */
        HARDWARE,

        /**
         * 多签钱包：需要多个签名者批准交易
         */
        MULTISIG
    }

    /**
     * 创建单密钥钱包
     *
     * @param name 钱包名称
     * @return 新创建的钱包
     */
    public static Wallet createSimpleWallet(String name) {
        // 生成随机私钥（32字节）
        SecureRandom random = new SecureRandom();
        byte[] privateKey = new byte[32];
        random.nextBytes(privateKey);

        // 从私钥派生地址
        byte[] address = deriveAddressFromPrivateKey(privateKey);

        Map<Integer, byte[]> accounts = new HashMap<>();
        accounts.put(0, address);

        return Wallet.builder()
            .walletId(UUID.randomUUID().toString())
            .name(name)
            .type(WalletType.SIMPLE)
            .masterSecret(privateKey)
            .accounts(accounts)
            .currentAccountIndex(0)
            .createdAt(System.currentTimeMillis())
            .lastAccessedAt(System.currentTimeMillis())
            .locked(false)
            .build();
    }

    /**
     * 从私钥导入单密钥钱包
     *
     * @param name       钱包名称
     * @param privateKey 私钥（32字节）
     * @return 导入的钱包
     */
    public static Wallet importFromPrivateKey(String name, byte[] privateKey) {
        if (privateKey == null || privateKey.length != 32) {
            throw new IllegalArgumentException("Private key must be 32 bytes");
        }

        byte[] address = deriveAddressFromPrivateKey(privateKey);

        Map<Integer, byte[]> accounts = new HashMap<>();
        accounts.put(0, address);

        return Wallet.builder()
            .walletId(UUID.randomUUID().toString())
            .name(name)
            .type(WalletType.SIMPLE)
            .masterSecret(privateKey)
            .accounts(accounts)
            .currentAccountIndex(0)
            .createdAt(System.currentTimeMillis())
            .lastAccessedAt(System.currentTimeMillis())
            .locked(false)
            .build();
    }

    /**
     * 创建HD钱包（分层确定性钱包）
     *
     * @param name     钱包名称
     * @param mnemonic 助记词（BIP39）
     * @param password 可选密码（用于额外保护）
     * @return 新创建的HD钱包
     */
    public static Wallet createHDWallet(String name, String mnemonic, String password) {
        if (mnemonic == null || mnemonic.trim().isEmpty()) {
            throw new IllegalArgumentException("Mnemonic cannot be null or empty");
        }

        // 验证助记词有效性（BIP39）
        if (!validateMnemonic(mnemonic)) {
            throw new IllegalArgumentException("Invalid mnemonic phrase");
        }

        // 从助记词生成种子（BIP39）
        byte[] seed = mnemonicToSeed(mnemonic, password != null ? password : "");

        // 标准以太坊派生路径：m/44'/60'/0'/0
        String derivationPath = "m/44'/60'/0'/0";

        // 派生第一个账户地址
        byte[] firstAddress = deriveAddressFromSeed(seed, derivationPath, 0);

        Map<Integer, byte[]> accounts = new HashMap<>();
        accounts.put(0, firstAddress);

        return Wallet.builder()
            .walletId(UUID.randomUUID().toString())
            .name(name)
            .type(WalletType.HD)
            .masterSecret(seed)
            .mnemonic(mnemonic)
            .derivationPath(derivationPath)
            .accounts(accounts)
            .currentAccountIndex(0)
            .createdAt(System.currentTimeMillis())
            .lastAccessedAt(System.currentTimeMillis())
            .locked(false)
            .build();
    }

    /**
     * 生成随机助记词（BIP39）
     *
     * @param wordCount 助记词数量（12, 15, 18, 21, 24）
     * @return 助记词
     */
    public static String generateMnemonic(int wordCount) {
        if (wordCount != 12 && wordCount != 15 && wordCount != 18 &&
            wordCount != 21 && wordCount != 24) {
            throw new IllegalArgumentException("Word count must be 12, 15, 18, 21, or 24");
        }

        // TODO: 实现BIP39助记词生成算法
        // 1. 生成熵（entropy）
        // 2. 计算校验和
        // 3. 将熵+校验和转换为助记词索引
        // 4. 从BIP39词表中选择对应单词

        // 暂时返回示例助记词（生产环境需要真实实现）
        return "abandon abandon abandon abandon abandon abandon " +
               "abandon abandon abandon abandon abandon about";
    }

    /**
     * 派生新账户地址（仅HD钱包）
     *
     * @return 新派生的地址
     */
    public byte[] deriveNextAccount() {
        if (type != WalletType.HD) {
            throw new UnsupportedOperationException("Only HD wallets support account derivation");
        }

        int nextIndex = accounts.size();
        byte[] newAddress = deriveAddressFromSeed(masterSecret, derivationPath, nextIndex);

        accounts.put(nextIndex, newAddress);
        currentAccountIndex = nextIndex;

        return newAddress;
    }

    /**
     * 获取指定索引的账户地址
     *
     * @param index 账户索引
     * @return 账户地址，如果不存在则返回null
     */
    public byte[] getAccount(int index) {
        return accounts.get(index);
    }

    /**
     * 获取当前活跃账户地址
     *
     * @return 当前账户地址
     */
    public byte[] getCurrentAccount() {
        return accounts.get(currentAccountIndex);
    }

    /**
     * 获取当前账户的私钥
     *
     * @return 私钥（32字节）
     */
    public byte[] getCurrentPrivateKey() {
        if (locked) {
            throw new IllegalStateException("Wallet is locked");
        }

        if (type == WalletType.SIMPLE) {
            return masterSecret;
        } else if (type == WalletType.HD) {
            // 从种子派生当前账户的私钥
            return derivePrivateKey(masterSecret, derivationPath, currentAccountIndex);
        }

        throw new UnsupportedOperationException("Unsupported wallet type");
    }

    /**
     * 签名交易
     *
     * @param transactionHash 交易哈希
     * @return 签名结果（包含r, s, v）
     */
    public Signature signTransaction(byte[] transactionHash) {
        if (locked) {
            throw new IllegalStateException("Wallet is locked, please unlock first");
        }

        if (transactionHash == null || transactionHash.length != 32) {
            throw new IllegalArgumentException("Transaction hash must be 32 bytes");
        }

        byte[] privateKey = getCurrentPrivateKey();

        // TODO: 实现ECDSA签名（secp256k1曲线）
        // 1. 使用私钥对交易哈希进行签名
        // 2. 生成r, s值
        // 3. 计算v值（恢复ID + chainId）

        // 暂时返回占位签名
        return new Signature(
            new byte[32], // r
            new byte[32], // s
            BigInteger.valueOf(27) // v
        );
    }

    /**
     * 锁定钱包
     */
    public void lock() {
        this.locked = true;
        this.lastAccessedAt = System.currentTimeMillis();
    }

    /**
     * 解锁钱包
     *
     * @param password 钱包密码
     * @return 是否解锁成功
     */
    public boolean unlock(String password) {
        // TODO: 实现密码验证逻辑
        // 1. 验证密码哈希
        // 2. 解密masterSecret
        // 3. 更新locked状态

        this.locked = false;
        this.lastAccessedAt = System.currentTimeMillis();
        return true;
    }

    /**
     * 验证钱包状态
     *
     * @return 是否有效
     */
    public boolean validate() {
        if (walletId == null || walletId.isEmpty()) {
            return false;
        }

        if (masterSecret == null || masterSecret.length == 0) {
            return false;
        }

        if (type == WalletType.HD) {
            if (mnemonic == null || mnemonic.isEmpty()) {
                return false;
            }
            if (derivationPath == null || derivationPath.isEmpty()) {
                return false;
            }
        }

        if (accounts.isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * 获取钱包中所有账户地址
     *
     * @return 地址列表
     */
    public List<byte[]> getAllAccounts() {
        return new ArrayList<>(accounts.values());
    }

    /**
     * 获取账户数量
     *
     * @return 账户数量
     */
    public int getAccountCount() {
        return accounts.size();
    }

    /**
     * 切换当前账户
     *
     * @param index 目标账户索引
     */
    public void switchAccount(int index) {
        if (!accounts.containsKey(index)) {
            throw new IllegalArgumentException("Account index does not exist: " + index);
        }
        this.currentAccountIndex = index;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 从私钥派生以太坊地址
     * address = keccak256(publicKey)[12:]
     *
     * @param privateKey 私钥（32字节）
     * @return 以太坊地址（20字节）
     */
    private static byte[] deriveAddressFromPrivateKey(byte[] privateKey) {
        // TODO: 实现地址派生
        // 1. 从私钥生成公钥（secp256k1椭圆曲线）
        // 2. 对公钥进行Keccak256哈希
        // 3. 取哈希结果的后20字节作为地址

        // 暂时返回占位地址
        return new byte[20];
    }

    /**
     * 从种子和派生路径派生地址（HD钱包）
     *
     * @param seed           种子
     * @param derivationPath 派生路径
     * @param accountIndex   账户索引
     * @return 派生的地址
     */
    private static byte[] deriveAddressFromSeed(byte[] seed, String derivationPath, int accountIndex) {
        // TODO: 实现BIP32 HD钱包派生
        // 1. 使用HMAC-SHA512从种子生成主私钥和链码
        // 2. 按照派生路径逐级派生子密钥
        // 3. 从最终子私钥派生地址

        // 暂时返回占位地址
        return new byte[20];
    }

    /**
     * 从种子派生私钥（HD钱包）
     *
     * @param seed           种子
     * @param derivationPath 派生路径
     * @param accountIndex   账户索引
     * @return 派生的私钥
     */
    private static byte[] derivePrivateKey(byte[] seed, String derivationPath, int accountIndex) {
        // TODO: 实现BIP32私钥派生
        return new byte[32];
    }

    /**
     * 验证助记词有效性（BIP39）
     *
     * @param mnemonic 助记词
     * @return 是否有效
     */
    private static boolean validateMnemonic(String mnemonic) {
        if (mnemonic == null || mnemonic.trim().isEmpty()) {
            return false;
        }

        String[] words = mnemonic.trim().split("\\s+");
        int wordCount = words.length;

        // 助记词必须是12, 15, 18, 21, 或 24个单词
        if (wordCount != 12 && wordCount != 15 && wordCount != 18 &&
            wordCount != 21 && wordCount != 24) {
            return false;
        }

        // TODO: 验证每个单词是否在BIP39词表中
        // TODO: 验证校验和

        return true;
    }

    /**
     * 从助记词生成种子（BIP39）
     *
     * @param mnemonic 助记词
     * @param password 可选密码
     * @return 种子（64字节）
     */
    private static byte[] mnemonicToSeed(String mnemonic, String password) {
        // TODO: 实现BIP39种子生成
        // 1. 规范化助记词和密码（NFKD）
        // 2. 使用PBKDF2-HMAC-SHA512派生种子
        // 参数：密码=mnemonic, 盐="mnemonic"+password, 迭代=2048, 输出=64字节

        // 暂时返回占位种子
        return new byte[64];
    }

    /**
     * 签名结果
     */
    @Data
    @Builder
    public static class Signature {
        /**
         * 签名r值
         */
        private byte[] r;

        /**
         * 签名s值
         */
        private byte[] s;

        /**
         * 签名v值（恢复ID）
         */
        private BigInteger v;

        public Signature(byte[] r, byte[] s, BigInteger v) {
            this.r = r;
            this.s = s;
            this.v = v;
        }
    }
}