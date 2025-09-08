package core;

import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.protocol.VidaTransactionSubscription;
import io.pwrlabs.util.encoders.ByteArrayWrapper;
import lombok.Getter;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.crypto.Data;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class Synchronizer {
    private static final Logger logger = LoggerFactory.getLogger(Synchronizer.class);
    @Getter
    private static VidaTransactionSubscription subscription;
    private static final Map<ByteArrayWrapper, Future<?>> futures = new ConcurrentHashMap<>();

    public static void sync(PWRJ pwrj, long vidaId, long startingBlock) throws IOException {
        long lastCheckedBlock = getLastCheckedBlock();
        if(lastCheckedBlock != -1 && lastCheckedBlock >= startingBlock) {
            startingBlock = lastCheckedBlock + 1;
            logger.info("Resuming from last checked block: " + lastCheckedBlock);
        } else {
            logger.info("Starting from configured starting block: " + startingBlock);
        }
        subscription = pwrj.subscribeToVidaTransactions(pwrj, vidaId, startingBlock, Synchronizer::saveLastCheckedBlock, transaction -> {
            String sender = transaction.getSender();
            if(sender.startsWith("0x")) sender = sender.substring(2);

            if(sender.equalsIgnoreCase("5358B93679273DC89CD941911CD1524ECC0217B9")) {
                byte[] data = transaction.getData();
                List<byte[]> dataDecoded = new ArrayList<>();
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while(buffer.remaining() > 0) {
                    int len = buffer.getInt();
                    byte[] chunk = new byte[len];
                    buffer.get(chunk);
                    dataDecoded.add(chunk);
                }

                for (byte[] chunk : dataDecoded) {
                    ByteBuffer chunkBuffer = ByteBuffer.wrap(chunk);
                    int projectIdLen = chunkBuffer.getInt();
                    byte[] projectIdBytes = new byte[projectIdLen];
                    chunkBuffer.get(projectIdBytes);
                    int keyLen = chunkBuffer.getInt();
                    byte[] key = new byte[keyLen];
                    chunkBuffer.get(key);
                    int valueLen = chunkBuffer.getInt();
                    byte[] value = new byte[valueLen];
                    chunkBuffer.get(value);

                    while (true) {
                        try {
                            Database.put(projectIdBytes, key, value);
                            completeFuture(projectIdBytes, key, value);
                            logger.info("Synchronized data from transaction: Project ID: " + new String(projectIdBytes) + ", Key: " + new String(key) + ", Value: " + new String(value));
                        } catch (Exception e) {
                            logger.error("Failed to put data into database: " + e.getMessage());
                            e.printStackTrace();
                            Thread.yield();
                        }
                        break;
                    }
                }
            }
        });
    }

    private static Void saveLastCheckedBlock(long lastCheckedBlock) {
        try {
            Database.put("synchronizer".getBytes(), "lastCheckedBlock".getBytes(), ByteBuffer.allocate(8).putLong(lastCheckedBlock).array());
            return null;
        } catch (Exception e) {
            logger.error("Failed to save last checked block: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static long getLastCheckedBlock() {
        try {
            byte[] data = Database.get("synchronizer".getBytes(), "lastCheckedBlock".getBytes());
            if(data == null || data.length != 8) return -1;
            ByteBuffer buffer = ByteBuffer.wrap(data);
            return buffer.getLong();
        } catch (Exception e) {
            logger.error("Failed to get last checked block: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    //A function thread will use to get a future that tells them when the new key and data they submitted has been added
    public static Future<?> waitForNewValueToBeAdded(byte[] projectId, byte[] key, byte[] value) {
        ByteBuffer buffer = ByteBuffer.allocate(projectId.length + key.length + value.length);
        buffer.put(projectId);
        buffer.put(key);
        buffer.put(value);

        ByteArrayWrapper futureKey = new ByteArrayWrapper(buffer.array());
        Future<?> future = new CompletableFuture<>();
        futures.putIfAbsent(futureKey, future);

        // If someone submitted the same request twice at the same time, return the existing future
        return futures.get(futureKey);
    }

    private static void completeFuture(byte[] projectId, byte[] key, byte[] value) {
        ByteBuffer buffer = ByteBuffer.allocate(projectId.length + key.length + value.length);
        buffer.put(projectId);
        buffer.put(key);
        buffer.put(value);

        ByteArrayWrapper futureKey = new ByteArrayWrapper(buffer.array());
        Future<?> future = futures.remove(futureKey);
        if(future != null && future instanceof CompletableFuture) {
            ((CompletableFuture<?>) future).complete(null);
        }
    }
}
