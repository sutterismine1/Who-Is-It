import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

public class ServerThread extends Thread{
    private Socket p1;
    private Socket p2;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private Cipher decryptCipher;
    private Cipher encryptCipher;
    private IvParameterSpec ivParameterSpec;
    private String algorithm;
    private SecretKey symKey1;
    private SecretKey symKey2;
    public ServerThread(Socket p1, Socket p2) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        this.p1 = p1;
        this.p2 = p2;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = pair.getPrivate();
        publicKey = pair.getPublic();
        decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
        encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        algorithm = "AES/ECB/PKCS5Padding";
    }
    public static String encrypt(String algorithm, String input, SecretKey key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {

        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cipherText = cipher.doFinal(input.getBytes());
        return Base64.getEncoder()
                .encodeToString(cipherText);
    }
    public static String decrypt(String algorithm, String cipherText, SecretKey key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {

        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] plainText = cipher.doFinal(Base64.getDecoder()
                .decode(cipherText));
        return new String(plainText);
    }
    public void run() {
        System.out.println("Both clients connected. Starting game.");
        try {

            InputStream input1 = p1.getInputStream();
            InputStream input2 = p2.getInputStream();
            BufferedReader reader1 = new BufferedReader(new InputStreamReader(input1));
            BufferedReader reader2 = new BufferedReader(new InputStreamReader(input2));
            OutputStream output1 = p1.getOutputStream();
            OutputStream output2 = p2.getOutputStream();
            PrintWriter writer1 = new PrintWriter(output1, true);
            PrintWriter writer2 = new PrintWriter(output2, true);

            //receive client address and port from turn server
            String target1 = reader1.readLine();
            System.out.println(target1);
            String[] targetArr1 = target1.split("\\|"); //expecting "{client address}|{client port}|{turnserver port}"
            String target2 = reader2.readLine(); //expecting "{client address}|{client port}|{turnserver port}"
            System.out.println(target2);
            String[] targetArr2 = target2.split("\\|");
            String test = reader1.readLine();
            String test1 = reader2.readLine();
            System.out.println(test);
            System.out.println(test1);

            //send public key to clients to help them create a symmetric key
            String publicKeyString = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            writer1.println(publicKeyString);
            writer2.println(publicKeyString);
            //get encrypted symmetric key from each player
            String symKeyContent1 = reader1.readLine();
            String symKeyContent2 = reader2.readLine();
            byte[] encryptedAesKey1Bytes = Base64.getDecoder().decode(symKeyContent1);
            byte[] encryptedAesKey2Bytes = Base64.getDecoder().decode(symKeyContent2);
            //decrypt each symmetric key with the RSA private key and store them\
            byte[] decodedSK1  = decryptCipher.doFinal(encryptedAesKey1Bytes);
            byte[] decodedSK2 = decryptCipher.doFinal(encryptedAesKey2Bytes);
            symKey1 = new SecretKeySpec(decodedSK1, 0, decodedSK1.length, "AES");
            symKey2 = new SecretKeySpec(decodedSK2, 0, decodedSK2.length, "AES");
            System.out.println("SYM KEY 1: " + symKey1);
            System.out.println("SYM KEY 2: " + symKey2);
            // lets clients know who is starting (no need to encrypt this part)
            writer1.println("A");
            writer2.println("B");
            writer1.println(encrypt(algorithm, "Question", symKey1));
            try {
                SocketListener sl1 = new SocketListener(p1.getInputStream());
                sl1.addChangeListener(e -> {
                    String encResponse = (String) e.getSource();
                    String response;
                    try {
                        response = decrypt(algorithm, encResponse, symKey1);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    System.out.println("Received from A: " + response);
                    if(response.contains("Yes") || response.contains("No")){
                        try {
                            writer1.println(encrypt(algorithm, "Question", symKey1));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    } else if(response.isEmpty()){
                        Thread.currentThread().interrupt();
                    }
                    try {
                        writer2.println(encrypt(algorithm, response, symKey2));
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }

                });
                SocketListener sl2 = new SocketListener(p2.getInputStream());
                sl2.addChangeListener(e -> {
                    String encResponse = (String) e.getSource();
                    String response;
                    try {
                        response = decrypt(algorithm, encResponse, symKey2);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                    System.out.println("Received from B: " + response);
                    if(response.contains("Yes") || response.contains("No")){
                        try {
                            writer2.println(encrypt(algorithm, "Question", symKey2));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }else if(response.isEmpty()){
                        Thread.currentThread().interrupt();
                    }
                    try {
                        writer1.println(encrypt(algorithm, response, symKey1));
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
                Thread t1 = new Thread(sl1);
                t1.start();
                Thread t2 = new Thread(sl2);
                t2.start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }/*
            while (true) {
                // wait for question from A
                System.out.println("User A Question:");
                try {
                    String question = reader1.readLine();
                    if (question == null || question.trim().equals("")) { //if empty input, socket has been closed
                    } else {
                        System.out.println(question);
                        writer2.println(question);
                        try {
                            String response = reader2.readLine();
                            System.out.println("B says " + response);
                            writer1.println(response);
                            writer2.println("Question");
                        } catch (SocketTimeoutException toEx) {
                            System.out.println("User B Timedout on Y/N");
                            writer2.println("Timeout");
                            writer1.println("Question");
                            continue;   //send question prompt to A, restart loop to wait for A again
                        } catch (Exception ex) {
                            System.out.println("Server exception: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                } catch (SocketTimeoutException toEx) {
                    System.out.println("User A Timedout");
                    writer1.println("Timeout");
                    writer2.println("Question");
                } catch (Exception ex) {
                    System.out.println("Server exception: " + ex.getMessage());
                    ex.printStackTrace();
                }
                //wait for question from B
                System.out.println("User B Question:");
                while(true) {   //Loop will exit after the first iteration under most cases, unless User A refused to say Y/N in which case B will be prompted again
                    try {
                        String question = reader2.readLine();
                        if (question == null || question.trim().equals("")) { //if empty input, socket has been closed
                        } else {
                            System.out.println(question);
                            writer1.println(question);
                            try {
                                String response = reader1.readLine();
                                writer2.println(response);
                                writer1.println("Question");
                                break;
                            } catch (SocketTimeoutException toEx) {
                                System.out.println("User A Timedout on Y/N");
                                writer1.println("Timeout");
                                writer2.println("Question");
                                continue;   //send question prompt to B, restart loop to wait for A again
                            } catch (Exception ex) {
                                System.out.println("Server exception: " + ex.getMessage());
                                ex.printStackTrace();
                            }
                        }
                    } catch (SocketTimeoutException toEx) {
                        System.out.println("User B Timedout");
                        writer2.println("Timeout");
                        writer1.println("Question");
                    } catch (Exception ex) {
                        System.out.println("Server exception: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            }
        }catch (Exception ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally{
            System.out.println("Connection Thread Ended.");
        }*/
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }
}
