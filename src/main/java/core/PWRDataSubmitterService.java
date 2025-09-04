package core;

import com.github.pwrlabs.pwrj.record.response.Response;
import io.pwrlabs.util.encoders.BiResult;
import main.Main;
import main.Settings;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PWRDataSubmitterService {
    private static LinkedBlockingQueue<List<Object>> queue = new LinkedBlockingQueue<>();
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    public static void submitData(byte[] projectId, byte[] key, byte[] data) {
        if(projectId == null || projectId.length == 0) {
            throw new IllegalArgumentException("Project ID cannot be null or empty");
        }
        if(key == null || key.length == 0) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if(data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        if(key.length + data.length > 1_000_000) {
            throw new IllegalArgumentException("Data too large");
        }

        queue.add(List.of(projectId, key, data));
    }

    static {
        //add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            isShuttingDown.set(true);
        }));

        Settings.nonDaemonExecutor.execute(() -> {
            List<List<Object>> batch = new ArrayList<>();
            while(true) {
                try {
                    List<Object> item = queue.poll();
                    if(item != null) {
                        batch.add(item);
                    }

                    if(item == null && batch.isEmpty() && isShuttingDown.get()) {
                        // If shutting down and no more items to process, exit the loop
                        return;
                    }

                    if(item == null) {
                        if(!batch.isEmpty()) {
                            submitDataToPwrChain(batch);
                            batch.clear();
                        }
                        // Sleep for a short time to avoid spamming
                        Thread.sleep(100);
                    }

                } catch (Exception e) {
                    //If an error is thrown, the batch is not cleared so it can be retried
                    e.printStackTrace();
                    try { Thread.sleep(100); } catch (Exception e1) {} // Sleep a bit before retrying so we don't spam
                }
            }
        });
    }

    private static void submitDataToPwrChain(List<List<Object>> batch) throws IOException {
        if(batch == null || batch.isEmpty()) return;

        List<byte[]> toSubmitList = new ArrayList<>();
        for (List<Object> item : batch) {
            if(item.size() != 3) continue;
            byte[] projectId = (byte[]) item.get(0);
            byte[] key = (byte[]) item.get(1);
            byte[] data = (byte[]) item.get(2);

            ByteBuffer buffer = ByteBuffer.allocate(4 + projectId.length + 4 + key.length + 4 + data.length);
            buffer.putInt(projectId.length);
            buffer.put(projectId);
            buffer.putInt(key.length);
            buffer.put(key);
            buffer.putInt(data.length);
            buffer.put(data);

            byte[] toSubmit = buffer.array();
            toSubmitList.add(toSubmit);
        }

        long maxTxnSize = Main.pwrj.getMaxTransactionSize();
        long maxTxnBodySize = maxTxnSize - 1500; // Leave some room for overhead and signature

        List<byte[]> currentBatch = new ArrayList<>();
        long currentBatchSize = 0;
        for (byte[] toSubmit : toSubmitList) {
            if (currentBatchSize + 4 + toSubmit.length > maxTxnBodySize) {
                // Submit current batch
                submitBatch(currentBatch);
                // Start new batch
                currentBatch.clear();
                currentBatchSize = 0;
            }
            currentBatch.add(toSubmit);
            currentBatchSize += 4 + toSubmit.length;
        }

        // Submit any remaining data
        if (!currentBatch.isEmpty()) {
            submitBatch(currentBatch);
        }
    }

    private static void submitBatch(List<byte[]> batchData) throws IOException {
        if(batchData == null || batchData.isEmpty()) return;

        int totalBodySize = 0;
        for (byte[] data : batchData) {
            totalBodySize += 4 + data.length; // 4 bytes for length prefix
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalBodySize);
        for (byte[] data : batchData) {
            buffer.putInt(data.length);
            buffer.put(data);
        }

        Response r = Main.wallet.submitPayableVidaData(Main.vidaId, buffer.array(), 0, Main.pwrj.getFeePerByte());
        if(!r.isSuccess()) throw new IOException("Failed to submit data: " + r.getError());
    }
}
