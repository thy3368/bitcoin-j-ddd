package com.tanggo.fund.eth.lib.service;

import com.tanggo.fund.eth.lib.domain.Account;
import com.tanggo.fund.eth.lib.domain.Wallet;
import com.tanggo.fund.eth.lib.domain.repo.WalletRepository;
import com.tanggo.fund.eth.lib.domain.service.CryptoService;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * 钱包应用服务（用例层）
 *
 * 协调领域对象和基础设施服务，实现钱包管理用例
 *
 * 职责：
 * - 创建和管理钱包
 * - 导入和导出钱包
 * - 地址派生
 * - 交易签名
 * - 余额查询
 *
 * 遵循Clean Architecture原则：
 * - 依赖领域层接口（WalletRepository, CryptoService）
 * - 编排领域实体（Wallet, Account）
 * - 不包含业务逻辑（业务逻辑在领域实体中）
 */
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final CryptoService cryptoService;

    /**
     * 创建单密钥钱包
     *
     * @param name 钱包名称
     * @return 创建的钱包
     */
    public Wallet createSimpleWallet(String name) {
        // 使用领域方法创建钱包
        Wallet wallet = Wallet.createSimpleWallet(name);

        // 持久化钱包
        walletRepository.save(wallet);

        return wallet;
    }

    /**
     * 从私钥导入钱包
     *
     * @param name       钱包名称
     * @param privateKey 私钥（十六进制字符串）
     * @return 导入的钱包
     */
    public Wallet importWalletFromPrivateKey(String name, String privateKey) {
        // 解析私钥
        byte[] privateKeyBytes = hexToBytes(privateKey);

        // 验证私钥长度
        if (privateKeyBytes.length != 32) {
            throw new IllegalArgumentException("Invalid private key length");
        }

        // 导入钱包
        Wallet wallet = Wallet.importFromPrivateKey(name, privateKeyBytes);

        // 持久化钱包
        walletRepository.save(wallet);

        return wallet;
    }

    /**
     * 创建HD钱包
     *
     * @param name     钱包名称
     * @param wordCount 助记词数量（12, 15, 18, 21, 24）
     * @param password 可选密码
     * @return 创建的HD钱包
     */
    public WalletCreationResult createHDWallet(String name, int wordCount, String password) {
        // 生成助记词
        String mnemonic = Wallet.generateMnemonic(wordCount);

        // 创建HD钱包
        Wallet wallet = Wallet.createHDWallet(name, mnemonic, password);

        // 持久化钱包
        walletRepository.save(wallet);

        return new WalletCreationResult(wallet, mnemonic);
    }

    /**
     * 从助记词导入HD钱包
     *
     * @param name     钱包名称
     * @param mnemonic 助记词
     * @param password 可选密码
     * @return 导入的钱包
     */
    public Wallet importWalletFromMnemonic(String name, String mnemonic, String password) {
        // 创建HD钱包
        Wallet wallet = Wallet.createHDWallet(name, mnemonic, password);

        // 持久化钱包
        walletRepository.save(wallet);

        return wallet;
    }

    /**
     * 获取钱包
     *
     * @param walletId 钱包ID
     * @return 钱包（如果存在）
     */
    public Optional<Wallet> getWallet(String walletId) {
        return walletRepository.findById(walletId);
    }

    /**
     * 获取所有钱包
     *
     * @return 钱包列表
     */
    public List<Wallet> getAllWallets() {
        return walletRepository.findAll();
    }

    /**
     * 删除钱包
     *
     * @param walletId 钱包ID
     * @return 是否删除成功
     */
    public boolean deleteWallet(String walletId) {
        return walletRepository.delete(walletId);
    }

    /**
     * 派生新账户地址（仅HD钱包）
     *
     * @param walletId 钱包ID
     * @return 新派生的地址
     */
    public byte[] deriveNextAccount(String walletId) {
        // 获取钱包
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // 派生新账户
        byte[] newAddress = wallet.deriveNextAccount();

        // 更新钱包
        walletRepository.update(wallet);

        return newAddress;
    }

    /**
     * 获取钱包余额
     *
     * @param walletId 钱包ID
     * @param accountIndex 账户索引
     * @return 账户余额（Wei）
     */
    public BigInteger getBalance(String walletId, int accountIndex) {
        // 获取钱包
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // 获取账户地址
        byte[] address = wallet.getAccount(accountIndex);
        if (address == null) {
            throw new IllegalArgumentException("Account not found at index: " + accountIndex);
        }

        // TODO: 通过AccountRepository查询余额
        // Account account = accountRepository.findByAddress(address);
        // return account != null ? account.getBalance() : BigInteger.ZERO;

        return BigInteger.ZERO;
    }

    /**
     * 签名交易
     *
     * @param walletId        钱包ID
     * @param transactionHash 交易哈希
     * @param password        钱包密码（用于解锁）
     * @return 签名结果
     */
    public Wallet.Signature signTransaction(String walletId, byte[] transactionHash, String password) {
        // 获取钱包
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // 解锁钱包
        if (wallet.isLocked()) {
            boolean unlocked = wallet.unlock(password);
            if (!unlocked) {
                throw new IllegalArgumentException("Failed to unlock wallet: incorrect password");
            }
        }

        // 签名交易
        Wallet.Signature signature = wallet.signTransaction(transactionHash);

        // 锁定钱包
        wallet.lock();

        // 更新钱包（更新最后访问时间）
        walletRepository.update(wallet);

        return signature;
    }

    /**
     * 切换当前账户
     *
     * @param walletId 钱包ID
     * @param accountIndex 目标账户索引
     */
    public void switchAccount(String walletId, int accountIndex) {
        // 获取钱包
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // 切换账户
        wallet.switchAccount(accountIndex);

        // 更新钱包
        walletRepository.update(wallet);
    }

    /**
     * 锁定钱包
     *
     * @param walletId 钱包ID
     */
    public void lockWallet(String walletId) {
        // 获取钱包
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // 锁定钱包
        wallet.lock();

        // 更新钱包
        walletRepository.update(wallet);
    }

    /**
     * 解锁钱包
     *
     * @param walletId 钱包ID
     * @param password 钱包密码
     * @return 是否解锁成功
     */
    public boolean unlockWallet(String walletId, String password) {
        // 获取钱包
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // 解锁钱包
        boolean unlocked = wallet.unlock(password);

        // 更新钱包
        if (unlocked) {
            walletRepository.update(wallet);
        }

        return unlocked;
    }

    /**
     * 导出私钥
     *
     * @param walletId 钱包ID
     * @param password 钱包密码
     * @return 私钥（十六进制字符串）
     */
    public String exportPrivateKey(String walletId, String password) {
        // 获取钱包
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // 验证钱包类型（仅支持单密钥钱包）
        if (wallet.getType() != Wallet.WalletType.SIMPLE) {
            throw new UnsupportedOperationException("Only simple wallets support private key export");
        }

        // 解锁钱包
        if (wallet.isLocked()) {
            boolean unlocked = wallet.unlock(password);
            if (!unlocked) {
                throw new IllegalArgumentException("Failed to unlock wallet: incorrect password");
            }
        }

        // 获取私钥
        byte[] privateKey = wallet.getCurrentPrivateKey();

        // 锁定钱包
        wallet.lock();

        return bytesToHex(privateKey);
    }

    /**
     * 导出助记词
     *
     * @param walletId 钱包ID
     * @param password 钱包密码
     * @return 助记词
     */
    public String exportMnemonic(String walletId, String password) {
        // 获取钱包
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        // 验证钱包类型（仅支持HD钱包）
        if (wallet.getType() != Wallet.WalletType.HD) {
            throw new UnsupportedOperationException("Only HD wallets have mnemonic");
        }

        // 解锁钱包
        if (wallet.isLocked()) {
            boolean unlocked = wallet.unlock(password);
            if (!unlocked) {
                throw new IllegalArgumentException("Failed to unlock wallet: incorrect password");
            }
        }

        // 获取助记词
        String mnemonic = wallet.getMnemonic();

        // 锁定钱包
        wallet.lock();

        return mnemonic;
    }

    // ==================== 辅助方法 ====================

    /**
     * 十六进制字符串转字节数组
     */
    private byte[] hexToBytes(String hex) {
        // 移除0x前缀
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }

        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 钱包创建结果
     */
    public static class WalletCreationResult {
        private final Wallet wallet;
        private final String mnemonic;

        public WalletCreationResult(Wallet wallet, String mnemonic) {
            this.wallet = wallet;
            this.mnemonic = mnemonic;
        }

        public Wallet getWallet() {
            return wallet;
        }

        public String getMnemonic() {
            return mnemonic;
        }
    }
}
