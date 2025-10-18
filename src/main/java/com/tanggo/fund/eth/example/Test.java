package com.tanggo.fund.eth.example;

import com.tanggo.fund.eth.lib.outbound.Provider;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 以太坊 Provider 使用示例
 */
public class Test {

    public static void main(String[] args) {
        // 示例：如何使用 Provider

        // 1. 使用公共以太坊节点（需要替换为真实的 Infura/Alchemy API Key）
        Provider provider = new Provider("https://mainnet.infura.io/v3/YOUR-PROJECT-ID");

        // 或者使用本地节点
        // Provider provider = new Provider("http://localhost:8545");

        try {
            // 2. 查询余额示例
            String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"; // Vitalik 的地址
            BigDecimal balance = provider.getBalance(address);
            System.out.println("地址 " + address + " 的余额: " + balance + " ETH");

            // 3. 查询特定区块的余额
            BigDecimal balanceAtBlock = provider.getBalance(address, "0x1000000"); // 区块 16777216
            System.out.println("区块 16777216 时的余额: " + balanceAtBlock + " ETH");

            // 4. 获取当前区块号
            long blockNumber = provider.getBlockNumber();
            System.out.println("当前区块号: " + blockNumber);

            // 5. 获取 Gas 价格
            BigDecimal gasPrice = provider.getGasPrice();
            System.out.println("当前 Gas 价格: " + gasPrice + " Gwei");

            // 6. 获取地址的交易计数（nonce）
            long nonce = provider.getTransactionCount(address);
            System.out.println("地址的交易计数: " + nonce);

            // 7. 查询交易详情
            String txHash = "0x..."; // 替换为真实交易哈希
            Map<String, Object> txDetails = provider.getTransaction(txHash);
            System.out.println("交易详情: " + txDetails);

            // 8. 发送已签名的交易（需要先签名交易）
            // String signedTx = "0x..."; // 签名后的交易数据
            // String txHash = provider.sendTransaction(signedTx);
            // System.out.println("交易已发送，哈希: " + txHash);

            // 9. 调用智能合约（只读）
            String contractAddress = "0x..."; // 合约地址
            String data = "0x..."; // 编码后的方法调用数据
            String result = provider.callContract(contractAddress, data);
            System.out.println("合约调用结果: " + result);

        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 简单的余额查询示例
     */
    public void simpleBalanceCheck() {
        Provider provider = new Provider();
        String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";

        try {
            BigDecimal balance = provider.getBalance(address);
            System.out.println("余额: " + balance + " ETH");
        } catch (Exception e) {
            System.err.println("查询失败: " + e.getMessage());
        }
    }

    /**
     * 批量查询多个地址的余额
     */
    public void batchBalanceCheck(String[] addresses) {
        Provider provider = new Provider();

        for (String address : addresses) {
            try {
                BigDecimal balance = provider.getBalance(address);
                System.out.println(address + ": " + balance + " ETH");
            } catch (Exception e) {
                System.err.println("查询 " + address + " 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 监控账户余额变化
     */
    public void monitorBalance(String address, long intervalSeconds) {
        Provider provider = new Provider();
        BigDecimal lastBalance = BigDecimal.ZERO;

        while (true) {
            try {
                BigDecimal currentBalance = provider.getBalance(address);

                if (!currentBalance.equals(lastBalance)) {
                    System.out.println("余额变化: " + lastBalance + " -> " + currentBalance + " ETH");
                    lastBalance = currentBalance;
                }

                Thread.sleep(intervalSeconds * 1000);

            } catch (Exception e) {
                System.err.println("监控出错: " + e.getMessage());
                try {
                    Thread.sleep(5000); // 出错后等待5秒
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
