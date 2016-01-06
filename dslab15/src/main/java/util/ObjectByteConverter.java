package util;

import java.io.*;

/**
 * Created by Markus on 05.01.2016.
 */
public class ObjectByteConverter {
    public static byte[] serObject(Object o){
        ByteArrayOutputStream arrout = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        try {
            ObjectOutputStream objout = new ObjectOutputStream(arrout);
            objout.writeObject(o);

            b = arrout.toByteArray();
            arrout.close();
            objout.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return b;
    }

    public static Object deserObject(byte[] b){
        ByteArrayInputStream arr = new ByteArrayInputStream(b);
        ObjectInputStream ser;
        Object o = null;
        try {
            ser = new ObjectInputStream(arr);
            o = ser.readObject();
            arr.close();
            ser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return o;
    }

}
