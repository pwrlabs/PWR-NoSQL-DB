package api;

import com.github.pwrlabs.pwrj.entities.FalconTransaction;
import core.PWRDataSubmitterService;
import core.ProjectSecrets;
import core.Synchronizer;
import io.pwrlabs.newerror.ValidationException;
import io.pwrlabs.util.encoders.ByteArrayWrapper;
import io.pwrlabs.utils.BinaryJSONObject;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static spark.Spark.post;

public class POST {
    private static final Logger logger = LoggerFactory.getLogger(POST.class);

    public static void run() {
        post("/storeData", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                JSONObject req = new JSONObject(request.body());

                String projectId = req.optString("projectId", null);
                String secret = req.optString("secret", null);
                String keyHex = req.optString("key", null);
                String valueHex = req.optString("value", null);

                if (projectId == null || secret == null || keyHex == null || valueHex == null) {
                    return getError(response, "Missing required fields");
                }

                if(!ProjectSecrets.isValidProjectSecret(projectId, secret)) {
                    return getError(response, "Invalid project ID or secret");
                }

                if(keyHex.startsWith("0x")) keyHex = keyHex.substring(2);
                if(valueHex.startsWith("0x")) valueHex = valueHex.substring(2);

                byte[] projectIdBytes = projectId.getBytes();
                byte[] key = Hex.decode(keyHex);
                byte[] value = Hex.decode(valueHex);

                PWRDataSubmitterService.submitData(projectIdBytes, key, value);
                Future<?> future = Synchronizer.waitForNewValueToBeAdded(projectIdBytes, key, value);
                logger.info("Request received amd data sent to blockchain. Waiting for data to be stored in the database...");
                future.get(10000, TimeUnit.MILLISECONDS); // wait up to 10 seconds

                return getSuccess("message", "Data stored successfully");
            } catch (Exception var5) {
                var5.printStackTrace();
                return getError(response, var5.getLocalizedMessage());
            }
        });
    }


    // There was a problem on the server side
    public static JSONObject getError(spark.Response response, String message) {
        if (message == null) message = "Unknown error";
        response.status(400);
        JSONObject object = new JSONObject();
        object.put("message", message);

        return object;
    }

    public static JSONObject getSuccess(Object... variables) throws Exception {
        JSONObject object = new JSONObject();

        int size = variables.length;
        if (size % 2 != 0)
            throw new Exception("Provided variables length should be even when using getSuccess");

        for (int t = 0; t < size; t += 2) {
            object.put(variables[t].toString(), variables[t + 1]);
        }

        return object;
    }
}
