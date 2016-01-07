package channel;

import org.bouncycastle.util.encoders.Base64;
import util.ObjectByteConverter;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Created by Markus on 05.01.2016.
 */
public class AESChannel extends ChannelDecorator {

    private Cipher cipherToencrypt;
    private Cipher cipherTodecrypt;
    private boolean active = false;

    public AESChannel(Channel c, byte[] key, byte[] iv) {
        super(c);
        byte[] decodedKey = Base64.decode(key);
        byte[] decodedIV = Base64.decode(iv);

        try {
            cipherToencrypt = Cipher.getInstance("AES/CTR/NoPadding");
            SecretKey originalKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

            IvParameterSpec ivParameterSpec = new IvParameterSpec(decodedIV);

            cipherToencrypt.init(Cipher.ENCRYPT_MODE, originalKey, ivParameterSpec);

            cipherTodecrypt = Cipher.getInstance("AES/CTR/NoPadding");
            cipherTodecrypt.init(Cipher.DECRYPT_MODE, originalKey, ivParameterSpec);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void write(Object o) throws ChannelException, IOException {

        //byte[] msg = ObjectByteConverter.serObject(o);
        try {
            if(active == true){
                byte[] msg = ObjectByteConverter.serObject(o);
                channel.write(cipherToencrypt.doFinal(msg));
            }else {
                channel.write(cipherToencrypt.doFinal((byte[]) o));
            }
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }

    }

    @Override
    public Object read() throws IOException, ChannelException {
        try {
            //System.out.println(active);
            if(active == true){
                //System.out.println("Normalbetrieb");
                byte[] msg = cipherTodecrypt.doFinal((byte[]) super.read());
                Object o = ObjectByteConverter.deserObject(msg);
                return o;
            }else{
                byte[] enMsg = (byte[]) super.read();
                return cipherTodecrypt.doFinal(enMsg);
            }
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setActive(boolean active){
        this.active = active;
    }
}
