package core;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Database {
    private static final RocksDB db = null;

    static {

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
