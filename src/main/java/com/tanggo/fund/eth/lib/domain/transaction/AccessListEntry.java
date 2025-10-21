package com.tanggo.fund.eth.lib.domain.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 访问列表条目 - EIP-2930
 * 用于预声明交易将访问的账户和存储槽，优化Gas成本
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessListEntry {

    /**
     * 账户地址 (20 bytes)
     * 交易将访问的账户地址
     */
    private byte[] address;

    /**
     * 存储键列表
     * 该账户将被访问的存储槽位置（每个32字节）
     */
    @Builder.Default
    private List<byte[]> storageKeys = new ArrayList<>();

    /**
     * 验证访问列表条目的有效性
     * @return 是否有效
     */
    public boolean validate() {
        // 地址必须是20字节
        if (address == null || address.length != 20) {
            return false;
        }

        // 存储键不能为null
        if (storageKeys == null) {
            return false;
        }

        // 每个存储键必须是32字节
        for (byte[] key : storageKeys) {
            if (key == null || key.length != 32) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取存储键数量
     * @return 存储键数量
     */
    public int getStorageKeyCount() {
        return storageKeys != null ? storageKeys.size() : 0;
    }

    /**
     * 添加存储键
     * @param storageKey 存储键（32字节）
     */
    public void addStorageKey(byte[] storageKey) {
        if (storageKey == null || storageKey.length != 32) {
            throw new IllegalArgumentException("Storage key must be 32 bytes");
        }
        if (storageKeys == null) {
            storageKeys = new ArrayList<>();
        }
        storageKeys.add(storageKey);
    }
}