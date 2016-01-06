package util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;

/**
 * Please note that this class is not needed for Lab 1, but can later be
 * used in Lab 2.
 * 
 * Provides security provider related utility methods.
 */
public final class SecurityUtils {

	private SecurityUtils() {
	}

	/**
	 * Registers the {@link BouncyCastleProvider} as the primary security
	 * provider if necessary.
	 */
	public static synchronized void registerBouncyCastle() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.insertProviderAt(new BouncyCastleProvider(), 0);
		}
	}

	public static synchronized byte[] generateEncryptedSecureRandom(){
		SecureRandom secureRandom = new SecureRandom();
		final byte[] number = new byte[32];
		secureRandom.nextBytes(number);
		return Base64.encode(number);
	}

	public static synchronized byte[] createSessionKey() throws NoSuchAlgorithmException {
		KeyGenerator generator = KeyGenerator.getInstance("AES");
		generator.init(256);
		SecretKey key = generator.generateKey();
		return key.getEncoded();
	}

	public static synchronized byte[] createInitVector() {
		byte[] number = new byte[16];
		IvParameterSpec ivSpec = new IvParameterSpec(number);
		return ivSpec.getIV();
	}
}
