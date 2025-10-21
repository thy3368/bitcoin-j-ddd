package com.tanggo.fund.eth.lib.service;

import com.tanggo.fund.eth.lib.domain.Account;
import com.tanggo.fund.eth.lib.domain.Wallet;
import com.tanggo.fund.eth.lib.domain.repo.WalletRepository;
import com.tanggo.fund.eth.lib.domain.service.CryptoService;
import com.tanggo.fund.eth.lib.domain.transaction.Eip1559Transaction;
import com.tanggo.fund.eth.lib.outbound.AccountRepo;
import com.tanggo.fund.eth.lib.service.dto.TransferRequest;
import com.tanggo.fund.eth.lib.service.dto.TransferResponse;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * 转账应用服务（用例层）
 *
 * 协调钱包、账户、交易等领域对象，实现以太坊转账用例
 *
 * 职责：
 * - 构建和签名转账交易
 * - 验证转账参数和账户状态
 * - 估算Gas和费用
 * - 管理交易生命周期
 *
 * 遵循Clean Architecture原则：
 * - 依赖领域层接口
 * - 编排领域实体
 * - 不包含业务逻辑（业务逻辑在领域实体中）
 */
@RequiredArgsConstructor
public class TransferService {

    private final WalletRepository walletRepository;
    private final AccountRepo accountRepository;
    private final CryptoService cryptoService;

