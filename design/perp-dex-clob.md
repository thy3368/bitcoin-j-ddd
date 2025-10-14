# 永续合约 DEX CLOB 技术方案

## 1. 项目概述

### 1.1 设计目标

基于币安 Aster、Hyperliquid 和 dYdX v4 架构，设计一个高性能、低延迟的永续合约去中心化交易所（Perp DEX）CLOB 系统，遵循 DDD 和 Clean Architecture 原则。

### 1.2 核心特性

#### 永续合约特性
- **资金费率机制**: 每8小时结算，维持标记价格与指数价格平衡
- **保证金系统**: 支持全仓模式（Cross Margin）和逐仓模式（Isolated Margin）
- **杠杆交易**: 最高125倍杠杆，用户可自主选择
- **标记价格**: 基于多个现货交易所的指数价格，防止恶意操纵
- **强平引擎**: 实时监控保证金率，触发强制平仓
- **ADL (自动减仓)**: 在极端行情下自动减少对手方持仓

#### DEX 特性
- **链下订单簿 + 链上结算**: 高性能撮合 + 去中心化托管
- **非托管钱包**: 用户资产始终由智能合约控制
- **透明度**: 所有结算和资金费率链上可验证
- **跨链支持**: 通过跨链桥支持多链资产
- **MEV 保护**: 使用时间锁和批量处理防止抢跑

### 1.3 性能指标

参考 Hyperliquid 和币安撮合引擎的性能基准：

| 指标 | 目标值 | 参考基准 |
|-----|-------|---------|
| 订单处理能力 | ≥ 200,000 订单/秒 | Hyperliquid: 200k/s |
| 撮合延迟 (P99) | < 10ms | dYdX v4: ~10ms |
| 订单取消延迟 | < 1ms | 链下即时 |
| 链上结算确认 | < 1秒 | HyperBFT: 0.2s |
| 订单簿深度查询 | < 1μs | 内存操作 |
| 资金费率计算 | 每8小时 | 行业标准 |
| 强平检查频率 | 每100ms | 实时风控 |

### 1.4 技术栈

#### 后端技术
- **语言**: Java 25
- **框架**: Spring Boot 4.0.0-M3
- **编译优化**: GraalVM AOT 原生编译
- **内存管理**: 堆外内存 + 对象池
- **并发模型**: 单线程事件循环 + LMAX Disruptor
- **持久化**: PostgreSQL + Redis + Chronicle Queue

#### 区块链技术
- **智能合约**: Solidity (EVM兼容链)
- **Layer 2**: Arbitrum / Optimism / zkSync
- **跨链桥**: LayerZero / Wormhole
- **预言机**: Chainlink / Pyth Network
- **ZK 证明**: zkSNARKs (可选，用于隐私订单)

---

## 2. 系统架构

### 2.1 整体架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                         用户层 (User Layer)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │  Web3 钱包   │  │  WebSocket   │  │   REST API   │           │
│  │  MetaMask    │  │   实时行情    │  │   查询接口   │           │
│  └──────────────┘  └──────────────┘  └──────────────┘           │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    链下撮合层 (Off-Chain Matching)                │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  订单簿引擎 (Order Book Engine)                          │   │
│  │  - 高速撮合 (200k orders/sec)                            │   │
│  │  - 内存订单簿                                            │   │
│  │  - FIFO 价格-时间优先                                    │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  风险引擎 (Risk Engine)                                   │   │
│  │  - 保证金检查                                            │   │
│  │  - 强平监控                                              │   │
│  │  - 杠杆管理                                              │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  资金费率引擎 (Funding Rate Engine)                      │   │
│  │  - 每 8 小时计算                                         │   │
│  │  - 标记价格 vs 指数价格                                  │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                  链上结算层 (On-Chain Settlement)                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  智能合约 (Smart Contracts)                               │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │   │
│  │  │  保证金合约  │  │  清算合约    │  │  资金费率    │   │   │
│  │  │  Margin      │  │  Liquidation │  │  Funding     │   │   │
│  │  │  Contract    │  │  Contract    │  │  Contract    │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘   │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │   │
│  │  │  持仓管理    │  │  结算合约    │  │  保险基金    │   │   │
│  │  │  Position    │  │  Settlement  │  │  Insurance   │   │   │
│  │  │  Manager     │  │  Contract    │  │  Fund        │   │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  预言机层 (Oracle Layer)                                  │   │
│  │  ┌──────────────┐  ┌──────────────┐                      │   │
│  │  │  Chainlink   │  │  Pyth Network│                      │   │
│  │  │  Price Feed  │  │  Price Feed  │                      │   │
│  │  └──────────────┘  └──────────────┘                      │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                   存储层 (Storage Layer)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │  PostgreSQL  │  │  Redis Cache │  │  Chronicle   │           │
│  │  持仓/订单   │  │  热数据缓存  │  │  WAL日志     │           │
│  └──────────────┘  └──────────────┘  └──────────────┘           │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 六边形架构 (Hexagonal Architecture)

```
┌─────────────────────────────────────────────────────────────┐
│                     Inbound Adapters                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ WebSocket API│  │   REST API   │  │  Blockchain  │     │
│  │   Handler    │  │  Controller  │  │   Listener   │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
└─────────┼──────────────────┼──────────────────┼─────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                    Application Services                     │
│  ┌────────────────────────────────────────────────────┐    │
│  │  PerpetualContractService (永续合约用例编排)       │    │
│  │  - openPosition()     // 开仓                       │    │
│  │  - closePosition()    // 平仓                       │    │
│  │  - adjustLeverage()   // 调整杠杆                   │    │
│  │  - liquidatePosition() // 强制平仓                  │    │
│  │  - calculateFundingRate() // 计算资金费率          │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer (核心)                     │
│  ┌─────────────────────────────────────────────────┐       │
│  │  PerpOrderBook (永续合约订单簿聚合根)            │       │
│  │  - openLongPosition(Order): PositionResult       │       │
│  │  - openShortPosition(Order): PositionResult      │       │
│  │  - matchOrders(): List<Trade>                    │       │
│  │  - calculateUnrealizedPnL(Position): Money       │       │
│  └─────────────────────────────────────────────────┘       │
│  ┌─────────────────┐  ┌─────────────────┐                 │
│  │  Position (实体) │  │  Margin (实体)   │                 │
│  │  - id           │  │  - userId        │                 │
│  │  - symbol       │  │  - balance       │                 │
│  │  - side         │  │  - locked        │                 │
│  │  - leverage     │  │  - availMargin   │                 │
│  │  - entryPrice   │  │  - marginRatio   │                 │
│  │  - quantity     │  │                  │                 │
│  │  - unrealizedPnL│  │                  │                 │
│  │  - liquidPrice  │  │                  │                 │
│  └─────────────────┘  └─────────────────┘                 │
│  ┌─────────────────────────────────────────────────┐       │
│  │  FundingRateEngine (领域服务)                    │       │
│  │  - calculateFundingRate(Symbol): FundingRate    │       │
│  │  - settleFunding(Position): Money               │       │
│  └─────────────────────────────────────────────────┘       │
│  ┌─────────────────────────────────────────────────┐       │
│  │  LiquidationEngine (领域服务)                    │       │
│  │  - checkLiquidation(Position): boolean          │       │
│  │  - executeLiquidation(Position): LiqResult      │       │
│  │  - calculateLiqPrice(Position): Price           │       │
│  └─────────────────────────────────────────────────┘       │
│  ┌─────────────────────────────────────────────────┐       │
│  │  Ports (接口定义)                                │       │
│  │  - PositionRepository                            │       │
│  │  - MarginRepository                              │       │
│  │  - BlockchainGateway                             │       │
│  │  - OracleGateway                                 │       │
│  └─────────────────────────────────────────────────┘       │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   Outbound Adapters                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │  PostgreSQL  │  │   Arbitrum   │  │   Chainlink  │     │
│  │  Repository  │  │   Contract   │  │   Oracle     │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 核心领域模型

### 3.1 Position (持仓实体)

```java
package com.tanggo.fund.bitcoin.lib.domain.entities;

