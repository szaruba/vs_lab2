package hash;

import org.bouncycastle.util.encoders.Base64;

import javax.crypto.Mac;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Philipp on 08.12.2015.
 */
public class HashService {

    public static String hashMessage(Key key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(message.getBytes());
            byte[] hash = mac.doFinal();
            String hashBass64 = new String(Base64.encode(hash));
            return hashBass64;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isHashedCorrectly(Key key, String hashedMessage, String message) {
        byte[] computedHash = decodeB64(hashMessage(key, message));
        byte[] receivedHash = decodeB64(hashedMessage);
        return MessageDigest.isEqual(computedHash, receivedHash);
    }

    public static byte[] decodeB64(String hashedMessage) {
        return Base64.decode(hashedMessage.getBytes());
    }
}
