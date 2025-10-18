package com.tanggo.fund.eth.lib.outbound;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import com.googlecode.jsonrpc4j.ProxyUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * 以太坊 Provider - 外部网关适配器
 * 遵循六边形架构，作为 outbound adapter 连接以太坊节点
 */
public class Provider {

    private final String rpcUrl;
    private final EthereumRpcClient rpcClient;

    /**
     * 构造函数 - 使用默认 RPC URL
     */
    public Provider() {
        this("https://mainnet.infura.io/v3/YOUR-PROJECT-ID");
    }

    /**
     * 构造函数 - 自定义 RPC URL
     *
     * @param rpcUrl 以太坊节点的 JSON-RPC URL
     */
    public Provider(String rpcUrl) {
        this.rpcUrl = rpcUrl;
        this.rpcClient = createRpcClient();
    }

    /**
     * 创建 JSON-RPC 客户端
     */
    private EthereumRpcClient createRpcClient() {
        try {
            // 创建 JSON-RPC HTTP 客户端
            JsonRpcHttpClient httpClient = new JsonRpcHttpClient(URI.create(rpcUrl).toURL());

            // 可选：设置认证信息（如果需要）
            // Map<String, String> headers = new HashMap<>();
            // headers.put("Authorization", "Bearer YOUR-TOKEN");
            // httpClient.setHeaders(headers);

            // 使用动态代理创建接口实现
            return ProxyUtil.createClientProxy(
                    getClass().getClassLoader(),
                    EthereumRpcClient.class,
                    httpClient
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Ethereum RPC client", e);
        }
    }

    /**
     * 获取指定地址的余额
     *
     * @param address 以太坊地址（0x开头的42字符地址）
     * @return 余额（以 ETH 为单位）
     */
    public BigDecimal getBalance(String address) {
        try {
            // 调用 eth_getBalance 方法，获取最新区块的余额
            String balanceHex = rpcClient.ethGetBalance(address, "latest");

            // 将十六进制字符串转换为 BigInteger（Wei）
            BigInteger balanceWei = hexToBigInteger(balanceHex);

            // 将 Wei 转换为 ETH（1 ETH = 10^18 Wei）
            return weiToEth(balanceWei);

        } catch (Throwable e) {
            throw new RuntimeException("Failed to get balance for address: " + address, e);
        }
    }

    /**
     * 获取指定地址在指定区块的余额
     *
     * @param address 以太坊地址
     * @param block 区块参数（"latest", "earliest", "pending" 或十六进制区块号）
     * @return 余额（ETH）
     */
    public BigDecimal getBalance(String address, String block) {
        try {
            String balanceHex = rpcClient.ethGetBalance(address, block);
            BigInteger balanceWei = hexToBigInteger(balanceHex);
            return weiToEth(balanceWei);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get balance for address: " + address + " at block: " + block, e);
        }
    }

    /**
     * 发送已签名的交易
     *
     * @param signedTxHex 签名后的交易数据（十六进制）
     * @return 交易哈希
     */
    public String sendTransaction(String signedTxHex) {
        try {
            return rpcClient.ethSendRawTransaction(signedTxHex);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to send transaction", e);
        }
    }

    /**
     * 获取当前区块号
     *
     * @return 区块号
     */
    public long getBlockNumber() {
        try {
            String blockNumberHex = rpcClient.ethBlockNumber();
            return hexToBigInteger(blockNumberHex).longValue();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get block number", e);
        }
    }

    /**
     * 获取交易详情
     *
     * @param txHash 交易哈希
     * @return 交易详情
     */
    public Map<String, Object> getTransaction(String txHash) {
        try {
            return rpcClient.ethGetTransactionByHash(txHash);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get transaction: " + txHash, e);
        }
    }

    /**
     * 获取当前 Gas 价格
     *
     * @return Gas 价格（Gwei）
     */
    public BigDecimal getGasPrice() {
        try {
            String gasPriceHex = rpcClient.ethGasPrice();
            BigInteger gasPriceWei = hexToBigInteger(gasPriceHex);
            return weiToGwei(gasPriceWei);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get gas price", e);
        }
    }

    /**
     * 获取地址的交易计数（nonce）
     *
     * @param address 地址
     * @return 交易计数
     */
    public long getTransactionCount(String address) {
        try {
            String countHex = rpcClient.ethGetTransactionCount(address, "latest");
            return hexToBigInteger(countHex).longValue();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get transaction count for: " + address, e);
        }
    }

    /**
     * 调用智能合约（只读，不发送交易）
     *
     * @param to 合约地址
     * @param data 调用数据（编码后的方法和参数）
     * @return 调用结果（十六进制）
     */
    public String callContract(String to, String data) {
        try {
            Map<String, String> transaction = new HashMap<>();
            transaction.put("to", to);
            transaction.put("data", data);
            return rpcClient.ethCall(transaction, "latest");
        } catch (Throwable e) {
            throw new RuntimeException("Failed to call contract: " + to, e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 将十六进制字符串转换为 BigInteger
     */
    private BigInteger hexToBigInteger(String hex) {
        if (hex == null || hex.isEmpty()) {
            return BigInteger.ZERO;
        }
        // 移除 "0x" 前缀
        String cleaned = hex.startsWith("0x") ? hex.substring(2) : hex;
        return new BigInteger(cleaned, 16);
    }

    /**
     * 将 Wei 转换为 ETH
     */
    private BigDecimal weiToEth(BigInteger wei) {
        BigDecimal weiDecimal = new BigDecimal(wei);
        BigDecimal divisor = new BigDecimal("1000000000000000000"); // 10^18
        return weiDecimal.divide(divisor, 18, RoundingMode.DOWN);
    }

    /**
     * 将 Wei 转换为 Gwei
     */
    private BigDecimal weiToGwei(BigInteger wei) {
        BigDecimal weiDecimal = new BigDecimal(wei);
        BigDecimal divisor = new BigDecimal("1000000000"); // 10^9
        return weiDecimal.divide(divisor, 9, RoundingMode.DOWN);
    }

    /**
     * 将 ETH 转换为 Wei
     */
    public static BigInteger ethToWei(BigDecimal eth) {
        BigDecimal multiplier = new BigDecimal("1000000000000000000"); // 10^18
        return eth.multiply(multiplier).toBigInteger();
    }

    /**
     * 获取 RPC URL
     */
    public String getRpcUrl() {
        return rpcUrl;
    }
}
