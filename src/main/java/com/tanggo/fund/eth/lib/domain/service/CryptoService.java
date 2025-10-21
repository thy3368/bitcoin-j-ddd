package com.tanggo.fund.eth.lib.domain.service;

import com.tanggo.fund.eth.lib.domain.Wallet;

import java.math.BigInteger;

/**
 * 加密服务接口（领域服务）
 *
 * 提供密钥管理、地址派生、交易签名等加密操作
 *
 * 这是一个领域服务接口，定义了钱包领域所需的加密原语
 * 具体实现由基础设施层提供（使用Bouncy Castle、Web3j等库）
 */
public interface CryptoService {

    /**
     * 从私钥派生以太坊地址
     * 算法：address = keccak256(publicKey)[12:]
     *
     * @param privateKey 私钥（32字节）
     * @return 以太坊地址（20字节）
     */
    byte[] deriveAddress(byte[] privateKey);

    /**
     * 生成随机私钥
     *
     * @return 32字节私钥
     */
    byte[] generatePrivateKey();

    /**
     * 从私钥派生公钥（secp256k1）
     *
     * @param privateKey 私钥（32字节）
     * @return 公钥（64字节，未压缩格式）
     */
    byte[] derivePublicKey(byte[] privateKey);

    /**
     * 生成BIP39助记词
     *
     * @param entropyBits 熵位数（128, 160, 192, 224, 256）
     * @return 助记词字符串
     */
    String generateMnemonic(int entropyBits);

    /**
     * 验证BIP39助记词有效性
     *
     * @param mnemonic 助记词
     * @return 是否有效
     */
    boolean validateMnemonic(String mnemonic);

    /**
     * 从助记词生成种子（BIP39）
     *
     * @param mnemonic 助记词
     * @param password 可选密码（BIP39 passphrase）
     * @return 种子（64字节）
     */
    byte[] mnemonicToSeed(String mnemonic, String password);

    /**
     * 从种子派生HD钱包主密钥（BIP32）
     *
     * @param seed 种子（64字节）
     * @return HD密钥对象
     */
    HDKey deriveMasterKey(byte[] seed);

    /**
     * 从HD密钥派生子密钥（BIP32/BIP44）
     *
     * @param parentKey  父密钥
     * @param path       派生路径（例如：m/44'/60'/0'/0/0）
     * @return 派生的子密钥
     */
    HDKey deriveChildKey(HDKey parentKey, String path);

    /**
     * 对交易哈希进行ECDSA签名（secp256k1）
     *
     * @param transactionHash 交易哈希（32字节）
     * @param privateKey      私钥（32字节）
     * @param chainId         链ID（用于EIP-155）
     * @return 签名结果
     */
    Wallet.Signature signTransaction(byte[] transactionHash, byte[] privateKey, BigInteger chainId);

    /**
     * 验证ECDSA签名
     *
     * @param hash      消息哈希
     * @param signature 签名
     * @param publicKey 公钥
     * @return 签名是否有效
     */
    boolean verifySignature(byte[] hash, Wallet.Signature signature, byte[] publicKey);

    /**
     * 从签名恢复公钥（ECDSA恢复）
     *
     * @param hash      消息哈希
     * @param signature 签名
     * @return 恢复的公钥
     */
    byte[] recoverPublicKey(byte[] hash, Wallet.Signature signature);

    /**
     * 计算Keccak256哈希
     *
     * @param data 输入数据
     * @return 哈希结果（32字节）
     */
    byte[] keccak256(byte[] data);

    /**
     * RLP编码
     *
     * @param data 输入数据
     * @return RLP编码结果
     */
    byte[] rlpEncode(Object data);

    /**
     * RLP解码
     *
     * @param encoded RLP编码的数据
     * @return 解码结果
     */
    Object rlpDecode(byte[] encoded);

    /**
     * 使用密码加密数据（用于钱包存储）
     *
     * @param data     原始数据
     * @param password 密码
     * @return 加密后的数据
     */
    byte[] encrypt(byte[] data, String password);

    /**
     * 使用密码解密数据
     *
     * @param encryptedData 加密的数据
     * @param password      密码
     * @return 解密后的数据
     */
    byte[] decrypt(byte[] encryptedData, String password);

    /**
     * 验证地址校验和（EIP-55）
     *
     * @param address 地址字符串（0x开头）
     * @return 是否有效
     */
    boolean validateAddressChecksum(String address);

    /**
     * 格式化地址为EIP-55校验和格式
     *
     * @param address 地址（20字节）
     * @return 带校验和的地址字符串
     */
    String toChecksumAddress(byte[] address);

    /**
     * HD密钥对象（BIP32）
     */
    class HDKey {
        /**
         * 私钥（32字节）
         */
        private final byte[] privateKey;

        /**
         * 公钥（33字节，压缩格式）
         */
        private final byte[] publicKey;

        /**
         * 链码（32字节）
         */
        private final byte[] chainCode;

        /**
         * 派生深度
         */
        private final int depth;

        /**
         * 父密钥指纹（4字节）
         */
        private final byte[] parentFingerprint;

        /**
         * 子密钥索引
         */
        private final int childIndex;

        public HDKey(byte[] privateKey, byte[] publicKey, byte[] chainCode,
                     int depth, byte[] parentFingerprint, int childIndex) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.chainCode = chainCode;
            this.depth = depth;
            this.parentFingerprint = parentFingerprint;
            this.childIndex = childIndex;
        }

        public byte[] getPrivateKey() {
            return privateKey;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public byte[] getChainCode() {
            return chainCode;
        }

        public int getDepth() {
            return depth;
        }

        public byte[] getParentFingerprint() {
            return parentFingerprint;
        }

        public int getChildIndex() {
            return childIndex;
        }

        /**
         * 判断是否为硬化派生
         *
         * @return 是否硬化
         */
        public boolean isHardened() {
            return childIndex >= 0x80000000;
        }
    }
}