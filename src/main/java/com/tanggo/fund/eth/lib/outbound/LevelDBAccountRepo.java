package com.tanggo.fund.eth.lib.outbound;

import com.tanggo.fund.eth.lib.domain.Account;
import com.tanggo.fund.eth.lib.domain.repo.IAccountRepo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.iq80.leveldb.impl.Iq80DBFactory.*;

/**
 * Account仓储实现 - 使用LevelDB作为持久化存储
 * LevelDB是Google开发的高性能嵌入式键值存储库，特点是快速写入和有序存储
 */
@Slf4j
@Repository("levelDBAccountRepo")
public class LevelDBAccountRepo implements IAccountRepo {

    private DB db;

    @Value("${leveldb.path:./data/leveldb-accounts}")
    private String dbPath;

    @Value("${leveldb.cacheSize:104857600}")  // 100MB缓存
    private long cacheSize;

    @Value("${leveldb.createIfMissing:true}")
    private boolean createIfMissing;

    @Value("${leveldb.compression:true}")
    private boolean compression;

    /**
     * 初始化LevelDB数据库
     */
    @PostConstruct
    public void init() {
        try {
            File dbFile = new File(dbPath);
            if (!dbFile.exists() && createIfMissing) {
                boolean created = dbFile.mkdirs();
                if (!created && !dbFile.exists()) {
                    throw new RuntimeException("Failed to create LevelDB directory: " + dbPath);
                }
            }

            Options options = new Options();
            options.createIfMissing(createIfMissing);
            options.cacheSize(cacheSize);

            // 配置压缩（LevelDB默认使用Snappy压缩）
            if (compression) {
                options.compressionType(org.iq80.leveldb.CompressionType.SNAPPY);
            } else {
                options.compressionType(org.iq80.leveldb.CompressionType.NONE);
            }

            // 打开数据库
            db = factory.open(dbFile, options);

            log.info("LevelDB initialized successfully at: {}, cache size: {}MB, compression: {}",
                    dbPath, cacheSize / 1024 / 1024, compression);
        } catch (IOException e) {
            log.error("Failed to initialize LevelDB", e);
            throw new RuntimeException("LevelDB initialization failed", e);
        }
    }

    /**
     * 关闭LevelDB数据库
     */
    @PreDestroy
    public void close() {
        if (db != null) {
            try {
                db.close();
                log.info("LevelDB closed successfully");
            } catch (IOException e) {
                log.error("Failed to close LevelDB", e);
            }
        }
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

        try {
            byte[] key = address.getBytes(StandardCharsets.UTF_8);
            byte[] value = db.get(key);

            if (value == null) {
                log.debug("Account not found: {}", address);
                return null;
            }

            Account account = deserialize(value);
            log.debug("Account queried from LevelDB: {} -> nonce={}, balance={}",
                    address, account.getNonce(), account.getBalance());
            return account;
        } catch (Exception e) {
            log.error("Failed to query account from LevelDB: {}", address, e);
            return null;
        }
    }

    /**
     * 更新（保存）账户到LevelDB
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
            log.error("Account address is null or empty, cannot save to LevelDB");
            throw new IllegalArgumentException("Account must have a valid address");
        }

        try {
            byte[] key = address.getBytes(StandardCharsets.UTF_8);
            byte[] value = serialize(account);

            db.put(key, value);

            log.debug("Account saved to LevelDB: {} -> nonce={}, balance={}",
                    address, account.getNonce(), account.getBalance());
        } catch (Exception e) {
            log.error("Failed to update account in LevelDB: {}", address, e);
            throw new RuntimeException("Failed to save account to LevelDB", e);
        }
    }

    /**
     * 批量更新账户（使用WriteBatch提升性能）
     * @param accounts 账户数组
     */
    public void batchUpdate(Account... accounts) {
        if (accounts == null || accounts.length == 0) {
            return;
        }

        try (WriteBatch batch = db.createWriteBatch()) {
            for (Account account : accounts) {
                if (account != null && account.getAddress() != null) {
                    byte[] key = account.getAddress().getBytes(StandardCharsets.UTF_8);
                    byte[] value = serialize(account);
                    batch.put(key, value);
                }
            }
            db.write(batch);
            log.debug("Batch updated {} accounts to LevelDB", accounts.length);
        } catch (Exception e) {
            log.error("Failed to batch update accounts in LevelDB", e);
            throw new RuntimeException("Failed to batch save accounts to LevelDB", e);
        }
    }

    /**
     * 删除账户
     * @param address 账户地址
     */
    public void delete(String address) {
        if (address == null || address.isEmpty()) {
            return;
        }

        try {
            byte[] key = address.getBytes(StandardCharsets.UTF_8);
            db.delete(key);
            log.debug("Account deleted from LevelDB: {}", address);
        } catch (Exception e) {
            log.error("Failed to delete account from LevelDB: {}", address, e);
        }
    }

    /**
     * 获取所有账户的迭代器（用于批量处理）
     * 注意：使用完毕后必须关闭迭代器
     */
    public DBIterator iterator() {
        return db.iterator();
    }

    /**
     * 序列化Account对象为字节数组
     * 格式: [address长度(4字节)][address][nonce(4字节)][balance(4字节)][bytecode_hash长度(4字节)][bytecode_hash]
     */
    private byte[] serialize(Account account) {
        byte[] addressBytes = account.getAddress() != null
                ? account.getAddress().getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        byte[] hashBytes = account.getBytecode_hash() != null
                ? account.getBytecode_hash().getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        int bufferSize = 4 + addressBytes.length + 4 + 4 + 4 + hashBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        // 写入address
        buffer.putInt(addressBytes.length);
        buffer.put(addressBytes);

        // 写入账户数据
        buffer.putInt(account.getNonce());
        buffer.putInt(account.getBalance());

        // 写入bytecode_hash
        buffer.putInt(hashBytes.length);
        buffer.put(hashBytes);

        return buffer.array();
    }

    /**
     * 反序列化字节数组为Account对象
     */
    private Account deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
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
