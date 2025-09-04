package api;

import com.github.pwrlabs.pwrj.Utils.Falcon;
import com.github.pwrlabs.pwrj.entities.WithdrawalOrder;
import com.github.pwrlabs.pwrj.record.response.Response;
import core.Database;
import io.pwrlabs.database.rocksdb.MerkleTree;
import io.pwrlabs.util.encoders.ByteArrayWrapper;
import io.pwrlabs.util.encoders.Hex;
import io.pwrlabs.utils.BinaryJSONKeyMapper;
import io.pwrlabs.utils.BinaryJSONObject;
import io.pwrlabs.utils.PWRReentrantReadWriteLock;
import io.pwrlabs.utils.PWRReentrantReadWriteLockManager;
import main.Main;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Spark;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;

import static io.pwrlabs.newerror.NewError.errorIf;
import static spark.Spark.get;

public class GET {
    public static void run() {

        get("/getValue", (request, response) -> {
            response.header("Content-Type", "application/json");
            try {
                String projectId = request.queryParams("projectId");
                String keyHex = request.queryParams("key");

                if (projectId == null || keyHex == null) {
                    return getError(response, "Missing required fields");
                }

                if(keyHex.startsWith("0x")) keyHex = keyHex.substring(2);
                byte[] projectIdBytes = projectId.getBytes();
                byte[] key = Hex.decode(keyHex);

                byte[] value = Database.get(projectIdBytes, key);
                if(value == null) {
                    return getError(response, "Key not found");
                } else {
                    return getSuccess("value", Hex.toHexString(value));
                }
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        });

    }

    public static JSONObject getError(spark.Response response, String message) {
        response.status(400);
        JSONObject object = new JSONObject();
        object.put("message", message);
        return object;
    }

    public static JSONObject getSuccess(Object... variables) throws Exception {
        JSONObject object = new JSONObject();
        int size = variables.length;
        if (size % 2 != 0) {
            throw new Exception("Provided variables length should be even when using getSuccess");
        } else {
            for(int t = 0; t < size; t += 2) {
                object.put(variables[t].toString(), variables[t + 1]);
            }

            return object;
        }
    }

}