    /**
     * 执行转账
     *
     * 完整流程：
     * 1. 验证请求参数
     * 2. 加载钱包并解锁
     * 3. 查询发送者账户状态（余额、nonce）
     * 4. 估算Gas（如果未提供）
     * 5. 构建EIP-1559交易
     * 6. 签名交易
     * 7. 验证交易有效性
     * 8. 返回签名后的交易
     *
     * @param request 转账请求
     * @return 转账响应（包含交易哈希和签名后的交易数据）
     */
    public TransferResponse transfer(TransferRequest request) {
        // 1. 验证请求参数
        if (!request.validate()) {
            throw new IllegalArgumentException("Invalid transfer request");
        }

        // 2. 加载钱包并解锁
        Wallet wallet = walletRepository.findById(request.getWalletId())
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + request.getWalletId()));

        if (wallet.isLocked()) {
            boolean unlocked = wallet.unlock(request.getPassword());
            if (!unlocked) {
                throw new IllegalArgumentException("Failed to unlock wallet: incorrect password");
            }
        }

        try {
            // 获取发送者地址
            byte[] fromAddress = wallet.getCurrentAccount();
            if (fromAddress == null) {
                throw new IllegalStateException("No active account in wallet");
            }

            // 3. 查询发送者账户状态
            Account senderAccount = accountRepository.query(bytesToHex(fromAddress));
            if (senderAccount == null) {
                // 账户不存在，创建新账户
                senderAccount = Account.createEOA(fromAddress);
            }

            // 验证余额充足
            BigInteger totalCost = calculateTotalCost(request);
            if (!senderAccount.hasSufficientBalance(totalCost)) {
                throw new IllegalArgumentException(
                    String.format("Insufficient balance. Required: %s Wei, Available: %s Wei",
                        totalCost, senderAccount.getBalance())
                );
            }

            // 4. 确定nonce
            BigInteger nonce = request.getNonce();
            if (nonce == null) {
                nonce = senderAccount.getNonce();
            }

            // 5. 估算Gas（如果未提供）
            BigInteger gasLimit = request.getGasLimit();
            if (gasLimit == null) {
                gasLimit = estimateGas(request);
            }

            // 6. 构建EIP-1559交易
            Eip1559Transaction transaction = buildTransaction(request, fromAddress, nonce, gasLimit);

            // 7. 签名交易
            byte[] transactionHash = getUnsignedTransactionHash(transaction);
            Wallet.Signature signature = wallet.signTransaction(transactionHash);

            // 将签名应用到交易（byte[] 转换为 BigInteger）
            transaction.setV(signature.getV());
            transaction.setR(new BigInteger(1, signature.getR()));  // 1表示正数
            transaction.setS(new BigInteger(1, signature.getS()));

            // 8. 验证交易
            if (!transaction.validate()) {
                throw new IllegalStateException("Transaction validation failed");
            }

            if (!transaction.verifySignature()) {
                throw new IllegalStateException("Transaction signature verification failed");
            }

            // 9. 构建响应
            return buildResponse(transaction, fromAddress);

        } finally {
            // 锁定钱包
            wallet.lock();
            walletRepository.update(wallet);
        }
    }

    /**
     * 估算转账所需的Gas
     *
     * @param request 转账请求
     * @return 估算的Gas限制
     */
    public BigInteger estimateGas(TransferRequest request) {
        // 简单ETH转账的内在Gas成本
        BigInteger intrinsicGas = BigInteger.valueOf(21_000);

        // 如果有数据，计算数据成本
        if (request.getData() != null && request.getData().length > 0) {
            for (byte b : request.getData()) {
                if (b == 0) {
                    intrinsicGas = intrinsicGas.add(BigInteger.valueOf(4)); // 零字节：4 gas
                } else {
                    intrinsicGas = intrinsicGas.add(BigInteger.valueOf(16)); // 非零字节：16 gas
                }
            }
        }

        // 添加10%的安全边际
        BigInteger safetyMargin = intrinsicGas.multiply(BigInteger.valueOf(110))
            .divide(BigInteger.valueOf(100));

        return safetyMargin;
    }

    /**
     * 计算交易的总成本
     * totalCost = 转账金额 + (gasLimit * maxFeePerGas)
     *
     * @param request 转账请求
     * @return 总成本（Wei）
     */
    private BigInteger calculateTotalCost(TransferRequest request) {
        BigInteger amount = request.getAmount();
        BigInteger gasLimit = request.getGasLimit();
        BigInteger maxFeePerGas = request.getMaxFeePerGas();

        if (gasLimit == null) {
            gasLimit = estimateGas(request);
        }

        if (maxFeePerGas == null) {
            // 如果未提供最大费用，使用默认值（例如100 Gwei）
            maxFeePerGas = BigInteger.valueOf(100_000_000_000L);
        }

        BigInteger gasCost = gasLimit.multiply(maxFeePerGas);
        return amount.add(gasCost);
    }

    /**
     * 构建EIP-1559交易
     *
     * @param request     转账请求
     * @param fromAddress 发送者地址
     * @param nonce       交易nonce
     * @param gasLimit    Gas限制
     * @return 构建的交易
     */
    private Eip1559Transaction buildTransaction(TransferRequest request, byte[] fromAddress,
                                                 BigInteger nonce, BigInteger gasLimit) {
        return Eip1559Transaction.builder()
            .chainId(request.getChainId())
            .nonce(nonce)
            .maxPriorityFeePerGas(request.getMaxPriorityFeePerGas() != null
                ? request.getMaxPriorityFeePerGas()
                : BigInteger.valueOf(2_000_000_000L)) // 默认2 Gwei
            .maxFeePerGas(request.getMaxFeePerGas() != null
                ? request.getMaxFeePerGas()
                : BigInteger.valueOf(100_000_000_000L)) // 默认100 Gwei
            .gasLimit(gasLimit)
            .to(request.getToAddressBytes())
            .value(request.getAmount())
            .data(request.getData() != null ? request.getData() : new byte[0])
            .accessList(new ArrayList<>())
            .build();
    }

    /**
     * 获取未签名交易的哈希
     *
     * @param transaction 交易
     * @return 交易哈希（32字节）
     */
    private byte[] getUnsignedTransactionHash(Eip1559Transaction transaction) {
        // TODO: 实现未签名交易的哈希计算
        // 1. RLP编码未签名交易：rlp([chainId, nonce, maxPriorityFeePerGas,
        //                          maxFeePerGas, gasLimit, to, value, data, accessList])
        // 2. 添加交易类型前缀：0x02 || rlpEncoded
        // 3. Keccak256哈希

        // 暂时使用占位实现
        // 生产环境需要使用cryptoService.rlpEncode和cryptoService.keccak256
        return new byte[32];
    }

    /**
     * 构建转账响应
     *
     * @param transaction 已签名的交易
     * @param fromAddress 发送者地址
     * @return 转账响应
     */
    private TransferResponse buildResponse(Eip1559Transaction transaction, byte[] fromAddress) {
        return TransferResponse.builder()
            .transactionHash(bytesToHex(transaction.getTransactionHash()))
            .fromAddress(bytesToHex(fromAddress))
            .toAddress(bytesToHex(transaction.getTo()))
            .amount(transaction.getValue())
            .gasLimit(transaction.getGasLimit())
            .maxFeePerGas(transaction.getMaxFeePerGas())
            .maxPriorityFeePerGas(transaction.getMaxPriorityFeePerGas())
            .nonce(transaction.getNonce())
            .chainId(transaction.getChainId())
            .status(TransferResponse.TransactionStatus.SIGNED)
            .rawTransaction(bytesToHex(transaction.encode()))
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * 查询转账状态
     *
     * @param transactionHash 交易哈希
     * @return 转账响应
     */
    public TransferResponse getTransferStatus(String transactionHash) {
        // TODO: 实现交易状态查询
        // 1. 从交易池或区块链查询交易
        // 2. 获取交易确认数
        // 3. 获取交易收据
        // 4. 返回状态信息

        throw new UnsupportedOperationException("Transaction status query not yet implemented");
    }

    /**
     * 取消待处理的转账（通过发送相同nonce但更高Gas价格的交易）
     *
     * @param walletId        钱包ID
     * @param nonce           要取消的交易的nonce
     * @param newMaxFeePerGas 新的更高的maxFeePerGas
     * @param password        钱包密码
     * @return 取消交易的响应
     */
    public TransferResponse cancelTransfer(String walletId, BigInteger nonce,
                                           BigInteger newMaxFeePerGas, String password) {
        // 构建一个发送给自己的0 ETH交易，使用相同的nonce但更高的Gas价格
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        byte[] address = wallet.getCurrentAccount();

        TransferRequest cancelRequest = TransferRequest.builder()
            .walletId(walletId)
            .toAddress(bytesToHex(address)) // 发送给自己
            .amount(BigInteger.ZERO) // 0 ETH
            .nonce(nonce) // 相同的nonce
            .maxFeePerGas(newMaxFeePerGas) // 更高的Gas价格
            .maxPriorityFeePerGas(newMaxFeePerGas.divide(BigInteger.TWO))
            .password(password)
            .build();

        return transfer(cancelRequest);
    }

    /**
     * 加速待处理的转账（通过发送相同交易但更高Gas价格）
     *
     * @param originalRequest 原始转账请求
     * @param newMaxFeePerGas 新的更高的maxFeePerGas
     * @return 加速交易的响应
     */
    public TransferResponse speedUpTransfer(TransferRequest originalRequest,
                                            BigInteger newMaxFeePerGas) {
        // 使用相同参数但更高的Gas价格重新发送交易
        TransferRequest speedUpRequest = TransferRequest.builder()
            .walletId(originalRequest.getWalletId())
            .toAddress(originalRequest.getToAddress())
            .amount(originalRequest.getAmount())
            .nonce(originalRequest.getNonce())
            .maxFeePerGas(newMaxFeePerGas)
            .maxPriorityFeePerGas(newMaxFeePerGas.divide(BigInteger.TWO))
            .gasLimit(originalRequest.getGasLimit())
            .data(originalRequest.getData())
            .password(originalRequest.getPassword())
            .build();

        return transfer(speedUpRequest);
    }

    // ==================== 辅助方法 ====================

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 十六进制字符串转字节数组
     */
    private byte[] hexToBytes(String hex) {
        if (hex == null) {
            return null;
        }

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
}