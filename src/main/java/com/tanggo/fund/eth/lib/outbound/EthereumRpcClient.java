package com.tanggo.fund.eth.lib.outbound;

import com.googlecode.jsonrpc4j.JsonRpcMethod;

import java.util.List;
import java.util.Map;

/**
 * 以太坊 JSON-RPC 客户端接口
 * 定义了与以太坊节点通信的标准方法
 */
public interface EthereumRpcClient {

    /**
     * 获取指定地址的余额
     *
     * @param address 以太坊地址（0x开头）
     * @param block 区块参数（"latest", "earliest", "pending" 或区块号）
     * @return 余额（Wei，十六进制字符串）
     */
    @JsonRpcMethod("eth_getBalance")
    String ethGetBalance(String address, String block);

    /**
     * 获取当前区块号
     *
     * @return 区块号（十六进制字符串）
     */
    @JsonRpcMethod("eth_blockNumber")
    String ethBlockNumber();

    /**
     * 发送已签名的交易
     *
     * @param signedTransactionData 签名后的交易数据（十六进制）
     * @return 交易哈希
     */
    @JsonRpcMethod("eth_sendRawTransaction")
    String ethSendRawTransaction(String signedTransactionData);

    /**
     * 根据交易哈希获取交易详情
     *
     * @param transactionHash 交易哈希
     * @return 交易详情
     */
    @JsonRpcMethod("eth_getTransactionByHash")
    Map<String, Object> ethGetTransactionByHash(String transactionHash);

    /**
     * 获取交易收据
     *
     * @param transactionHash 交易哈希
     * @return 交易收据
     */
    @JsonRpcMethod("eth_getTransactionReceipt")
    Map<String, Object> ethGetTransactionReceipt(String transactionHash);

    /**
     * 根据区块号或标签获取区块信息
     *
     * @param blockParameter 区块号（十六进制）或标签（latest/earliest/pending）
     * @param fullTransactions 是否返回完整交易信息
     * @return 区块信息
     */
    @JsonRpcMethod("eth_getBlockByNumber")
    Map<String, Object> ethGetBlockByNumber(String blockParameter, boolean fullTransactions);

    /**
     * 根据区块哈希获取区块信息
     *
     * @param blockHash 区块哈希
     * @param fullTransactions 是否返回完整交易信息
     * @return 区块信息
     */
    @JsonRpcMethod("eth_getBlockByHash")
    Map<String, Object> ethGetBlockByHash(String blockHash, boolean fullTransactions);

    /**
     * 获取指定地址在指定区块的交易数量（nonce）
     *
     * @param address 地址
     * @param block 区块参数
     * @return 交易数量（十六进制）
     */
    @JsonRpcMethod("eth_getTransactionCount")
    String ethGetTransactionCount(String address, String block);

    /**
     * 调用智能合约方法（不发送交易）
     *
     * @param transaction 交易对象
     * @param block 区块参数
     * @return 调用结果
     */
    @JsonRpcMethod("eth_call")
    String ethCall(Map<String, String> transaction, String block);

    /**
     * 估算 Gas
     *
     * @param transaction 交易对象
     * @return 估算的 Gas（十六进制）
     */
    @JsonRpcMethod("eth_estimateGas")
    String ethEstimateGas(Map<String, String> transaction);

    /**
     * 获取当前 Gas 价格
     *
     * @return Gas 价格（Wei，十六进制）
     */
    @JsonRpcMethod("eth_gasPrice")
    String ethGasPrice();

    /**
     * 获取日志/事件
     *
     * @param filter 过滤器参数
     * @return 日志列表
     */
    @JsonRpcMethod("eth_getLogs")
    List<Map<String, Object>> ethGetLogs(Map<String, Object> filter);

    /**
     * 获取链 ID
     *
     * @return 链 ID（十六进制）
     */
    @JsonRpcMethod("eth_chainId")
    String ethChainId();

    /**
     * 获取客户端版本
     *
     * @return 客户端版本信息
     */
    @JsonRpcMethod("web3_clientVersion")
    String web3ClientVersion();

    /**
     * 获取网络 ID
     *
     * @return 网络 ID
     */
    @JsonRpcMethod("net_version")
    String netVersion();
}
