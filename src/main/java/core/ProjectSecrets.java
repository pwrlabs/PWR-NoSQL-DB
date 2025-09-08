package core;

import io.pwrlabs.hashing.PWRHash;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProjectSecrets {
    private static final Logger logger = LoggerFactory.getLogger(ProjectSecrets.class);
    private static Map<String /*Project ID*/, byte[] /*Secret Hash*/> secrets = new ConcurrentHashMap<>();

    public static boolean isValidProjectSecret(String projectId, String secret) {
        byte[] secretHash = getSecretHash(secret);
        if(secrets.containsKey(projectId)) {
            return Arrays.equals(secrets.get(projectId), secretHash);
        } else {
            boolean fetchedSecret = isProjectSecret(projectId, secret);
            if(fetchedSecret) {
                secrets.put(projectId, secretHash);
            }
            return fetchedSecret;
        }
    }

    private static boolean isProjectSecret(String projectId, String secret) {
        try {
            // Encode the project ID for URL safety
            String encodedProjectId = URLEncoder.encode(projectId, StandardCharsets.UTF_8);
            String encodedSecret = URLEncoder.encode(secret, StandardCharsets.UTF_8);

            // Build the URL with query parameter
            String url = "https://vwffmwljalplkfiurosc.supabase.co/functions/v1/validate-project-secret?project_id=" + encodedProjectId + "&project_secret=" + encodedSecret;

            // Create HTTP client and request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            // Send the request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Check if request was successful
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                return json.optBoolean("valid", false);
            } else if (response.statusCode() == 404) {
                logger.warn("Project not found: " + projectId);
                return false;
            }
            else {
                throw new RuntimeException("HTTP Error: " + response.statusCode() + " - " + response.body());
            }

        } catch (Exception e) {
            // Log the error and rethrow or return null based on your error handling strategy
            System.err.println("Error fetching project secret: " + e.getMessage());
            throw new RuntimeException("Failed to get project secret for project: " + projectId, e);
        }
    }

    private static byte[] getSecretHash(String secret) {
        if(secret == null || secret.isEmpty()) throw new IllegalArgumentException("Secret cannot be null or empty");
        byte[] hash = PWRHash.hash256(secret.getBytes(StandardCharsets.UTF_8));
        return hash;
    }

    public static void main(String[] args) {
        String projectId = "npu6o3uooiijkmnawvjced";
        String projectSecret = "pwr_h3MmbZSKSPuf9L523E0Y6g==";

        boolean isValid = isProjectSecret(projectId, projectSecret);
        System.out.println("Is valid: " + isValid);

        isValid = isProjectSecret(projectId, projectSecret);
        System.out.println("Is valid: " + isValid);
    }
}
