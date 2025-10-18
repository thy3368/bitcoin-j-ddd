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
@Repository
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

        String address = account.getAddress();
        if (address == null || address.isEmpty()) {
            log.error("Account address is null or empty, cannot save to MDBX");
            throw new IllegalArgumentException("Account must have a valid address");
        }

        update(address, account);
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
     * 格式: [address长度(4字节)][address][nonce(4字节)][balance(4字节)][bytecode_hash长度(4字节)][bytecode_hash]
     */
    private ByteBuffer serialize(Account account) {
        byte[] addressBytes = account.getAddress() != null
                ? account.getAddress().getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        byte[] hashBytes = account.getBytecode_hash() != null
                ? account.getBytecode_hash().getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        int bufferSize = 4 + addressBytes.length + 4 + 4 + 4 + hashBytes.length;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);

        // 写入address
        buffer.putInt(addressBytes.length);
        buffer.put(addressBytes);

        // 写入账户数据
        buffer.putInt(account.getNonce());
        buffer.putInt(account.getBalance());

        // 写入bytecode_hash
        buffer.putInt(hashBytes.length);
        buffer.put(hashBytes);

        buffer.flip();
        return buffer;
    }

    /**
     * 反序列化ByteBuffer为Account对象
     */
    private Account deserialize(ByteBuffer buffer) {
        Account account = new Account();

        // 读取address
        int addressLen = buffer.getInt();
        if (addressLen > 0) {
            byte[] addressBytes = new byte[addressLen];
            buffer.get(addressBytes);
            account.setAddress(new String(addressBytes, StandardCharsets.UTF_8));
        }

        // 读取账户数据
        account.setNonce(buffer.getInt());
        account.setBalance(buffer.getInt());

        // 读取bytecode_hash
        int hashLen = buffer.getInt();
        if (hashLen > 0) {
            byte[] hashBytes = new byte[hashLen];
            buffer.get(hashBytes);
            account.setBytecode_hash(new String(hashBytes, StandardCharsets.UTF_8));
        }

        return account;
    }
}
