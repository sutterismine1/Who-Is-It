import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.net.ssl.*;

public class Main {
    static boolean toggle = false;
    static boolean turn = false; //it is neither clients turn until the server assigns A and B
    static final int TURNPORT = 49152;
    static final int PORT = 26000;
    static JDialog currentMsg;
    static Socket turnServer;
    static InputStream input;
    static BufferedReader reader;
    static OutputStream output;
    static PrintWriter writer;
    static SecretKey symKey;

    //JFrame init
    static JFrame unconnected=new JFrame();
    static JFrame game=new JFrame();
    static JPanel peoplePanel = new JPanel();

    public static void main(String[] args) throws IOException {
        //show names button
        JButton showNames = new JButton("Show Names");
        showNames.setBounds(0,825,200,100);
        showNames.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                peoplePanel.setVisible(toggle);
                toggle = !toggle;
            }
        });
        game.add(showNames);

        //text input for guess
        JTextField guessInput = new JTextField("Enter Question/Guess Here", 12);
        guessInput.setBounds(200, 825, 400,100);
        guessInput.setForeground(Color.GRAY);
        guessInput.addFocusListener(new FocusListener() {   //turns initial text into a placeholder text that disappears on focus and reappears when focus is lost, taken from https://stackoverflow.com/questions/16213836/java-swing-jtextfield-set-placeholder
            @Override
            public void focusGained(FocusEvent e) {
                if (guessInput.getText().equals("Enter Question/Guess Here")) {
                    guessInput.setText("");
                    guessInput.setForeground(Color.BLACK);
                }
            }
            @Override
            public void focusLost(FocusEvent e) {
                if (guessInput.getText().isEmpty()) {
                    guessInput.setForeground(Color.GRAY);
                    guessInput.setText("Enter Question/Guess Here");
                }
            }
        });
        game.add(guessInput);

        //send button
        JButton send = new JButton("Send");
        send.setBounds(600,825,200,100);
        send.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                if (turn) {
                    int result = JOptionPane.showConfirmDialog(game, "Are you sure you want to send:\n" + guessInput.getText());
                    if(result==0) {//yes
                        try {
                            writer.println(encrypt(symKey.getAlgorithm(), guessInput.getText(), symKey));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                        turn=!turn;
                    }
                }
                else {
                    JOptionPane.showMessageDialog(game, "It is not your turn yet!");
                }
            }
        });
        game.add(send);


        //load images
        ToggleCoverImage[] people;
        peoplePanel.setLayout(null);
        people = loadImages();
        for (ToggleCoverImage person:
                people) {
            peoplePanel.add(person);
        }
        peoplePanel.setBounds(0, 0, 800, 800);
        game.add(peoplePanel);

        //load name labels
        JPanel nameLabels = new JPanel();
        nameLabels.setLayout(null);
        String[] names={"Adele", "Aubrey", "Ed", "Edward", "Albert", "Fred", "Fazbear", "Grace", "Madeline", "Oprah", "Scott", "Beatrix", "Tom", "Urkel", "Velma", "Leia"};
        for (int i = 0; i < 16; i++) {
            JLabel nameLabel = new JLabel(names[i]);
            nameLabel.setFont(new Font("Arial", Font.PLAIN, 18));
            int y = 3;
            if (i<4){
                y=0;
            } else if (i<8) {
                y=1;
            } else if (i<12) {
                y=2;
            }
            nameLabel.setBounds((i%4)*200+75,y*200, 200, 200);
            nameLabels.add(nameLabel);
        }
        nameLabels.setBounds(0, 0, 800, 800);
        game.add(nameLabels);

        //make sure socket is closed when game is closed
        WindowListener closeListener = new WindowAdapter(){
            @Override
            public void windowClosing(WindowEvent e){
                closeSocket();
                ((JFrame) e.getSource()).dispose();
                System.exit(0);
            }
        };

        //connect button
        JButton connect = new JButton("Search for a game");
        connect.setBounds(300,450,200,100);
        connect.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                try {
                    connect();
                    startGame();
                } catch (IOException ex) {
                    System.out.print(ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
        unconnected.add(connect);

        //JFrame config and display
        game.setSize(800,1000);
        game.setLayout(null);
        game.setFocusable(true);
        game.setVisible(false);
        game.addWindowListener(closeListener); //terminates jvm and closes socket
        unconnected.setSize(800,1000);
        unconnected.setLayout(null);
        unconnected.setFocusable(true);
        unconnected.setVisible(true);
        unconnected.addWindowListener(closeListener);//terminates jvm and closes socket
    }
    public static ToggleCoverImage[] loadImages() throws IOException {
        String[] f_names = {"adele.jpg", "aubreyplaza.jpg", "edsheeran.jpg", "edwardelric.jpg", "einstein.jpg", "fred.jpg", "freddyfazbear.jpg", "gracehopper.jpg", "madeline.jpg", "oprah.jpg", "scottwozniak.jpg", "thebride.jpg", "tomholland.jpg", "urkel.jpg", "velma.jpg", "princessleia.jpg"};
        ToggleCoverImage[] temp = new ToggleCoverImage[16];
        for (int i = 0; i < 16; i++) {
            BufferedImage image = ImageIO.read(new File("../../assets/" + f_names[i]));
            int y = 3;
            if (i<4){
                y=0;
            } else if (i<8) {
                y=1;
            } else if (i<12) {
                y=2;
            }
            ToggleCoverImage person = new ToggleCoverImage(image, (i%4)*200, y*200, 200, 200);
            temp[i] = person;
        }
        return temp;
    }

    // Method to show the Yes/No popup
    private static void showPopup(String question) {
        JDialog dialog = new JDialog(game, "Confirmation", true); // Modal dialog
        dialog.setUndecorated(true); // Remove title bar decorations

        // Create a content panel with a border layout
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());

        // Use JTextArea to allow text wrapping
        JTextArea textArea = new JTextArea("Opponent Says: " + question);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setBorder(null);
        textArea.setFont(new Font("Arial", Font.PLAIN, 14));

        contentPanel.add(textArea, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton yesButton = new JButton("Yes");
        yesButton.addActionListener(e -> {
            System.out.println("User selected Yes.");
            dialog.dispose(); // Close the dialog
            try {
                writer.println(encrypt(symKey.getAlgorithm(), "Yes", symKey));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        JButton noButton = new JButton("No");
        noButton.addActionListener(e -> {
            System.out.println("User selected No.");
            dialog.dispose(); // Close the dialog
            try {
                writer.println(encrypt(symKey.getAlgorithm(), "No", symKey));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);

        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.getContentPane().add(contentPanel);

        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(game); // Center the dialog
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // Disable the close operation
        dialog.setVisible(true); // Show the dialog
    }
    public static SecretKey generateKey(int n) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(n);
        SecretKey key = keyGenerator.generateKey();
        return key;
    }
    public static String encrypt(String algorithm, String input, SecretKey key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {

        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] cipherText = cipher.doFinal(input.getBytes());
        return Base64.getEncoder()
                .encodeToString(cipherText);
    }
    public static String decrypt(String algorithm, String cipherText, SecretKey key) throws NoSuchPaddingException, NoSuchAlgorithmException,
            BadPaddingException, IllegalBlockSizeException, InvalidKeyException {

        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] plainText = cipher.doFinal(Base64.getDecoder()
                .decode(cipherText));
        return new String(plainText);
    }
    private synchronized static void connect() throws IOException {
        turnServer = new Socket("localhost", TURNPORT);
        try {
            input = turnServer.getInputStream();
            reader = new BufferedReader(new InputStreamReader(input));
            output = turnServer.getOutputStream();
            writer = new PrintWriter(output, true);
            boolean allocated = false;
            while(!allocated){
                    writer.println("allocate|localhost|" + PORT); //send "allocate|target address|target port" to TURN Relay (not encrypted)
                    System.out.println("Sent allocation");
                    String response = reader.readLine();
                    System.out.println(response);
                    if (response.contains("Allocation Successful")) {
                        allocated = true;
                    }
            }
            String publicKeyContent = reader.readLine();      //read servers public key
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyContent));  //create a public key from the plaintext received
            PublicKey publicKey = kf.generatePublic(keySpecX509);
            Cipher encryptCipher = Cipher.getInstance("RSA");
            System.out.println("Server Public Key Plaintext: " + publicKey);
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey); //make encryption cipher with servers public key
            symKey = generateKey(256);
            System.out.println("SYM KEY: " + symKey);
            byte[] encryptedSymKey = encryptCipher.doFinal(symKey.getEncoded());
            String encodedSymKey = Base64.getEncoder().encodeToString(encryptedSymKey);
            System.out.println(encodedSymKey);
            writer.println(encodedSymKey); //encrypt symmetric key with the servers public key
            writer.flush();
            int playerNum = reader.read();
            unconnected.setVisible(false);
            game.setVisible(true);
            if (playerNum == 65){ //ascii code for A, indicating the server has chosen this connection as Player A
                turn = true;
            }
            else{
                turn = false;
                JOptionPane.showMessageDialog(game, "The other player is thinking of a question, you will recieve a message shortly.");
            }
        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }
    private static void startGame() throws IOException {
        SocketListener sl = new SocketListener( turnServer.getInputStream() );
        sl.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                // Logic for handling the state change
                String encStr = (String) e.getSource();
                String str;
                try {
                    str = decrypt(symKey.getAlgorithm(), encStr, symKey);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                System.out.println("State changed! Received data: " + str);

                SwingUtilities.invokeLater(() -> {  //allows these dialogs to be disposed of in the main dispatch thread (don't ask me why)
                    if (str.contains("Timeout")) {
                        turn = false;
                        if (currentMsg != null) {
                            currentMsg.dispose();
                        }
                        currentMsg = new JOptionPane("You took too long! Miss a turn!", JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION).createDialog(game, "Timeout");
                        currentMsg.setVisible(true);
                    }
                    else if (str.strip().equals("Yes") || str.strip().equals("No")){
                        turn = false;
                        if (currentMsg != null) {
                            currentMsg.dispose();
                        }
                        currentMsg = new JOptionPane(("Opponent says " + str), JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION).createDialog(game, "Other Players Turn");
                        currentMsg.setVisible(true);
                    }
                    else if (str.contains("Question")) {
                        turn = true;
                        if (currentMsg != null) {
                            currentMsg.dispose();
                        }
                        currentMsg = new JOptionPane("It is your turn, type a question or a guess, and click send! You have one minute before losing a turn!", JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION).createDialog(game, "Your Turn");
                        currentMsg.setVisible(true);
                    }
                    else if (str.contains("Alone")) {
                        System.exit(1);
                    }
                    else{
                        showPopup(str);
                    }
                });
            }
        });
        Thread t = new Thread( sl );
        t.start();
    }
    private static void closeSocket() {
        try {
            if (turnServer != null) {
                turnServer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
