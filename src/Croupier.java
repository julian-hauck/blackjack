import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class Croupier {

    private static int port;
    private static String name;
    private static int maxPlayers = 6;
    private enum ClientType {PLAYER, COUNTER};
    private enum State {REGISTER, BET, CARDS, PRIZE, WAIT}
    private static State state = State.REGISTER;
    private static final Map<String, ClientInfo> players = new HashMap<>();
    private static final Map<String, ClientInfo> counters = new HashMap<>();

    private record ClientInfo(String ip, int port) { }

    private static void fatal(String input) {
        System.err.println(input);
        System.exit(-1);
    }

    public static boolean isIP(String ip) { // Checks if String is valid IPv4 address
        String[] parts = ip.split("\\."); // Split by dot
        if (parts.length != 4) { return false; } // Must be 4 chunks
        for (String p : parts) { // Check if numbers are valid
            try {
                int number = Integer.parseInt(p);
                if (number < 0 || number > 255) { return false; }
            } catch (NumberFormatException e) { return false; }
        }
        return true;
    }

    public static boolean isPort(String port) {
        try {
            int number = Integer.parseInt(port);
            if (number < 0 || number > 65535) { return false; }
        } catch (NumberFormatException e) { return false; }
        return true;
    }

    public static void main(String[] args) {

        // Handling arguments, checking validity
        if (args.length != 2) {
            fatal("Arguments: \"<port number> <client name>\"");
        }
        if (!isPort(args[0])) {
            fatal("Invalid port number");
        } else {
            port = Integer.parseInt(args[0]);
        }
        name = args[1];

        System.out.println(name + " (Port: " + port + ") is here, looking around.\nUse \"register <ip address> <port number>\" to contact another client.\nUse \"send <registered client name> <message>\" to message them.\nUse \"quit\" to exit program.");
        // Start a new thread to listen for messages
        new Thread(() -> receiveLines(port)).start();

        // Main thread continues to process user input
        try(BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) { // closes automatically
            String input;
            while (!(input = br.readLine()).equalsIgnoreCase("quit")) {
                String[] parts = input.split(" ");
                if (parts[0].equalsIgnoreCase("register") && parts.length == 3 && isPort(parts[2])) {
                    register(parts[1], Integer.parseInt(parts[2]));
                } else if (parts[0].equalsIgnoreCase("send")) {
                    String receiver = parts[1];
                    ClientInfo receiverInfo = players.get(receiver);
                    if (receiverInfo != null) {
                        String message = input.substring(input.indexOf(receiver) + receiver.length()).trim();
                        sendLines(receiverInfo.ip, receiverInfo.port, message);
                        System.out.println("Sent \"" + message + "\" to " + receiver + ".");
                    } else {
                        System.err.println("Unknown client \"" + receiver + "\".");
                    }
                } else {
                    System.err.println("Unknown command.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static final int packetSize = 4096;

    private static void receiveLines(int ownPort) {
        try(DatagramSocket s = new DatagramSocket(ownPort)) { // closes automatically
            byte[] buffer = new byte[packetSize];
            String line;
            do {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                s.receive(p);
                line = new String(buffer, 0, p.getLength(), StandardCharsets.UTF_8);
                if (line.startsWith("register")) {
                    String[] parts = line.split(" ");
                    String name = parts[3];
                    String ip = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String message = "registration successful";
                    if (parts[0].equals("registerPlayer")) {
                        if (players.containsKey("name")) {
                            ClientInfo c = players.get(name);
                            if (ip.equals(c.ip) && port == c.port) {
                                sendMessage(ip, port, message);  // Wiederholung der Bestaetigung
                            } else {
                                message = "registration declined Name bereits vergeben";
                                sendMessage(ip, port, message);
                            }
                        } else if (state != State.REGISTER) {
                            message = "registration declined Spiel hat bereits begonnen";
                            sendMessage(ip, port, message);
                        } else if (players.size() < maxPlayers) {
                            message = "registration declined maximale Spieleranzahl erreicht";
                            sendMessage(ip, port, message);
                        } else {
                            players.put(name, new ClientInfo(ip, port));
                            sendMessage(name, ClientType.PLAYER, message);
                            System.out.println("Spieler " + name + " registriert");
                        }
                    } else if (parts[0].equals("registerCounter")) {
                        if (counters.containsKey("name")) {
                            ClientInfo c = counters.get(name);
                            if (ip.equals(c.ip) && port == c.port) {
                                sendMessage(ip, port, message); // Wiederholung der Bestaetigung
                            } else {
                                message = "registration declined Spieler hat bereits einen Kartenzähler";
                                sendMessage(ip, port, message);
                            }
                        } else if (!players.containsKey("name")) {
                            message = "registration declined Spieler unbekannt";
                            sendMessage(ip, port, message);
                        } else {
                            counters.put(name, new ClientInfo(ip, port));
                            sendMessage(name, ClientType.COUNTER, message);
                            System.out.println("Spieler " + name + " registriert");
                        }
                    }
                }
                System.out.println(line);
            } while (!line.equalsIgnoreCase("quit"));
        } catch (IOException e) {
            System.err.println("Unable to receive message on ownPort \"" + ownPort + "\".");
        }
    }

    private static void sendMessage(String name, ClientType type, String message) {
        ClientInfo client;
        client = type == ClientType.PLAYER ? players.get(name) : counters.get(name);
        sendMessage(client, message);
    }

    private static void sendMessage(String ip, int port, String message) {
        ClientInfo client = new ClientInfo(ip, port);
        sendMessage(client, message);
    }

    private static void sendMessage(ClientInfo client, String message) {
        try (DatagramSocket s = new DatagramSocket()) { // closes automatically
            InetAddress ip = InetAddress.getByName(client.ip);
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(buffer, buffer.length, ip, client.port);
            s.send(p);
            //System.out.println("Message sent.");
        } catch (IOException e) {
            System.err.println("Unable to send message to \"" + " " + client.ip + " " + client.port + "\".");
        }
    }
}