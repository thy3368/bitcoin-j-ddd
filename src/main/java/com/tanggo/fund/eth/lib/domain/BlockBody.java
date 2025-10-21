package com.tanggo.fund.eth.lib.domain;

import com.tanggo.fund.eth.lib.domain.transaction.Eip1559Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 以太坊区块体
 * 包含交易列表和叔块（ommers）列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockBody {

    /**
     * 交易列表
     * 区块中包含的所有交易
     */
    @Builder.Default
    private List<Eip1559Transaction> transactions = new ArrayList<>();

    /**
     * 叔块（Ommers/Uncles）列表
     * 最多包含2个叔块头
     * 注意：在PoS（合并后）的以太坊中，此字段为空
     */
    @Builder.Default
    private List<Header> ommers = new ArrayList<>();

    /**
     * 提款列表 - EIP-4895（上海升级）
     * 验证者的提款请求
     * null表示上海升级之前的区块
     */
    private List<Withdrawal> withdrawals;

    /**
     * 验证区块体的基本有效性
     * @return 是否有效
     */
    public boolean validate() {
        // 交易列表不能为null
        if (transactions == null) {
            return false;
        }

        // 叔块列表不能为null
        if (ommers == null) {
            return false;
        }

        // 叔块数量限制：最多2个
        if (ommers.size() > 2) {
            return false;
        }

        // 验证所有叔块头
        for (Header ommerHeader : ommers) {
            if (ommerHeader == null || !ommerHeader.validate()) {
                return false;
            }
        }

        // 如果有提款，验证提款列表
        if (withdrawals != null) {
            for (Withdrawal withdrawal : withdrawals) {
                if (withdrawal == null) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 获取交易数量
     * @return 交易数量
     */
    public int getTransactionCount() {
        return transactions != null ? transactions.size() : 0;
    }

    /**
     * 获取叔块数量
     * @return 叔块数量
     */
    public int getOmmerCount() {
        return ommers != null ? ommers.size() : 0;
    }

    /**
     * 判断是否有叔块
     * @return 是否有叔块
     */
    public boolean hasOmmers() {
        return ommers != null && !ommers.isEmpty();
    }

    /**
     * 判断是否有提款
     * @return 是否有提款
     */
    public boolean hasWithdrawals() {
        return withdrawals != null && !withdrawals.isEmpty();
    }
}
