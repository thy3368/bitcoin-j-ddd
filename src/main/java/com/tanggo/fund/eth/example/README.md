# 以太坊 JSON-RPC Provider 使用指南

本模块提供了基于 `jsonrpc4j` 的以太坊 JSON-RPC 客户端实现，遵循六边形架构（Hexagonal Architecture）模式。

## 功能特性

- ✅ 查询地址余额（支持指定区块）
- ✅ 发送已签名交易
- ✅ 获取交易详情和收据
- ✅ 查询区块信息
- ✅ 获取 Gas 价格
- ✅ 调用智能合约（只读）
- ✅ 自动处理 Wei/ETH/Gwei 单位转换

## 架构说明

```
EthereumRpcClient (接口)  ←-- jsonrpc4j 动态代理
         ↑
         |
    Provider (适配器)  ←-- Outbound Adapter
         ↑
         |
   应用业务逻辑
```

### 核心组件

1. **EthereumRpcClient.java**: 定义以太坊 JSON-RPC 接口方法
2. **Provider.java**: 外部网关适配器，封装 RPC 调用
3. **Test.java**: 使用示例和测试代码

## 快速开始

### 1. 添加依赖

已在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.github.briandilley.jsonrpc4j</groupId>
    <artifactId>jsonrpc4j</artifactId>
    <version>1.6</version>
</dependency>
```

### 2. 基本使用

```java
// 创建 Provider 实例
Provider provider = new Provider("https://mainnet.infura.io/v3/YOUR-API-KEY");

// 查询余额
String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
BigDecimal balance = provider.getBalance(address);
System.out.println("余额: " + balance + " ETH");
```

### 3. 支持的 RPC 节点

#### 公共节点服务商
- **Infura**: `https://mainnet.infura.io/v3/YOUR-PROJECT-ID`
- **Alchemy**: `https://eth-mainnet.g.alchemy.com/v2/YOUR-API-KEY`
- **QuickNode**: `https://YOUR-ENDPOINT.quiknode.pro/YOUR-API-KEY/`
- **Ankr**: `https://rpc.ankr.com/eth`

#### 本地节点
- **Geth**: `http://localhost:8545`
- **Hardhat**: `http://localhost:8545`
- **Ganache**: `http://localhost:7545`

## API 文档

### Provider 方法

#### getBalance(String address)
获取地址的最新余额。

**参数**:
- `address`: 以太坊地址（0x开头）

**返回**: `BigDecimal` - 余额（ETH）

**示例**:
```java
BigDecimal balance = provider.getBalance("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb");
```

---

#### getBalance(String address, String block)
获取地址在指定区块的余额。

**参数**:
- `address`: 以太坊地址
- `block`: 区块参数（`"latest"`, `"earliest"`, `"pending"` 或十六进制区块号）

**返回**: `BigDecimal` - 余额（ETH）

**示例**:
```java
BigDecimal balance = provider.getBalance(address, "0x1000000");
```

---

#### sendTransaction(String signedTxHex)
发送已签名的交易。

**参数**:
- `signedTxHex`: 签名后的交易数据（十六进制）

**返回**: `String` - 交易哈希

**示例**:
```java
String txHash = provider.sendTransaction("0xf86c...");
```

---

#### getBlockNumber()
获取当前区块号。

**返回**: `long` - 区块号

**示例**:
```java
long blockNumber = provider.getBlockNumber();
```

---

#### getGasPrice()
获取当前 Gas 价格。

**返回**: `BigDecimal` - Gas 价格（Gwei）

**示例**:
```java
BigDecimal gasPrice = provider.getGasPrice();
```

---

#### getTransaction(String txHash)
获取交易详情。

**参数**:
- `txHash`: 交易哈希

**返回**: `Map<String, Object>` - 交易详情

**示例**:
```java
Map<String, Object> tx = provider.getTransaction("0x123...");
```

---

#### getTransactionCount(String address)
获取地址的交易计数（nonce）。

**参数**:
- `address`: 地址

**返回**: `long` - 交易计数

**示例**:
```java
long nonce = provider.getTransactionCount(address);
```

---

#### callContract(String to, String data)
调用智能合约（只读，不发送交易）。

**参数**:
- `to`: 合约地址
- `data`: 编码后的方法调用数据

