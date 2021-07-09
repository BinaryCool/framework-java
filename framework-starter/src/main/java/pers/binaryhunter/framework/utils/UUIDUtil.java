package pers.binaryhunter.framework.utils;

import java.util.UUID;

public class UUIDUtil {
	public static String[] chars = new String[] { "a", "b", "c", "d", "e", "f",
        "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s",
        "t", "u", "v", "w", "x", "y", "z", "0", "1", "2", "3", "4", "5",
        "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "G", "H", "I",
        "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",
        "W", "X", "Y", "Z" };

	public static String get32UUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

	public static String get16UUID(){
		StringBuffer shortBuffer = new StringBuffer();
		String uuid = get32UUID();
	    for (int i = 0; i < 16; i++) {
	        String str = uuid.substring(i * 2, i * 2 + 2);
	        int x = Integer.parseInt(str, 16);
	        shortBuffer.append(chars[x % 0x3E]);
	    }
	    return shortBuffer.toString();
	}

	public static String get8UUID(){
		StringBuffer shortBuffer = new StringBuffer();
        String uuid = get32UUID();
	    for (int i = 0; i < 8; i++) {
	        String str = uuid.substring(i * 4, i * 4 + 4);
	        int x = Integer.parseInt(str, 16);
	        shortBuffer.append(chars[x % 0x3E]);
	    }
	    return shortBuffer.toString();
	}
}
