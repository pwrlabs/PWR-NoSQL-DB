package main;

import lombok.Getter;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

@Getter
public class Config {

    @Getter
    private static String rpcUrl, walletPassword;
    @Getter
    private static long vidaStartingBlock;

    static {
        File configFile = new File("config.json");

        try {
            JSONObject config = new JSONObject();
            if(configFile.exists()) config = new JSONObject(Files.readString(configFile.toPath()));

            rpcUrl = config.optString("rpcUrl", "https://pwrrpc.pwrlabs.io");
            walletPassword = config.optString("walletPassword", "walletPassword");
            vidaStartingBlock = config.optLong("vidaStartingBlock", 946921);
        } catch (Exception e) {
            System.err.println("Config:static:Failed to load config file");
            e.printStackTrace();
            //System.out.println("Error: " + e.getMessage());
            System.exit(0);
        }
    }
}