package main;

import lombok.Getter;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

@Getter
public class Config {

    @Getter
    private static String rpcUrl;

    static {
        File configFile = new File("config.json");

        try {
            JSONObject config = new JSONObject();
            if(configFile.exists()) config = new JSONObject(Files.readString(configFile.toPath()));

            rpcUrl = config.getString("https://pwrrpc.pwrlabs.io");
        } catch (Exception e) {
            System.err.println("Config:static:Failed to load config file");
            e.printStackTrace();
            //System.out.println("Error: " + e.getMessage());
            System.exit(0);
        }
    }
}