import lombok.Builder;
import lombok.Getter;
import lombok.With;
import java.math.BigDecimal;

/**
 * 永续合约持仓实体
 *
 * 核心概念:
 * - 多头 (Long): 做多，价格上涨盈利
 * - 空头 (Short): 做空，价格下跌盈利
 * - 保证金: 开仓需要锁定的抵押品
 * - 杠杆: 放大收益和风险的倍数
 */
@Getter
@Builder
public class Position {
    private final PositionId id;
    private final UserId userId;
    private final Symbol symbol;

    @With
    private final PositionSide side;          // LONG / SHORT

    @With
    private final BigDecimal leverage;        // 杠杆倍数 (1-125x)

    @With
    private final Price entryPrice;           // 开仓价格

    @With
    private final Quantity quantity;          // 持仓数量 (合约张数)

    @With
    private final Money initialMargin;        // 初始保证金

    @With
    private final Money maintenanceMargin;    // 维持保证金

    @With
    private final Price markPrice;            // 标记价格 (用于未实现盈亏计算)

    @With
    private final Price liquidationPrice;     // 强平价格

    @With
    private final PositionStatus status;      // OPEN / CLOSED / LIQUIDATED

    private final long openTimestamp;

    @With
    private final Long closeTimestamp;

    /**
     * 计算未实现盈亏 (Unrealized PnL)
     *
     * 公式:
     * - 多头: (标记价格 - 开仓价格) * 数量
     * - 空头: (开仓价格 - 标记价格) * 数量
     */
    public Money calculateUnrealizedPnL(Price currentMarkPrice) {
        BigDecimal priceDiff = side == PositionSide.LONG
            ? currentMarkPrice.getValue().subtract(entryPrice.getValue())
            : entryPrice.getValue().subtract(currentMarkPrice.getValue());

        BigDecimal pnl = priceDiff.multiply(quantity.getValue());
        return Money.of(pnl);
    }