**返回**: `String` - 调用结果（十六进制）

**示例**:
```java
String result = provider.callContract("0x...", "0x70a08231...");
```

## 工具方法

### 单位转换

```java
// ETH 转 Wei
BigInteger wei = Provider.ethToWei(new BigDecimal("1.5"));

// Wei 转 ETH（内部方法）
// weiToEth(BigInteger wei)

// Wei 转 Gwei（内部方法）
// weiToGwei(BigInteger wei)
```

## 完整示例

### 查询余额

```java
public class BalanceChecker {
    public static void main(String[] args) {
        Provider provider = new Provider("https://mainnet.infura.io/v3/YOUR-KEY");

        String address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";
        BigDecimal balance = provider.getBalance(address);

        System.out.println("余额: " + balance + " ETH");
    }
}
```

### 批量查询

```java
public void batchCheck(String[] addresses) {
    Provider provider = new Provider();

    for (String address : addresses) {
        BigDecimal balance = provider.getBalance(address);
        System.out.println(address + ": " + balance + " ETH");
    }
}
```

### 监控余额变化

```java
public void monitorBalance(String address) {
    Provider provider = new Provider();
    BigDecimal lastBalance = BigDecimal.ZERO;

    while (true) {
        BigDecimal currentBalance = provider.getBalance(address);

        if (!currentBalance.equals(lastBalance)) {
            System.out.println("余额变化: " + lastBalance + " -> " + currentBalance);
            lastBalance = currentBalance;
        }

        Thread.sleep(10000); // 10秒轮询一次
    }
}
```

## 错误处理

所有方法在失败时都会抛出 `RuntimeException`，建议使用 try-catch 处理：

```java
try {
    BigDecimal balance = provider.getBalance(address);
    System.out.println("余额: " + balance);
} catch (RuntimeException e) {
    System.err.println("查询失败: " + e.getMessage());
    e.printStackTrace();
}
```

## 性能优化建议

1. **连接池复用**: 对于高频调用，建议复用 `Provider` 实例
2. **批量查询**: 使用 `eth_call` 和 Multicall 合约批量查询
3. **缓存**: 对不变的数据（如历史区块余额）进行缓存
4. **限流**: 公共节点通常有速率限制，注意控制请求频率

## EthereumRpcClient 支持的方法

| 方法 | 描述 |
|------|------|
| `eth_getBalance` | 获取余额 |
| `eth_blockNumber` | 获取区块号 |
| `eth_sendRawTransaction` | 发送交易 |
| `eth_getTransactionByHash` | 获取交易 |
| `eth_getTransactionReceipt` | 获取交易收据 |
| `eth_getBlockByNumber` | 根据区块号获取区块 |
| `eth_getBlockByHash` | 根据哈希获取区块 |
| `eth_getTransactionCount` | 获取交易计数 |
| `eth_call` | 调用合约 |
| `eth_estimateGas` | 估算 Gas |
| `eth_gasPrice` | 获取 Gas 价格 |
| `eth_getLogs` | 获取日志 |
| `eth_chainId` | 获取链 ID |
| `web3_clientVersion` | 获取客户端版本 |
| `net_version` | 获取网络 ID |

## 注意事项

1. **API Key 安全**: 不要在代码中硬编码 API Key，建议使用环境变量或配置文件
2. **速率限制**: 免费节点通常有请求限制，生产环境建议使用付费服务
3. **网络选择**: 默认连接主网，测试时请使用测试网（Sepolia/Goerli）
4. **Gas 费用**: 发送交易需要 ETH 支付 Gas 费用

## 测试网配置

### Sepolia 测试网
```java
Provider provider = new Provider("https://sepolia.infura.io/v3/YOUR-KEY");
```

### Goerli 测试网
```java
Provider provider = new Provider("https://goerli.infura.io/v3/YOUR-KEY");
```

## 相关资源

- [以太坊 JSON-RPC 规范](https://ethereum.org/en/developers/docs/apis/json-rpc/)
- [jsonrpc4j GitHub](https://github.com/briandilley/jsonrpc4j)
- [Infura 文档](https://docs.infura.io/)
- [Alchemy 文档](https://docs.alchemy.com/)

## License

本代码遵循项目整体 License。