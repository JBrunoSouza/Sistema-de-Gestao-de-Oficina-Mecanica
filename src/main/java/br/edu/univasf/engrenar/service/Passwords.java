package br.edu.univasf.engrenar.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Legacy hashes are accepted only for verification and upgraded after successful login. */
public final class Passwords {
    private static final int ITERATIONS=600_000;
    private static final SecureRandom RANDOM=new SecureRandom();
    private Passwords(){}
    public static void validate(String value){
        if(value==null||value.length()<12||value.length()>128||value.isBlank())
            throw new ValidationException("password","Use uma senha entre 12 e 128 caracteres.");
    }
    public static String hash(String value){
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);
        return "pbkdf2$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(value,salt,ITERATIONS));
    }
    public static boolean verify(String value,String stored){
        if(value==null||value.length()>128||stored==null)return false;
        try{
            if(stored.matches("[a-fA-F0-9]{64}"))return MessageDigest.isEqual(HexFormat.of().parseHex(stored),HexFormat.of().parseHex(digest(value)));
            String[] pieces=stored.split("\\$");if(pieces.length!=4||!pieces[0].equals("pbkdf2"))return false;
            int count=Integer.parseInt(pieces[1]);if(count<600_000||count>2_000_000)return false;
            byte[] salt=Base64.getDecoder().decode(pieces[2]),expected=Base64.getDecoder().decode(pieces[3]);
            return salt.length==16&&expected.length==32&&MessageDigest.isEqual(expected,derive(value,salt,count));
        }catch(IllegalArgumentException e){return false;}
    }
    private static byte[] derive(String value,byte[] salt,int iterations){
        PBEKeySpec spec=new PBEKeySpec(value.toCharArray(),salt,iterations,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e){throw new IllegalStateException("Não foi possível proteger a senha.",e);}
        finally{spec.clearPassword();}
    }
    public static String token(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static String digest(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
}
