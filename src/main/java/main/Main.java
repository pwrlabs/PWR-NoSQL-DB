package main;

import api.GET;
import api.POST;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.wallet.PWRFalconWallet;
import core.Synchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    public static final long vidaId = 7634785;
    public static final long startingBlockNumber = 920227;
    public static final PWRJ pwrj = new PWRJ(Config.getRpcUrl());
    public static PWRFalconWallet wallet;

    public static void main(String[] args) {
        try {
            wallet = PWRFalconWallet.loadWallet(pwrj, "encryptedSeedPhrase.txt", Config.getWalletPassword());
            System.out.println("Wallet address: " + wallet.getAddress());
            Synchronizer.sync(pwrj, vidaId, startingBlockNumber);

            GET.run();
            POST.run();
        } catch (Exception e) {
            logger.error("Main:main:Failed to start synchronizer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
