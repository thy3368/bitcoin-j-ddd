package com.tanggo.fund.eth.lib.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 以太坊应用服务层
 * 遵循 Clean Architecture，处理业务逻辑和用例编排
 */
public interface EthereumApplicationService {

    /**
     * 获取地址余额
     *
     * @param address 地址
     * @param block 区块参数
     * @return 余额（Wei）
     */
    BigInteger getBalance(String address, String block);

    /**
     * 获取当前区块号
     *
     * @return 区块号
     */
    long getCurrentBlockNumber();

    /**
     * 发送已签名交易
     *
     * @param signedTxData 签名交易数据
     * @return 交易哈希
     */
    String sendRawTransaction(String signedTxData);

    /**
     * 获取交易详情
     *
     * @param txHash 交易哈希
     * @return 交易详情
     */
    Map<String, Object> getTransactionByHash(String txHash);

    /**
     * 获取交易收据
     *
     * @param txHash 交易哈希
     * @return 交易收据
     */
    Map<String, Object> getTransactionReceipt(String txHash);

    /**
     * 根据区块号获取区块
     *
     * @param blockNumber 区块号
     * @param fullTx 是否返回完整交易
     * @return 区块信息
     */
    Map<String, Object> getBlockByNumber(String blockNumber, boolean fullTx);

    /**
     * 根据区块哈希获取区块
     *
     * @param blockHash 区块哈希
     * @param fullTx 是否返回完整交易
     * @return 区块信息
     */
    Map<String, Object> getBlockByHash(String blockHash, boolean fullTx);

    /**
     * 获取交易计数（nonce）
     *
     * @param address 地址
     * @param block 区块参数
     * @return 交易计数
     */
    long getTransactionCount(String address, String block);

    /**
     * 调用合约
     *
     * @param transaction 交易对象
     * @param block 区块参数
     * @return 调用结果
     */
    String callContract(Map<String, String> transaction, String block);

    /**
     * 估算 Gas
     *
     * @param transaction 交易对象
     * @return Gas 估算值
     */
    BigInteger estimateGas(Map<String, String> transaction);

    /**
     * 获取 Gas 价格
     *
     * @return Gas 价格（Wei）
     */
    BigInteger getGasPrice();

    /**
     * 获取日志
     *
     * @param filter 过滤器
     * @return 日志列表
     */
    List<Map<String, Object>> getLogs(Map<String, Object> filter);

    /**
     * 获取链 ID
     *
     * @return 链 ID
     */
    long getChainId();

    /**
     * 获取客户端版本
     *
     * @return 版本信息
     */
    String getClientVersion();

    /**
     * 获取网络 ID
     *
     * @return 网络 ID
     */
    String getNetworkId();

    /**
     * 获取账户列表
     *
     * @return 账户地址列表
     */
    List<String> getAccounts();

    /**
     * 创建新账户
     *
     * @param password 密码
     * @return 新账户地址
     */
    String createAccount(String password);

    /**
     * 解锁账户
     *
     * @param address 地址
     * @param password 密码
     * @param duration 解锁时长（秒）
     * @return 是否成功
     */
    boolean unlockAccount(String address, String password, int duration);
}