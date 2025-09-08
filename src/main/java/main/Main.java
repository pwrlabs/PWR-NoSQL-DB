package main;

import api.GET;
import api.POST;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.wallet.PWRFalconWallet;
import core.Synchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static spark.Spark.*;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static final long vidaId = 7634785;
    public static final PWRJ pwrj = new PWRJ(Config.getRpcUrl());
    public static PWRFalconWallet wallet;

    public static void main(String[] args) {
        try {
            wallet = PWRFalconWallet.loadWallet(pwrj, "encryptedSeedPhrase.txt", Config.getWalletPassword());
            System.out.println("Wallet address: " + wallet.getAddress());
            Synchronizer.sync(pwrj, vidaId, Config.getVidaStartingBlock());

            port(8080);

            options("/*",
                    (request, response) -> {

                        String accessControlRequestHeaders = request
                                .headers("Access-Control-Request-Headers");
                        if (accessControlRequestHeaders != null) {
                            response.header("Access-Control-Allow-Headers",
                                    accessControlRequestHeaders);
                        }

                        String accessControlRequestMethod = request
                                .headers("Access-Control-Request-Method");
                        if (accessControlRequestMethod != null) {
                            response.header("Access-Control-Allow-Methods",
                                    accessControlRequestMethod);
                        }

                        return "OK";
                    });
            before((request, response) -> response.header("Access-Control-Allow-Origin", "*"));

            GET.run();
            POST.run();
        } catch (Exception e) {
            logger.error("Main:main:Failed to start synchronizer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
