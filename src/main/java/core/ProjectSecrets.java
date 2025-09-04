package core;

import api.POST;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pwrlabs.pwrj.record.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProjectSecrets {
    private static final Logger logger = LoggerFactory.getLogger(ProjectSecrets.class);
    private static Map<String /*Project ID*/, String /*Secret*/> secrets = new ConcurrentHashMap<>();

    public static boolean isValidProjectSecret(String projectId, String secret) {
        if(secrets.containsKey(projectId)) {
            return secrets.get(projectId).equals(secret);
        } else {
            String fetchedSecret = getProjectSecret(projectId);
            if(fetchedSecret != null) {
                secrets.put(projectId, fetchedSecret);
                return fetchedSecret.equals(secret);
            } else {
                return false;
            }
        }
    }

    private static String getProjectSecret(String projectId) {
        try {
            // Encode the project ID for URL safety
            String encodedProjectId = URLEncoder.encode(projectId, StandardCharsets.UTF_8);

            // Build the URL with query parameter
            String url = "https://vwffmwljalplkfiurosc.supabase.co/functions/v1/get-project-secret?project_id=" + encodedProjectId;

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
                // Parse JSON response
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonNode = mapper.readTree(response.body());

                // Extract and return project_secret
                if (jsonNode.has("project_secret")) {
                    return jsonNode.get("project_secret").asText();
                } else {
                    throw new RuntimeException("project_secret not found in response");
                }
            } else if (response.statusCode() == 404) {
                logger.warn("Project not found: " + projectId);
                return null;
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

    public static void main(String[] args) {
        String projectId = "example_project_id"; // Replace with actual project ID
        String secret = getProjectSecret(projectId);
        System.out.println("Project ID: " + projectId);
        System.out.println("Project Secret: " + secret);

        projectId = "example_project_id"; // Replace with actual project ID
        secret = getProjectSecret(projectId);
        System.out.println("Project ID: " + projectId);
        System.out.println("Project Secret: " + secret);
    }
}
