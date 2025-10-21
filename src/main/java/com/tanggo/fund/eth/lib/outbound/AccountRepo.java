package com.tanggo.fund.eth.lib.outbound;

import com.tanggo.fund.eth.lib.domain.Account;
import com.tanggo.fund.eth.lib.domain.repo.IAccountRepo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.lmdbjava.EnvFlags.MDB_NOTLS;

/**
 * Account仓储实现 - 使用MDBX（LMDB）作为持久化存储
 * MDBX是一个高性能、轻量级的嵌入式键值存储库
 */
@Slf4j
//@Repository
public class AccountRepo implements IAccountRepo {

    private Env<ByteBuffer> env;
    private Dbi<ByteBuffer> db;

    @Value("${mdbx.path:./data/accounts}")
    private String dbPath;

    @Value("${mdbx.size:100MB}")
    private long dbSize = 100 * 1024 * 1024; // 100MB

    /**
     * 初始化MDBX环境和数据库
     */
    @PostConstruct
    public void init() {
        try {
            File dbFile = new File(dbPath);
            if (!dbFile.exists()) {
                boolean created = dbFile.mkdirs();
                if (!created && !dbFile.exists()) {
                    throw new RuntimeException("Failed to create MDBX directory: " + dbPath);
                }
            }

            // 创建LMDB环境
            env = Env.create()
                    .setMapSize(dbSize)
                    .setMaxDbs(1)
                    .setMaxReaders(126)
                    .open(dbFile, MDB_NOTLS);

            // 打开数据库
            db = env.openDbi("accounts", DbiFlags.MDB_CREATE);

            log.info("MDBX initialized successfully at: {}", dbPath);
        } catch (Exception e) {
            log.error("Failed to initialize MDBX", e);
            throw new RuntimeException("MDBX initialization failed", e);
        }
    }

    /**
     * 关闭MDBX环境
     */
    @PreDestroy
    public void close() {
        if (db != null) {
            db.close();
        }
        if (env != null) {
            env.close();
        }
        log.info("MDBX closed successfully");
    }

    /**
     * 查询账户
     * @param address 账户地址
     * @return Account对象，如果不存在则返回null
     */
    @Override
    public Account query(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }

        try (Txn<ByteBuffer> txn = env.txnRead()) {
            ByteBuffer key = ByteBuffer.allocateDirect(address.getBytes(StandardCharsets.UTF_8).length);
            key.put(address.getBytes(StandardCharsets.UTF_8)).flip();

            ByteBuffer value = db.get(txn, key);
            if (value == null) {
                log.debug("Account not found: {}", address);
                return null;
            }

            Account account = deserialize(value);
            log.debug("Account queried: {} -> nonce={}, balance={}", address, account.getNonce(), account.getBalance());
            return account;
        } catch (Exception e) {
            log.error("Failed to query account: {}", address, e);
            return null;
        }
    }

    /**
     * 更新（保存）账户到MDBX
     * 使用Account对象自身的address作为key
     * @param account Account对象
     */
    @Override
    public void update(Account account) {
        if (account == null) {
            log.warn("Cannot update null account");
            return;
        }

        byte[] address = account.getAddress();
        if (address == null || address.length == 0) {
            log.error("Account address is null or empty, cannot save to MDBX");
            throw new IllegalArgumentException("Account must have a valid address");
        }

        String addressHex = account.getAddressHex();
        update(addressHex, account);
    }

    /**
     * 更新（保存）账户到MDBX
     * @param address 账户地址
     * @param account Account对象
     */
    public void update(String address, Account account) {
        if (address == null || address.isEmpty() || account == null) {
            log.warn("Invalid parameters for update: address={}, account={}", address, account);
            return;
        }

        try (Txn<ByteBuffer> txn = env.txnWrite()) {
            ByteBuffer key = ByteBuffer.allocateDirect(address.getBytes(StandardCharsets.UTF_8).length);
            key.put(address.getBytes(StandardCharsets.UTF_8)).flip();

            ByteBuffer value = serialize(account);
            db.put(txn, key, value);

            txn.commit();
            log.debug("Account saved to MDBX: {} -> nonce={}, balance={}", address, account.getNonce(), account.getBalance());
        } catch (Exception e) {
            log.error("Failed to update account: {}", address, e);
            throw new RuntimeException("Failed to save account to MDBX", e);
        }
    }

    /**
     * 序列化Account对象为ByteBuffer
     * 格式: [address(20字节)][nonce长度(4字节)][nonce][balance长度(4字节)][balance]
     *       [storageRoot(32字节)][codeHash(32字节)]
     */
    private ByteBuffer serialize(Account account) {
        byte[] addressBytes = account.getAddress() != null ? account.getAddress() : new byte[20];
        byte[] nonceBytes = account.getNonce() != null ? account.getNonce().toByteArray() : new byte[0];
        byte[] balanceBytes = account.getBalance() != null ? account.getBalance().toByteArray() : new byte[0];
        byte[] storageRoot = account.getStorageRoot() != null ? account.getStorageRoot() : new byte[32];
        byte[] codeHash = account.getCodeHash() != null ? account.getCodeHash() : new byte[32];

        int bufferSize = 20 + 4 + nonceBytes.length + 4 + balanceBytes.length + 32 + 32;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);

        // 写入address (固定20字节)
        buffer.put(addressBytes);

        // 写入nonce
        buffer.putInt(nonceBytes.length);
        buffer.put(nonceBytes);

        // 写入balance
        buffer.putInt(balanceBytes.length);
        buffer.put(balanceBytes);

        // 写入storageRoot (固定32字节)
        buffer.put(storageRoot);

        // 写入codeHash (固定32字节)
        buffer.put(codeHash);

        buffer.flip();
        return buffer;
    }

    /**
     * 反序列化ByteBuffer为Account对象
     */
    private Account deserialize(ByteBuffer buffer) {
        // 读取address (20字节)
        byte[] addressBytes = new byte[20];
        buffer.get(addressBytes);

        // 读取nonce
        int nonceLen = buffer.getInt();
        byte[] nonceBytes = new byte[nonceLen];
        if (nonceLen > 0) {
            buffer.get(nonceBytes);
        }

        // 读取balance
        int balanceLen = buffer.getInt();
        byte[] balanceBytes = new byte[balanceLen];
        if (balanceLen > 0) {
            buffer.get(balanceBytes);
        }

        // 读取storageRoot (32字节)
        byte[] storageRoot = new byte[32];
        buffer.get(storageRoot);

        // 读取codeHash (32字节)
        byte[] codeHash = new byte[32];
        buffer.get(codeHash);

        return Account.builder()
                .address(addressBytes)
                .nonce(nonceLen > 0 ? new java.math.BigInteger(nonceBytes) : java.math.BigInteger.ZERO)
                .balance(balanceLen > 0 ? new java.math.BigInteger(balanceBytes) : java.math.BigInteger.ZERO)
                .storageRoot(storageRoot)
                .codeHash(codeHash)
                .build();
    }
}
