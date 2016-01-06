package util;

import java.io.File;
import java.io.IOException;
import java.security.Key;

/**
 * Created by Markus on 05.01.2016.
 */
public class KeyLoader {

    public static Key loadServerkey(String server_key) throws IOException {
        String path = KeyLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        String server_path = path.replace("build/", server_key);
        File pub = new File(server_path);
        return Keys.readPublicPEM(pub);
    }

    public static Key loadClientkey(String keys_dir, String username) throws IOException {
        String path = KeyLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        String server_path = path.replace("build/", keys_dir + File.separator + username + ".pem");
        File pub = new File(server_path);
        return Keys.readPrivatePEM(pub);
    }
}