    /**
     * 计算保证金率 (Margin Ratio)
     *
     * 公式: (初始保证金 + 未实现盈亏) / 持仓价值
     *
     * 风险等级:
     * - > 100%: 安全
     * - 50% - 100%: 健康
     * - 10% - 50%: 警告
     * - < 10%: 危险 (触发强平)
     */
    public BigDecimal calculateMarginRatio(Price currentMarkPrice) {
        Money unrealizedPnL = calculateUnrealizedPnL(currentMarkPrice);
        Money totalMargin = initialMargin.add(unrealizedPnL);

        BigDecimal positionValue = currentMarkPrice.getValue()
            .multiply(quantity.getValue());

        if (positionValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalMargin.getValue()
            .divide(positionValue, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * 检查是否触发强平
     *
     * 触发条件: 保证金率 < 维持保证金率 (通常 0.5%)
     */
    public boolean shouldLiquidate(Price currentMarkPrice) {
        if (side == PositionSide.LONG) {
            return currentMarkPrice.getValue()
                .compareTo(liquidationPrice.getValue()) <= 0;
        } else {
            return currentMarkPrice.getValue()
                .compareTo(liquidationPrice.getValue()) >= 0;
        }
    }

    /**
     * 计算强平价格
     *
     * 公式 (多头):
     * 强平价 = 开仓价 * (1 - 初始保证金率 + 维持保证金率)
     *
     * 公式 (空头):
     * 强平价 = 开仓价 * (1 + 初始保证金率 - 维持保证金率)
     */
    public static Price calculateLiquidationPrice(
        PositionSide side,
        Price entryPrice,
        BigDecimal leverage,
        BigDecimal maintenanceMarginRate
    ) {
        BigDecimal initialMarginRate = BigDecimal.ONE.divide(
            leverage, 8, RoundingMode.HALF_UP
        );

        BigDecimal factor;
        if (side == PositionSide.LONG) {
            factor = BigDecimal.ONE
                .subtract(initialMarginRate)
                .add(maintenanceMarginRate);
        } else {
            factor = BigDecimal.ONE
                .add(initialMarginRate)
                .subtract(maintenanceMarginRate);
        }

        return Price.of(
            entryPrice.getValue().multiply(factor)
        );
    }

    /**
     * 平仓
     */
    public Position close(Price closePrice, long timestamp) {
        Money realizedPnL = calculateUnrealizedPnL(closePrice);

        return this.withStatus(PositionStatus.CLOSED)
                   .withCloseTimestamp(timestamp)
                   .withMarkPrice(closePrice);
    }

    /**
     * 业务规则验证
     */
    public void validate() {
        if (leverage.compareTo(BigDecimal.ONE) < 0) {
            throw new InvalidPositionException("Leverage must be >= 1x");
        }
        if (leverage.compareTo(BigDecimal.valueOf(125)) > 0) {
            throw new InvalidPositionException("Leverage must be <= 125x");
        }
        if (quantity.isZero() || quantity.isNegative()) {
            throw new InvalidPositionException("Quantity must be positive");
        }
        if (initialMargin.isNegative()) {
            throw new InvalidPositionException("Initial margin must be positive");
        }
    }
}

/**
 * 持仓方向
 */
public enum PositionSide {
    LONG,   // 多头
    SHORT   // 空头
}

/**
 * 持仓状态
 */
public enum PositionStatus {
    OPEN,        // 开仓中
    CLOSED,      // 已平仓
    LIQUIDATED   // 已强平
}
```

### 3.2 Margin (保证金实体)

```java
package com.tanggo.fund.bitcoin.lib.domain.entities;

import lombok.Builder;
import lombok.Getter;
import lombok.With;
import java.math.BigDecimal;

/**
 * 保证金账户实体
 *
 * 支持两种模式:
 * - 全仓模式 (Cross Margin): 所有持仓共享保证金
 * - 逐仓模式 (Isolated Margin): 每个持仓独立保证金
 */
@Getter
@Builder
public class Margin {
    private final MarginId id;
    private final UserId userId;
    private final MarginMode mode;

    @With
    private final Money balance;              // 总余额

    @With
    private final Money lockedMargin;         // 已锁定保证金

    @With
    private final Money unrealizedPnL;        // 所有持仓的未实现盈亏

    @With
    private final Money availableMargin;      // 可用保证金

    /**
     * 计算可用保证金
     *
     * 公式: 余额 + 未实现盈亏 - 已锁定保证金
     */
    public Money calculateAvailableMargin() {
        return balance.add(unrealizedPnL).subtract(lockedMargin);
    }

    /**
     * 锁定保证金 (开仓时)
     */
    public Margin lockMargin(Money amount) {
        Money newLocked = lockedMargin.add(amount);
        Money newAvailable = calculateAvailableMargin().subtract(amount);

        if (newAvailable.isNegative()) {
            throw new InsufficientMarginException(
                "Available margin: " + calculateAvailableMargin() +
                ", Required: " + amount
            );
        }

        return this.withLockedMargin(newLocked)
                   .withAvailableMargin(newAvailable);
    }

    /**
     * 释放保证金 (平仓时)
     */
    public Margin unlockMargin(Money amount) {
        Money newLocked = lockedMargin.subtract(amount);
        Money newAvailable = calculateAvailableMargin().add(amount);

        return this.withLockedMargin(newLocked)
                   .withAvailableMargin(newAvailable);
    }

    /**
     * 更新未实现盈亏
     */
    public Margin updateUnrealizedPnL(Money newUnrealizedPnL) {
        return this.withUnrealizedPnL(newUnrealizedPnL)
                   .withAvailableMargin(calculateAvailableMargin());
    }

    /**
     * 结算盈亏 (平仓时)
     */
    public Margin settlePnL(Money realizedPnL) {
        Money newBalance = balance.add(realizedPnL);
        Money newUnrealizedPnL = unrealizedPnL.subtract(realizedPnL);

        return this.withBalance(newBalance)
                   .withUnrealizedPnL(newUnrealizedPnL)
                   .withAvailableMargin(calculateAvailableMargin());
    }

    /**
     * 检查是否有足够保证金开仓
     */
    public boolean hasEnoughMargin(Money requiredMargin) {
        return calculateAvailableMargin().getValue()
            .compareTo(requiredMargin.getValue()) >= 0;
    }
}

/**
 * 保证金模式
 */
public enum MarginMode {
    CROSS,      // 全仓模式
    ISOLATED    // 逐仓模式
}
```

### 3.3 FundingRate (资金费率值对象)

```java
package com.tanggo.fund.bitcoin.lib.domain.valueobjects;

import lombok.Value;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 资金费率值对象
 *
 * 资金费率机制:
 * - 目的: 维持永续合约价格锚定现货价格
 * - 计算周期: 每 8 小时
 * - 支付方向: 正费率时多头支付空头，负费率时空头支付多头
 *
 * 计算公式:
 * 资金费率 = Premium Index (溢价指数) + clamp(Interest Rate - Premium Index, 0.05%, -0.05%)
 *
 * Premium Index = (Max(0, Mark Price - Index Price) - Max(0, Index Price - Mark Price)) / Index Price
 */
@Value
public class FundingRate {
    BigDecimal rate;           // 费率 (如 0.0001 = 0.01%)
    long timestamp;            // 结算时间戳
    Symbol symbol;             // 交易对

    public static final BigDecimal MAX_FUNDING_RATE = new BigDecimal("0.0005");  // 0.05%
    public static final BigDecimal MIN_FUNDING_RATE = new BigDecimal("-0.0005"); // -0.05%
    public static final long FUNDING_INTERVAL = 8 * 60 * 60 * 1000; // 8小时

    /**
     * 计算资金费率
     *
     * @param markPrice 标记价格
     * @param indexPrice 指数价格
     * @param interestRate 利率 (通常为固定值 0.01%)
     */
    public static FundingRate calculate(
        Price markPrice,
        Price indexPrice,
        BigDecimal interestRate,
        Symbol symbol
    ) {
        // 计算溢价指数
        BigDecimal premiumIndex = calculatePremiumIndex(
            markPrice.getValue(),
            indexPrice.getValue()
        );

        // 计算资金费率
        BigDecimal fundingRate = premiumIndex.add(
            clamp(
                interestRate.subtract(premiumIndex),
                MIN_FUNDING_RATE,
                MAX_FUNDING_RATE
            )
        );

        return new FundingRate(
            fundingRate,
            System.currentTimeMillis(),
            symbol
        );
    }

    /**
     * 计算溢价指数
     */
    private static BigDecimal calculatePremiumIndex(
        BigDecimal markPrice,
        BigDecimal indexPrice
    ) {
        BigDecimal positivePremium = markPrice.subtract(indexPrice)
            .max(BigDecimal.ZERO);

        BigDecimal negativePremium = indexPrice.subtract(markPrice)
            .max(BigDecimal.ZERO);

        return positivePremium.subtract(negativePremium)
            .divide(indexPrice, 8, RoundingMode.HALF_UP);
    }

    /**
     * 限制在范围内
     */
    private static BigDecimal clamp(
        BigDecimal value,
        BigDecimal min,
        BigDecimal max
    ) {
        return value.min(max).max(min);
    }

    /**
     * 计算资金费用
     *
     * @param position 持仓
     * @return 正值表示支付，负值表示收取
     */
    public Money calculateFundingFee(Position position) {
        BigDecimal positionValue = position.getEntryPrice().getValue()
            .multiply(position.getQuantity().getValue());

        BigDecimal fee = positionValue.multiply(rate);

        // 多头持仓且费率为正，或空头持仓且费率为负，需要支付
        if ((position.getSide() == PositionSide.LONG && rate.compareTo(BigDecimal.ZERO) > 0) ||
            (position.getSide() == PositionSide.SHORT && rate.compareTo(BigDecimal.ZERO) < 0)) {
            return Money.of(fee.abs());
        } else {
            return Money.of(fee.abs().negate());
        }
    }

    /**
     * 检查是否到结算时间
     */
    public boolean shouldSettle(long currentTimestamp) {
        return currentTimestamp - timestamp >= FUNDING_INTERVAL;
    }

    /**
     * 年化收益率 (APR)
     */
    public BigDecimal toAPR() {
        // 一天 3 次结算，一年 365 天
        return rate.multiply(BigDecimal.valueOf(3 * 365));
    }
}
```

### 3.4 PerpOrderBook (永续合约订单簿聚合根)

```java
package com.tanggo.fund.bitcoin.lib.domain.entities;

import lombok.Getter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 永续合约订单簿聚合根
 *
 * 扩展现货订单簿，增加永续合约特性:
 * - 持仓管理
 * - 保证金检查
 * - 资金费率结算
 * - 强平处理
 */
@Getter
public class PerpOrderBook {
    private final Symbol symbol;

    // 买单价格树 (降序)
    private final NavigableMap<Price, PriceLevel> bids;

    // 卖单价格树 (升序)
    private final NavigableMap<Price, PriceLevel> asks;

    // 订单索引
    private final Map<OrderId, PerpOrder> orderIndex;

    // 持仓索引 (userId -> Position)
    private final Map<UserId, Position> positionIndex;

    // 标记价格 (由预言机提供)
    private volatile Price markPrice;

    // 指数价格 (由预言机提供)
    private volatile Price indexPrice;

    // 当前资金费率
    private volatile FundingRate currentFundingRate;

    // 统计信息
    private volatile long totalLongPositions;
    private volatile long totalShortPositions;
    private volatile Money totalLongValue;
    private volatile Money totalShortValue;

    public PerpOrderBook(Symbol symbol) {
        this.symbol = symbol;
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        this.asks = new TreeMap<>();
        this.orderIndex = new ConcurrentHashMap<>();
        this.positionIndex = new ConcurrentHashMap<>();
    }

    /**
     * 开多仓 (Long)
     */
    public PositionResult openLongPosition(
        PerpOrder order,
        Margin margin
    ) {
        // 1. 计算所需保证金
        Money requiredMargin = calculateRequiredMargin(
            order.getPrice(),
            order.getQuantity(),
            order.getLeverage()
        );

        // 2. 检查保证金是否足够
        if (!margin.hasEnoughMargin(requiredMargin)) {
            throw new InsufficientMarginException(
                "Required: " + requiredMargin +
                ", Available: " + margin.getAvailableMargin()
            );
        }

        // 3. 撮合订单
        MatchResult matchResult = matchBuyOrder(order);

        // 4. 创建或更新持仓
        Position position = createOrUpdatePosition(
            order.getUserId(),
            PositionSide.LONG,
            matchResult,
            order.getLeverage()
        );

        // 5. 锁定保证金
        Margin updatedMargin = margin.lockMargin(requiredMargin);

        return new PositionResult(
            position,
            updatedMargin,
            matchResult.getTrades()
        );
    }

    /**
     * 开空仓 (Short)
     */
    public PositionResult openShortPosition(
        PerpOrder order,
        Margin margin
    ) {
        Money requiredMargin = calculateRequiredMargin(
            order.getPrice(),
            order.getQuantity(),
            order.getLeverage()
        );

        if (!margin.hasEnoughMargin(requiredMargin)) {
            throw new InsufficientMarginException(
                "Required: " + requiredMargin +
                ", Available: " + margin.getAvailableMargin()
            );
        }

        MatchResult matchResult = matchSellOrder(order);

        Position position = createOrUpdatePosition(
            order.getUserId(),
            PositionSide.SHORT,
            matchResult,
            order.getLeverage()
        );

        Margin updatedMargin = margin.lockMargin(requiredMargin);

        return new PositionResult(
            position,
            updatedMargin,
            matchResult.getTrades()
        );
    }

    /**
     * 平仓
     */
    public ClosePositionResult closePosition(
        UserId userId,
        Margin margin
    ) {
        Position position = positionIndex.get(userId);
        if (position == null) {
            throw new PositionNotFoundException("No position found for user: " + userId);
        }

        // 创建平仓订单
        PerpOrder closeOrder = createCloseOrder(position);

        // 撮合
        MatchResult matchResult = position.getSide() == PositionSide.LONG
            ? matchSellOrder(closeOrder)
            : matchBuyOrder(closeOrder);

        // 计算实现盈亏
        Money realizedPnL = position.calculateUnrealizedPnL(markPrice);

        // 更新持仓状态
        Position closedPosition = position.close(markPrice, System.currentTimeMillis());

        // 释放保证金并结算盈亏
        Margin updatedMargin = margin
            .unlockMargin(position.getInitialMargin())
            .settlePnL(realizedPnL);

        // 移除持仓
        positionIndex.remove(userId);

        return new ClosePositionResult(
            closedPosition,
            updatedMargin,
            realizedPnL,
            matchResult.getTrades()
        );
    }

    /**
     * 计算所需保证金
     *
     * 公式: (价格 * 数量) / 杠杆
     */
    private Money calculateRequiredMargin(
        Price price,
        Quantity quantity,
        BigDecimal leverage
    ) {
        BigDecimal positionValue = price.getValue().multiply(quantity.getValue());
        BigDecimal margin = positionValue.divide(leverage, 8, RoundingMode.HALF_UP);
        return Money.of(margin);
    }

    /**
     * 创建或更新持仓
     */
    private Position createOrUpdatePosition(
        UserId userId,
        PositionSide side,
        MatchResult matchResult,
        BigDecimal leverage
    ) {
        Position existingPosition = positionIndex.get(userId);

        if (existingPosition != null) {
            // TODO: 处理持仓合并逻辑
            throw new UnsupportedOperationException(
                "Position merging not yet implemented"
            );
        }

        // 计算平均成交价
        Price avgPrice = calculateAveragePrice(matchResult.getTrades());

        // 计算总数量
        Quantity totalQty = calculateTotalQuantity(matchResult.getTrades());

        // 计算保证金
        Money initialMargin = calculateRequiredMargin(avgPrice, totalQty, leverage);
        Money maintenanceMargin = initialMargin.multiply(new BigDecimal("0.005")); // 0.5%

        // 计算强平价格
        Price liquidationPrice = Position.calculateLiquidationPrice(
            side,
            avgPrice,
            leverage,
            new BigDecimal("0.005")
        );

        Position position = Position.builder()
            .id(PositionId.generate())
            .userId(userId)
            .symbol(symbol)
            .side(side)
            .leverage(leverage)
            .entryPrice(avgPrice)
            .quantity(totalQty)
            .initialMargin(initialMargin)
            .maintenanceMargin(maintenanceMargin)
            .markPrice(markPrice)
            .liquidationPrice(liquidationPrice)
            .status(PositionStatus.OPEN)
            .openTimestamp(System.currentTimeMillis())
            .build();

        positionIndex.put(userId, position);

        return position;
    }

    /**
     * 更新标记价格和指数价格
     */
    public void updatePrices(Price newMarkPrice, Price newIndexPrice) {
        this.markPrice = newMarkPrice;
        this.indexPrice = newIndexPrice;

        // 重新计算资金费率
        this.currentFundingRate = FundingRate.calculate(
            newMarkPrice,
            newIndexPrice,
            new BigDecimal("0.0001"), // 固定利率 0.01%
            symbol
        );
    }

    /**
     * 检查所有持仓的强平条件
     */
    public List<Position> checkLiquidations() {
        List<Position> liquidatablePositions = new ArrayList<>();

        for (Position position : positionIndex.values()) {
            if (position.shouldLiquidate(markPrice)) {
                liquidatablePositions.add(position);
            }
        }

        return liquidatablePositions;
    }

    /**
     * 结算资金费率
     */
    public Map<UserId, Money> settleFundingRate() {
        Map<UserId, Money> fundingFees = new HashMap<>();

        for (Position position : positionIndex.values()) {
            Money fee = currentFundingRate.calculateFundingFee(position);
            fundingFees.put(position.getUserId(), fee);
        }

        return fundingFees;
    }

    // ... 其他辅助方法
}
```

---

## 4. 应用服务层

### 4.1 PerpetualContractService (永续合约用例编排)

```java
package com.tanggo.fund.bitcoin.lib.service;

import com.tanggo.fund.bitcoin.lib.domain.entities.*;
import com.tanggo.fund.bitcoin.lib.domain.repo.*;
import com.tanggo.fund.bitcoin.lib.domain.gateway.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 永续合约服务 - 用例编排
 *
 * 职责:
 * 1. 编排永续合约业务流程
 * 2. 协调保证金、持仓、订单簿
 * 3. 处理链上结算
 * 4. 管理资金费率结算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerpetualContractService {

    private final PerpOrderBookRegistry orderBookRegistry;
    private final PositionRepository positionRepository;
    private final MarginRepository marginRepository;
    private final OrderRepository orderRepository;
    private final BlockchainGateway blockchainGateway;
    private final OracleGateway oracleGateway;
    private final MarketDataPublisher marketDataPublisher;

    /**
     * 开仓用例
     *
     * 流程:
     * 1. 验证请求
     * 2. 获取标记价格
     * 3. 检查保证金
     * 4. 撮合订单
     * 5. 创建持仓
     * 6. 链上结算
     */
    @Transactional
    public OpenPositionResponse openPosition(OpenPositionRequest request) {
        // 1. 验证
        request.validate();

        // 2. 获取标记价格和指数价格
        OraclePriceData priceData = oracleGateway.getPriceData(request.getSymbol());

        // 3. 获取订单簿
        PerpOrderBook orderBook = orderBookRegistry.getOrderBook(request.getSymbol());
        orderBook.updatePrices(priceData.getMarkPrice(), priceData.getIndexPrice());

        // 4. 获取用户保证金账户
        Margin margin = marginRepository.findByUserId(request.getUserId())
            .orElseThrow(() -> new MarginAccountNotFoundException(request.getUserId()));

        // 5. 创建订单
        PerpOrder order = PerpOrder.builder()
            .id(OrderId.generate())
            .userId(request.getUserId())
            .symbol(request.getSymbol())
            .side(request.getPositionSide() == PositionSide.LONG
                ? OrderSide.BUY
                : OrderSide.SELL)
            .price(request.getPrice())
            .quantity(request.getQuantity())
            .leverage(request.getLeverage())
            .timestamp(System.nanoTime())
            .build();

        // 6. 执行开仓
        PositionResult result = request.getPositionSide() == PositionSide.LONG
            ? orderBook.openLongPosition(order, margin)
            : orderBook.openShortPosition(order, margin);

        // 7. 持久化
        positionRepository.save(result.getPosition());
        marginRepository.save(result.getMargin());
        orderRepository.save(order);

        // 8. 链上结算
        blockchainGateway.settlePosition(
            result.getPosition(),
            result.getMargin()
        );

        // 9. 发布事件
        publishPositionOpenedEvent(result);

        log.info("Position opened: userId={}, symbol={}, side={}, leverage={}x",
            request.getUserId(),
            request.getSymbol(),
            request.getPositionSide(),
            request.getLeverage());

        return new OpenPositionResponse(
            result.getPosition(),
            result.getTrades()
        );
    }

    /**
     * 平仓用例
     */
    @Transactional
    public ClosePositionResponse closePosition(ClosePositionRequest request) {
        // 1. 获取订单簿和价格
        PerpOrderBook orderBook = orderBookRegistry.getOrderBook(request.getSymbol());
        OraclePriceData priceData = oracleGateway.getPriceData(request.getSymbol());
        orderBook.updatePrices(priceData.getMarkPrice(), priceData.getIndexPrice());

        // 2. 获取保证金账户
        Margin margin = marginRepository.findByUserId(request.getUserId())
            .orElseThrow(() -> new MarginAccountNotFoundException(request.getUserId()));

        // 3. 执行平仓
        ClosePositionResult result = orderBook.closePosition(request.getUserId(), margin);

        // 4. 持久化
        positionRepository.save(result.getClosedPosition());
        marginRepository.save(result.getMargin());

        // 5. 链上结算
        blockchainGateway.settlePositionClose(
            result.getClosedPosition(),
            result.getMargin(),
            result.getRealizedPnL()
        );

        // 6. 发布事件
        publishPositionClosedEvent(result);

        log.info("Position closed: userId={}, realizedPnL={}",
            request.getUserId(),
            result.getRealizedPnL());

        return new ClosePositionResponse(
            result.getClosedPosition(),
            result.getRealizedPnL()
        );
    }

    /**
     * 调整杠杆用例
     */
    @Transactional
    public AdjustLeverageResponse adjustLeverage(AdjustLeverageRequest request) {
        // 1. 获取持仓
        Position position = positionRepository.findByUserId(request.getUserId())
            .orElseThrow(() -> new PositionNotFoundException(request.getUserId()));

        // 2. 验证新杠杆
        BigDecimal newLeverage = request.getNewLeverage();
        if (newLeverage.compareTo(BigDecimal.ONE) < 0 ||
            newLeverage.compareTo(BigDecimal.valueOf(125)) > 0) {
            throw new InvalidLeverageException("Leverage must be between 1x and 125x");
        }

        // 3. 重新计算保证金和强平价格
        Money newInitialMargin = calculateRequiredMargin(
            position.getEntryPrice(),
            position.getQuantity(),
            newLeverage
        );

        Price newLiquidationPrice = Position.calculateLiquidationPrice(
            position.getSide(),
            position.getEntryPrice(),
            newLeverage,
            new BigDecimal("0.005")
        );

        // 4. 更新持仓
        Position updatedPosition = position
            .withLeverage(newLeverage)
            .withInitialMargin(newInitialMargin)
            .withLiquidationPrice(newLiquidationPrice);

        // 5. 持久化
        positionRepository.save(updatedPosition);

        // 6. 链上更新
        blockchainGateway.updatePositionLeverage(
            updatedPosition.getId(),
            newLeverage,
            newLiquidationPrice
        );

        log.info("Leverage adjusted: userId={}, oldLeverage={}x, newLeverage={}x",
            request.getUserId(),
            position.getLeverage(),
            newLeverage);

        return new AdjustLeverageResponse(updatedPosition);
    }

    /**
     * 强制平仓用例 (由系统触发)
     */
    @Transactional
    public LiquidationResponse liquidatePosition(LiquidationRequest request) {
        // 1. 获取持仓
        Position position = positionRepository.findById(request.getPositionId())
            .orElseThrow(() -> new PositionNotFoundException(request.getPositionId()));

        // 2. 验证是否应该强平
        OraclePriceData priceData = oracleGateway.getPriceData(position.getSymbol());
        if (!position.shouldLiquidate(priceData.getMarkPrice())) {
            throw new InvalidLiquidationException(
                "Position does not meet liquidation criteria"
            );
        }

        // 3. 获取保证金账户
        Margin margin = marginRepository.findByUserId(position.getUserId())
            .orElseThrow(() -> new MarginAccountNotFoundException(position.getUserId()));

        // 4. 计算清算损失
        Money unrealizedPnL = position.calculateUnrealizedPnL(priceData.getMarkPrice());

        // 5. 更新持仓状态
        Position liquidatedPosition = position
            .withStatus(PositionStatus.LIQUIDATED)
            .withCloseTimestamp(System.currentTimeMillis());

        // 6. 更新保证金 (损失从保证金中扣除)
        Margin updatedMargin = margin
            .unlockMargin(position.getInitialMargin())
            .settlePnL(unrealizedPnL);

        // 7. 保险基金收取剩余保证金
        Money insuranceFundContribution = updatedMargin.getAvailableMargin();

        // 8. 持久化
        positionRepository.save(liquidatedPosition);
        marginRepository.save(updatedMargin);

        // 9. 链上结算
        blockchainGateway.settleLiquidation(
            liquidatedPosition,
            updatedMargin,
            insuranceFundContribution
        );

        // 10. 发布事件
        publishLiquidationEvent(liquidatedPosition, unrealizedPnL);

        log.warn("Position liquidated: userId={}, positionId={}, loss={}",
            position.getUserId(),
            position.getId(),
            unrealizedPnL);

        return new LiquidationResponse(
            liquidatedPosition,
            unrealizedPnL,
            insuranceFundContribution
        );
    }

    /**
     * 资金费率结算用例 (定时任务触发)
     */
    @Transactional
    public FundingSettlementResponse settleFunding(Symbol symbol) {
        // 1. 获取订单簿
        PerpOrderBook orderBook = orderBookRegistry.getOrderBook(symbol);

        // 2. 结算资金费率
        Map<UserId, Money> fundingFees = orderBook.settleFundingRate();

        // 3. 批量更新保证金账户
        List<Margin> updatedMargins = new ArrayList<>();
        for (Map.Entry<UserId, Money> entry : fundingFees.entrySet()) {
            Margin margin = marginRepository.findByUserId(entry.getKey())
                .orElseThrow(() -> new MarginAccountNotFoundException(entry.getKey()));

            Money fee = entry.getValue();
            // 正值表示支付，负值表示收取
            Margin updated = fee.isNegative()
                ? margin.withBalance(margin.getBalance().add(fee.abs()))
                : margin.withBalance(margin.getBalance().subtract(fee));

            updatedMargins.add(updated);
        }

        // 4. 持久化
        marginRepository.saveAll(updatedMargins);

        // 5. 链上结算
        blockchainGateway.settleFundingBatch(
            symbol,
            orderBook.getCurrentFundingRate(),
            fundingFees
        );

        log.info("Funding settled: symbol={}, rate={}, affectedUsers={}",
            symbol,
            orderBook.getCurrentFundingRate().getRate(),
            fundingFees.size());

        return new FundingSettlementResponse(
            symbol,
            orderBook.getCurrentFundingRate(),
            fundingFees
        );
    }

    // ... 辅助方法和事件发布
}
```

---

## 5. 智能合约层

### 5.1 保证金合约 (MarginContract.sol)

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";

/**
 * 保证金合约
 *
 * 功能:
 * - 用户存入/提取保证金
 * - 锁定/释放保证金
 * - 结算盈亏
 */
contract MarginContract is Ownable, ReentrancyGuard {

    IERC20 public immutable collateralToken; // 抵押品代币 (如 USDC)

    struct MarginAccount {
        uint256 balance;            // 总余额
        uint256 lockedMargin;       // 已锁定保证金
        int256 unrealizedPnL;       // 未实现盈亏
        MarginMode mode;            // 保证金模式
    }

    enum MarginMode {
        CROSS,      // 全仓
        ISOLATED    // 逐仓
    }

    mapping(address => MarginAccount) public marginAccounts;

    address public tradingEngine;      // 交易引擎地址
    address public liquidationEngine;  // 清算引擎地址

    event MarginDeposited(address indexed user, uint256 amount);
    event MarginWithdrawn(address indexed user, uint256 amount);
    event MarginLocked(address indexed user, uint256 amount);
    event MarginUnlocked(address indexed user, uint256 amount);
    event PnLSettled(address indexed user, int256 pnl);

    modifier onlyTradingEngine() {
        require(msg.sender == tradingEngine, "Only trading engine");
        _;
    }

    modifier onlyLiquidationEngine() {
        require(msg.sender == liquidationEngine, "Only liquidation engine");
        _;
    }

    constructor(address _collateralToken) {
        collateralToken = IERC20(_collateralToken);
    }

    /**
     * 存入保证金
     */
    function deposit(uint256 amount) external nonReentrant {
        require(amount > 0, "Amount must be positive");

        require(
            collateralToken.transferFrom(msg.sender, address(this), amount),
            "Transfer failed"
        );

        marginAccounts[msg.sender].balance += amount;

        emit MarginDeposited(msg.sender, amount);
    }

    /**
     * 提取保证金
     */
    function withdraw(uint256 amount) external nonReentrant {
        MarginAccount storage account = marginAccounts[msg.sender];

        uint256 availableMargin = calculateAvailableMargin(msg.sender);
        require(amount <= availableMargin, "Insufficient available margin");

        account.balance -= amount;

        require(
            collateralToken.transfer(msg.sender, amount),
            "Transfer failed"
        );

        emit MarginWithdrawn(msg.sender, amount);
    }

    /**
     * 锁定保证金 (开仓时调用)
     */
    function lockMargin(address user, uint256 amount)
        external
        onlyTradingEngine
    {
        MarginAccount storage account = marginAccounts[user];

        uint256 availableMargin = calculateAvailableMargin(user);
        require(amount <= availableMargin, "Insufficient available margin");

        account.lockedMargin += amount;

        emit MarginLocked(user, amount);
    }

    /**
     * 释放保证金 (平仓时调用)
     */
    function unlockMargin(address user, uint256 amount)
        external
        onlyTradingEngine
    {
        MarginAccount storage account = marginAccounts[user];
        require(account.lockedMargin >= amount, "Insufficient locked margin");

        account.lockedMargin -= amount;

        emit MarginUnlocked(user, amount);
    }

    /**
     * 结算盈亏 (平仓时调用)
     */
    function settlePnL(address user, int256 realizedPnL)
        external
        onlyTradingEngine
    {
        MarginAccount storage account = marginAccounts[user];

        if (realizedPnL >= 0) {
            account.balance += uint256(realizedPnL);
        } else {
            uint256 loss = uint256(-realizedPnL);
            require(account.balance >= loss, "Insufficient balance for loss");
            account.balance -= loss;
        }

        emit PnLSettled(user, realizedPnL);
    }

    /**
     * 强平结算 (清算引擎调用)
     */
    function settleLiquidation(
        address user,
        uint256 initialMargin,
        uint256 loss
    )
        external
        onlyLiquidationEngine
        returns (uint256 insuranceFundContribution)
    {
        MarginAccount storage account = marginAccounts[user];

        // 释放保证金
        require(account.lockedMargin >= initialMargin, "Invalid liquidation");
        account.lockedMargin -= initialMargin;

        // 扣除损失
        if (account.balance >= loss) {
            account.balance -= loss;
            insuranceFundContribution = initialMargin - loss;
        } else {
            // 损失超过余额，全部余额归保险基金
            insuranceFundContribution = account.balance;
            account.balance = 0;
        }

        return insuranceFundContribution;
    }

    /**
     * 计算可用保证金
     */
    function calculateAvailableMargin(address user)
        public
        view
        returns (uint256)
    {
        MarginAccount storage account = marginAccounts[user];

        int256 totalMargin = int256(account.balance) + account.unrealizedPnL;
        int256 available = totalMargin - int256(account.lockedMargin);

        return available > 0 ? uint256(available) : 0;
    }

    /**
     * 设置交易引擎地址
     */
    function setTradingEngine(address _tradingEngine) external onlyOwner {
        tradingEngine = _tradingEngine;
    }

    /**
     * 设置清算引擎地址
     */
    function setLiquidationEngine(address _liquidationEngine) external onlyOwner {
        liquidationEngine = _liquidationEngine;
    }
}
```

### 5.2 清算合约 (LiquidationContract.sol)

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./MarginContract.sol";
import "./interfaces/IPriceOracle.sol";

/**
 * 清算合约
 *
 * 功能:
 * - 监控持仓保证金率
 * - 触发强制平仓
 * - 管理保险基金
 */
contract LiquidationContract {

    struct Position {
        address user;
        bytes32 symbol;
        PositionSide side;
        uint256 quantity;
        uint256 entryPrice;
        uint256 leverage;
        uint256 initialMargin;
        uint256 liquidationPrice;
        PositionStatus status;
    }

    enum PositionSide { LONG, SHORT }
    enum PositionStatus { OPEN, CLOSED, LIQUIDATED }

    MarginContract public marginContract;
    IPriceOracle public priceOracle;

    mapping(bytes32 => Position) public positions;

    uint256 public insuranceFund;
    uint256 public constant MAINTENANCE_MARGIN_RATE = 5; // 0.5% (basis points)

    event PositionLiquidated(
        bytes32 indexed positionId,
        address indexed user,
        uint256 loss,
        uint256 insuranceFundContribution
    );

    event InsuranceFundUsed(
        bytes32 indexed positionId,
        uint256 amount
    );

    constructor(address _marginContract, address _priceOracle) {
        marginContract = MarginContract(_marginContract);
        priceOracle = IPriceOracle(_priceOracle);
    }

    /**
     * 检查并执行清算
     */
    function liquidate(bytes32 positionId) external {
        Position storage position = positions[positionId];
        require(position.status == PositionStatus.OPEN, "Position not open");

        // 获取标记价格
        uint256 markPrice = priceOracle.getMarkPrice(position.symbol);

        // 检查是否应该清算
        require(shouldLiquidate(position, markPrice), "Not liquidatable");

        // 计算损失
        int256 pnl = calculatePnL(position, markPrice);
        require(pnl < 0, "Position is profitable");

        uint256 loss = uint256(-pnl);

        // 执行清算
        uint256 insuranceContribution = marginContract.settleLiquidation(
            position.user,
            position.initialMargin,
            loss
        );

        // 更新持仓状态
        position.status = PositionStatus.LIQUIDATED;

        // 更新保险基金
        insuranceFund += insuranceContribution;

        emit PositionLiquidated(
            positionId,
            position.user,
            loss,
            insuranceContribution
        );
    }

    /**
     * 判断是否应该清算
     */
    function shouldLiquidate(Position memory position, uint256 markPrice)
        public
        pure
        returns (bool)
    {
        if (position.side == PositionSide.LONG) {
            return markPrice <= position.liquidationPrice;
        } else {
            return markPrice >= position.liquidationPrice;
        }
    }

    /**
     * 计算盈亏
     */
    function calculatePnL(Position memory position, uint256 markPrice)
        public
        pure
        returns (int256)
    {
        int256 priceDiff;

        if (position.side == PositionSide.LONG) {
            priceDiff = int256(markPrice) - int256(position.entryPrice);
        } else {
            priceDiff = int256(position.entryPrice) - int256(markPrice);
        }

        return (priceDiff * int256(position.quantity)) / 1e18;
    }

    /**
     * 使用保险基金弥补损失 (极端情况)
     */
    function useInsuranceFund(bytes32 positionId, uint256 amount)
        external
        onlyOwner
    {
        require(amount <= insuranceFund, "Insufficient insurance fund");

        insuranceFund -= amount;

        emit InsuranceFundUsed(positionId, amount);
    }
}
```

---

## 6. 低延迟优化

### 6.1 强平监控服务

```java
package com.tanggo.fund.bitcoin.lib.service;

import com.tanggo.fund.bitcoin.lib.domain.entities.Position;
import com.tanggo.fund.bitcoin.lib.domain.entities.PerpOrderBook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 强平监控服务
 *
 * 每 100ms 检查一次所有持仓的保证金率
 * 触发强平条件时立即执行清算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidationMonitorService {

    private final PerpOrderBookRegistry orderBookRegistry;
    private final PerpetualContractService perpService;
    private final ExecutorService liquidationExecutor = Executors.newFixedThreadPool(4);

    /**
     * 定时检查强平 (每100ms)
     */
    @Scheduled(fixedRate = 100)
    public void monitorLiquidations() {
        for (Symbol symbol : orderBookRegistry.getAllSymbols()) {
            checkSymbolLiquidations(symbol);
        }
    }

    private void checkSymbolLiquidations(Symbol symbol) {
        try {
            PerpOrderBook orderBook = orderBookRegistry.getOrderBook(symbol);

            // 获取需要强平的持仓列表
            List<Position> liquidatablePositions = orderBook.checkLiquidations();

            if (!liquidatablePositions.isEmpty()) {
                log.warn("Found {} positions to liquidate for symbol: {}",
                    liquidatablePositions.size(), symbol);

                // 并行执行强平
                liquidatablePositions.forEach(position -> {
                    liquidationExecutor.submit(() -> liquidatePosition(position));
                });
            }
        } catch (Exception e) {
            log.error("Error checking liquidations for symbol: " + symbol, e);
        }
    }

    private void liquidatePosition(Position position) {
        try {
            LiquidationRequest request = LiquidationRequest.builder()
                .positionId(position.getId())
                .build();

            LiquidationResponse response = perpService.liquidatePosition(request);

            log.info("Liquidation executed: positionId={}, user={}, loss={}",
                position.getId(),
                position.getUserId(),
                response.getLoss());

        } catch (Exception e) {
            log.error("Failed to liquidate position: " + position.getId(), e);
        }
    }
}
```

### 6.2 资金费率结算服务

```java
package com.tanggo.fund.bitcoin.lib.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 资金费率结算服务
 *
 * 每 8 小时结算一次资金费率
 * 结算时间: 00:00, 08:00, 16:00 UTC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundingRateSettlementService {

    private final PerpOrderBookRegistry orderBookRegistry;
    private final PerpetualContractService perpService;

    /**
     * 定时结算资金费率
     *
     * cron: 0 0 0,8,16 * * ? (每天 00:00, 08:00, 16:00 UTC)
     */
    @Scheduled(cron = "0 0 0,8,16 * * ?", zone = "UTC")
    public void settleFundingRates() {
        log.info("Starting funding rate settlement...");

        long startTime = System.currentTimeMillis();
        int totalSettled = 0;

        for (Symbol symbol : orderBookRegistry.getAllSymbols()) {
            try {
                FundingSettlementResponse response = perpService.settleFunding(symbol);
                totalSettled += response.getFundingFees().size();

                log.info("Funding settled for {}: rate={}, users={}",
                    symbol,
                    response.getFundingRate().getRate(),
                    response.getFundingFees().size());

            } catch (Exception e) {
                log.error("Failed to settle funding for symbol: " + symbol, e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Funding settlement completed: totalSettled={}, duration={}ms",
            totalSettled, duration);
    }
}
```

---

## 7. 测试策略

### 7.1 永续合约单元测试

```java
package com.tanggo.fund.bitcoin.lib.domain.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;

class PositionTest {

    @Test
    @DisplayName("多头持仓盈亏计算")
    void testLongPositionPnL() {
        // Given: 开多仓，入场价 50000，数量 1 BTC
        Position position = Position.builder()
            .side(PositionSide.LONG)
            .entryPrice(Price.of("50000"))
            .quantity(Quantity.of("1.0"))
            .leverage(BigDecimal.valueOf(10))
            .build();

        // When: 标记价格涨到 52000
        Price currentMarkPrice = Price.of("52000");
        Money unrealizedPnL = position.calculateUnrealizedPnL(currentMarkPrice);

        // Then: 盈利 2000
        assertThat(unrealizedPnL.getValue()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("空头持仓盈亏计算")
    void testShortPositionPnL() {
        // Given: 开空仓，入场价 50000，数量 1 BTC
        Position position = Position.builder()
            .side(PositionSide.SHORT)
            .entryPrice(Price.of("50000"))
            .quantity(Quantity.of("1.0"))
            .leverage(BigDecimal.valueOf(10))
            .build();

        // When: 标记价格跌到 48000
        Price currentMarkPrice = Price.of("48000");
        Money unrealizedPnL = position.calculateUnrealizedPnL(currentMarkPrice);

        // Then: 盈利 2000
        assertThat(unrealizedPnL.getValue()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("强平价格计算 - 多头 10x 杠杆")
    void testLongLiquidationPrice() {
        // Given: 多头，入场价 50000，10x 杠杆
        Price liquidationPrice = Position.calculateLiquidationPrice(
            PositionSide.LONG,
            Price.of("50000"),
            BigDecimal.valueOf(10),
            new BigDecimal("0.005") // 0.5% 维持保证金率
        );

        // Then: 强平价约为 50000 * (1 - 0.1 + 0.005) = 45250
        assertThat(liquidationPrice.getValue())
            .isCloseTo(new BigDecimal("45250"), within(new BigDecimal("10")));
    }

    @Test
    @DisplayName("保证金率计算")
    void testMarginRatio() {
        // Given: 10x 杠杆，入场价 50000，保证金 5000
        Position position = Position.builder()
            .side(PositionSide.LONG)
            .entryPrice(Price.of("50000"))
            .quantity(Quantity.of("1.0"))
            .leverage(BigDecimal.valueOf(10))
            .initialMargin(Money.of("5000"))
            .build();

        // When: 标记价格为 50000 (无盈亏)
        BigDecimal marginRatio = position.calculateMarginRatio(Price.of("50000"));

        // Then: 保证金率为 10% (5000 / 50000 * 100%)
        assertThat(marginRatio).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("强平触发检查 - 多头")
    void testLongLiquidationTrigger() {
        // Given: 多头，强平价 45250
        Position position = Position.builder()
            .side(PositionSide.LONG)
            .liquidationPrice(Price.of("45250"))
            .build();

        // When: 标记价格跌破强平价
        boolean shouldLiquidate = position.shouldLiquidate(Price.of("45200"));

        // Then: 应该触发强平
        assertThat(shouldLiquidate).isTrue();
    }

    @Test
    @DisplayName("资金费率计算")
    void testFundingRateCalculation() {
        // Given: 标记价格 50100，指数价格 50000
        FundingRate fundingRate = FundingRate.calculate(
            Price.of("50100"),
            Price.of("50000"),
            new BigDecimal("0.0001"), // 0.01% 利率
            Symbol.of("BTCUSDT")
        );

        // Then: 资金费率应该为正 (多头支付空头)
        assertThat(fundingRate.getRate()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("资金费用计算 - 多头持仓正费率")
    void testFundingFeeForLongPosition() {
        // Given: 多头持仓，资金费率 0.01%
        Position position = Position.builder()
            .side(PositionSide.LONG)
            .entryPrice(Price.of("50000"))
            .quantity(Quantity.of("1.0"))
            .build();

        FundingRate fundingRate = new FundingRate(
            new BigDecimal("0.0001"), // 0.01%
            System.currentTimeMillis(),
            Symbol.of("BTCUSDT")
        );

        // When: 计算资金费用
        Money fundingFee = fundingRate.calculateFundingFee(position);

        // Then: 多头支付，费用为正 = 50000 * 1.0 * 0.0001 = 5
        assertThat(fundingFee.getValue()).isEqualByComparingTo("5");
        assertThat(fundingFee.isPositive()).isTrue();
    }
}
```

---

## 8. 部署和运维

### 8.1 链下服务部署

```yaml
# docker-compose.yml
version: '3.8'

services:
  # 撮合引擎
  matching-engine:
    image: perp-dex-matching:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JVM_OPTS=-Xms16G -Xmx16G -XX:+UseZGC
    depends_on:
      - postgres
      - redis
    deploy:
      resources:
        limits:
          cpus: '8'
          memory: 32G

  # PostgreSQL 数据库
  postgres:
    image: postgres:16
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: perp_dex
      POSTGRES_USER: perp_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data

  # Redis 缓存
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 8gb --maxmemory-policy allkeys-lru

  # Kafka 消息队列
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092

volumes:
  postgres_data:
```

### 8.2 智能合约部署脚本

```javascript
// scripts/deploy.js
const { ethers } = require("hardhat");

async function main() {
  const [deployer] = await ethers.getSigners();

  console.log("Deploying contracts with account:", deployer.address);
  console.log("Account balance:", (await deployer.getBalance()).toString());

  // 1. 部署保证金合约
  const MarginContract = await ethers.getContractFactory("MarginContract");
  const USDC_ADDRESS = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"; // Mainnet USDC

  const marginContract = await MarginContract.deploy(USDC_ADDRESS);
  await marginContract.deployed();
  console.log("MarginContract deployed to:", marginContract.address);

  // 2. 部署清算合约
  const LiquidationContract = await ethers.getContractFactory("LiquidationContract");
  const CHAINLINK_BTC_USD = "0xF4030086522a5bEEa4988F8cA5B36dbC97BeE88c";

  const liquidationContract = await LiquidationContract.deploy(
    marginContract.address,
    CHAINLINK_BTC_USD
  );
  await liquidationContract.deployed();
  console.log("LiquidationContract deployed to:", liquidationContract.address);

  // 3. 设置权限
  await marginContract.setLiquidationEngine(liquidationContract.address);
  console.log("Liquidation engine set");

  // 4. 验证合约
  console.log("\nVerifying contracts on Etherscan...");

  await run("verify:verify", {
    address: marginContract.address,
    constructorArguments: [USDC_ADDRESS],
  });

  await run("verify:verify", {
    address: liquidationContract.address,
    constructorArguments: [marginContract.address, CHAINLINK_BTC_USD],
  });

  console.log("\nDeployment completed!");

  // 保存部署地址
  const deploymentInfo = {
    network: network.name,
    marginContract: marginContract.address,
    liquidationContract: liquidationContract.address,
    deployer: deployer.address,
    timestamp: new Date().toISOString()
  };

  fs.writeFileSync(
    `deployments/${network.name}.json`,
    JSON.stringify(deploymentInfo, null, 2)
  );
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
```

---

## 9. 监控和告警

### 9.1 监控指标

```java
package com.tanggo.fund.bitcoin.lib.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 永续合约监控指标
 */
@Component
@RequiredArgsConstructor
public class PerpMetrics {

    private final MeterRegistry registry;

    // 持仓相关指标
    public void recordPositionOpened(PositionSide side, BigDecimal leverage) {
        Counter.builder("perp.position.opened")
            .tag("side", side.name())
            .tag("leverage", String.valueOf(leverage.intValue()))
            .register(registry)
            .increment();
    }

    public void recordPositionClosed(Money realizedPnL) {
        Counter.builder("perp.position.closed")
            .register(registry)
            .increment();

        registry.gauge("perp.position.pnl", realizedPnL.getValue().doubleValue());
    }

    public void recordLiquidation(Money loss) {
        Counter.builder("perp.liquidation.count")
            .register(registry)
            .increment();

        registry.summary("perp.liquidation.loss")
            .record(loss.getValue().doubleValue());
    }

    // 资金费率指标
    public void recordFundingRate(Symbol symbol, BigDecimal rate) {
        Gauge.builder("perp.funding.rate", () -> rate.doubleValue())
            .tag("symbol", symbol.getValue())
            .register(registry);
    }

    // 风险指标
    public void recordTotalOpenInterest(Money value) {
        Gauge.builder("perp.open_interest.total", () -> value.getValue().doubleValue())
            .register(registry);
    }

    public void recordInsuranceFundBalance(Money balance) {
        Gauge.builder("perp.insurance_fund.balance", () -> balance.getValue().doubleValue())
            .register(registry);
    }

    // 性能指标
    public void recordPositionOpenLatency(long nanos) {
        Timer.builder("perp.position.open.latency")
            .publishPercentiles(0.5, 0.95, 0.99, 0.999)
            .register(registry)
            .record(nanos, TimeUnit.NANOSECONDS);
    }
}
```

### 9.2 告警规则 (Prometheus)

```yaml
# prometheus-alerts.yml
groups:
  - name: perp_dex_alerts
    interval: 10s
    rules:
      # 强平告警
      - alert: HighLiquidationRate
        expr: rate(perp_liquidation_count[5m]) > 10
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "High liquidation rate detected"
          description: "Liquidation rate is {{ $value }} per minute"

      # 保险基金告警
      - alert: LowInsuranceFund
        expr: perp_insurance_fund_balance < 100000
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Insurance fund running low"
          description: "Insurance fund balance: {{ $value }} USDC"

      # 资金费率异常
      - alert: AbnormalFundingRate
        expr: abs(perp_funding_rate) > 0.001
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Abnormal funding rate"
          description: "Funding rate is {{ $value }}%"

      # 订单簿深度告警
      - alert: LowOrderBookDepth
        expr: orderbook_depth_total < 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Low order book depth"
          description: "Total depth: {{ $value }} orders"

      # 撮合延迟告警
      - alert: HighMatchingLatency
        expr: histogram_quantile(0.99, perp_position_open_latency) > 0.05
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High matching latency"
          description: "P99 latency: {{ $value }}s"
```

---

## 10. 安全和风险控制

### 10.1 风险参数配置

```java
package com.tanggo.fund.bitcoin.lib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 风险控制参数配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "perp.risk")
public class RiskConfig {

    // 杠杆限制
    private BigDecimal maxLeverage = BigDecimal.valueOf(125);
    private BigDecimal minLeverage = BigDecimal.ONE;

    // 保证金率
    private BigDecimal initialMarginRate = new BigDecimal("0.01");      // 1%
    private BigDecimal maintenanceMarginRate = new BigDecimal("0.005"); // 0.5%

    // 持仓限制
    private Money maxPositionSize = Money.of("10000000"); // 1000万 USDC
    private Money minPositionSize = Money.of("10");       // 10 USDC

    // 资金费率限制
    private BigDecimal maxFundingRate = new BigDecimal("0.0005");  // 0.05%
    private BigDecimal minFundingRate = new BigDecimal("-0.0005"); // -0.05%

    // ADL (自动减仓) 参数
    private BigDecimal adlThreshold = new BigDecimal("0.02"); // 2% 保证金率触发 ADL
    private int adlMaxPositions = 100; // 每次最多减仓 100 个持仓

    // 保险基金
    private Money insuranceFundMinBalance = Money.of("100000"); // 最低 10 万 USDC

    // 价格限制
    private BigDecimal maxPriceDeviation = new BigDecimal("0.1"); // 最大价格偏离 10%
}
```

### 10.2 安全审计清单

- [ ] **智能合约审计**: 通过 CertiK, OpenZeppelin 等专业机构审计
- [ ] **重入攻击防护**: 使用 ReentrancyGuard
- [ ] **整数溢出防护**: Solidity 0.8+ 自动检查
- [ ] **权限控制**: Ownable + 多签钱包
- [ ] **时间锁**: 关键操作延迟执行
- [ ] **价格操纵防护**: 使用多个预言机源
- [ ] **MEV 防护**: 批量处理订单
- [ ] **前端运行防护**: 订单签名 + nonce
- [ ] **DDoS 防护**: 速率限制 + Cloudflare
- [ ] **私钥管理**: 使用 HSM 或 KMS

---

## 11. 参考资料

### 11.1 行业标准

- [Hyperliquid Whitepaper](https://hyperliquid.xyz/whitepaper.pdf) - 链上 CLOB 架构
- [dYdX v4 Documentation](https://docs.dydx.exchange/v4) - Cosmos SDK 实现
- [Binance Futures API](https://binance-docs.github.io/apidocs/futures/en/) - API 设计参考
- [FTX Liquidations Engine](https://help.ftx.com/hc/en-us/articles/360024780511-Liquidations) - 强平机制

### 11.2 技术文档

- [LMAX Disruptor](https://lmax-exchange.github.io/disruptor/) - 低延迟撮合
- [Chronicle Queue](https://chronicle.software/chronicle-queue/) - 高性能 WAL
- [Chainlink Price Feeds](https://docs.chain.link/data-feeds) - 预言机集成
- [LayerZero](https://layerzero.network/developers) - 跨链桥接
- [OpenZeppelin Contracts](https://docs.openzeppelin.com/contracts/) - 安全合约库

### 11.3 DDD 和架构

- [Domain-Driven Design Reference](https://www.domainlanguage.com/ddd/reference/)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Clean Architecture by Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

---

## 12. 附录

### A. 术语表

| 术语 | 英文 | 说明 |
|-----|------|-----|
| 永续合约 | Perpetual Contract | 无到期日的期货合约 |
| 资金费率 | Funding Rate | 用于锚定永续合约价格的费率机制 |
| 标记价格 | Mark Price | 用于计算未实现盈亏的参考价格 |
| 指数价格 | Index Price | 多个现货交易所的加权平均价格 |
| 保证金 | Margin | 开仓所需的抵押品 |
| 杠杆 | Leverage | 放大收益和风险的倍数 |
| 强制平仓 | Liquidation | 保证金不足时的自动平仓 |
| 全仓模式 | Cross Margin | 所有持仓共享保证金 |
| 逐仓模式 | Isolated Margin | 每个持仓独立保证金 |
| ADL | Auto-Deleveraging | 自动减仓机制 |
| PnL | Profit and Loss | 盈亏 |
| Open Interest | Open Interest | 未平仓合约总价值 |

### B. 变更历史

| 版本 | 日期 | 变更内容 |
|-----|------|---------|
| v1.0 | 2025-10-14 | 初始版本：永续合约 DEX CLOB 完整设计 |

---

**文档维护者**: Bitcoin-DDD Team
**最后更新**: 2025-10-14
**状态**: 待评审