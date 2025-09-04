package core;

import main.Settings;
import org.rocksdb.*;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Database {
    private static RocksDB db;

    static {
        RocksDB.loadLibrary();
        try {
            Options options = new Options()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true)

                    // ---- Performance (keep from previous "good" defaults) ----
                    .setIncreaseParallelism(Runtime.getRuntime().availableProcessors())
                    .setMaxBackgroundCompactions(Math.max(4, Runtime.getRuntime().availableProcessors()))
                    .setMaxBackgroundFlushes(2)
                    .setWriteBufferSize(64L * 1024 * 1024) // 64 MB memtable
                    .setMaxWriteBufferNumber(3)
                    .setMinWriteBufferNumberToMerge(1)
                    .setLevel0FileNumCompactionTrigger(4)
                    .setTargetFileSizeBase(64L * 1024 * 1024)
                    .setMaxOpenFiles(-1)
                    .setBytesPerSync(1L * 1024 * 1024)      // fsync pacing
                    .setWalBytesPerSync(1L * 1024 * 1024);  // WAL fsync pacing

            // ---- Table / read path ----
            BlockBasedTableConfig table = new BlockBasedTableConfig()
                    .setBlockCacheSize(128L * 1024 * 1024)
                    .setFilterPolicy(new BloomFilter(10, false))
                    .setCacheIndexAndFilterBlocks(true);
            options.setTableFormatConfig(table);

            // ---- LOGGING: keep logs tiny & rotated ----
            options
                    .setDbLogDir("data/rocksdb/logs")   // put logs under a subdir
                    .setInfoLogLevel(InfoLogLevel.FATAL_LEVEL) // cut INFO noise
                    .setKeepLogFileNum(2)               // keep at most 5 log files
                    .setMaxLogFileSize(8L * 1024 * 1024) // ~8 MB per log file
                    .setLogFileTimeToRoll(60L * 60);     // rotate at least hourly
            // Optional: avoid periodic stats dumps spamming logs
            options.setStatsDumpPeriodSec(0);            // disable; or set to 3600 for hourly

            // ---- WAL RETENTION: cap how much WAL sits on disk ----
            // Old WALs are needed only until their data is flushed/compacted.
            // These caps ensure they don’t grow unbounded.
            options
                    .setWalTtlSeconds(6L * 60 * 60)      // delete WALs older than 6 hours
                    .setWalSizeLimitMB(1024)             // or if total WAL > 1 GB, start deleting old
                    .setMaxTotalWalSize(512L * 1024 * 1024); // backpressure to keep WALs ~≤512MB

            // Open DB
            db = RocksDB.open(options, "data/rocksdb");

            // ---- Clean shutdown: make sure logs/WAL/SST are closed properly ----
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (db != null) {
                    while (Synchronizer.getSubscription().isRunning()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    while (Settings.nonDaemonExecutor.getActiveCount() > 0) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    db.close();
                }
            }));
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to initialize RocksDB", e);
        }
    }


    public static void put(byte[] projectId, byte[] key, byte[] value) throws RocksDBException {
        byte[] dbKey = getDbKey(projectId, key);
        db.put(dbKey, value);
    }

    public static byte[] get(byte[] projectId, byte[] key) throws RocksDBException {
        byte[] dbKey = getDbKey(projectId, key);
        return db.get(dbKey);
    }

    private static byte[] getDbKey(byte[] projectId, byte[] key) {
        ByteBuffer buffer = ByteBuffer.allocate(projectId.length + key.length);
        buffer.put(projectId);
        buffer.put(key);
        return buffer.array();
    }
}
