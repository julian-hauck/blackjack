import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Croupier {

    private static int port;
    private static String name;
    private static int maxPlayers = 6;
    private enum ClientType {PLAYER, COUNTER};
    private enum State {REGISTER, BET, CARDS, PRIZE, WAIT}
    private static State state = State.REGISTER;
    private static final Map<String, Player> players = new HashMap<>();
    private static final Map<String, ClientInfo> counters = new HashMap<>();
    private static final Map<String, Set<ClientInfo>> unacknowledged = Collections.synchronizedMap(new HashMap<>());
    static CardStack stack = new CardStack(7);

    private record ClientInfo(String ip, int port) { };
    private record toAcknowledge(ClientType type, String name, String card) {};

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
                sendCard(stack.pop("Croupier"));
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
                String[] parts = line.split(" ");
                if (line.startsWith("register")) {
                    String name = parts[3];
                    String ip = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String message = "registration successful";
                    if (parts[0].equals("registerPlayer")) {
                        if (players.containsKey("name")) {
                            Player c = players.get(name);
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
                            players.put(name, new Player(ip, port, name, stack));
                            sendMessage(ip, port, message);
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
                            sendMessage(ip, port, message);
                            System.out.println("Spieler " + name + " registriert");
                        }
                    }
                } else if (line.startsWith("hit")) {
                    Player pl = players.get(parts[1]);
                    pl.hit(parts[2], Integer.parseInt(parts[3]));
                } else if (line.startsWith("stand")) {
                    Player pl = players.get(parts[1]);
                    pl.stand(parts[2], Integer.parseInt(parts[3]));
                } else if (line.startsWith("doubleDown")) {
                    Player pl = players.get(parts[1]);
                    pl.doubleDown(parts[2], Integer.parseInt(parts[3]));
                } else if (line.startsWith("surrender")) {
                    Player pl = players.get(parts[1]);
                    pl.surrender(parts[2], Integer.parseInt(parts[3]));
                } else if (line.startsWith("split")) {
                    Player pl = players.get(parts[1]);
                    pl.split(parts[2], Integer.parseInt(parts[3]));
                }
                System.out.println(line);
            } while (!line.equalsIgnoreCase("quit"));
        } catch (IOException e) {
            System.err.println("Unable to receive message on ownPort \"" + ownPort + "\".");
        }
    }

    public static void sendMessage(String ip, int port, String message) {
        ClientInfo client = new ClientInfo(ip, port);
        sendMessage(client, message);
    }

    public static void sendMessage(Player player, String message) {
        ClientInfo client = new ClientInfo(player.getIp(), player.getPort());
        sendMessage(client, message);
    }

    public static void sendMessage(ClientInfo client, String message) {
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

    public static void sendCard(Card card) {
        try {
            String serializedCard = card.toJSON();
            for (Player p : players.values()) {
                sendMessage(p.getIp(), p.getPort(), serializedCard);
            }
            //TODO remove clients

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void checkTerminated() {
        boolean terminated = true;
        for (Player p : players.values()) {
            if (!p.getTerminated()) {
                terminated = false;
            }
        }
        if (terminated) {
            for (Player p : players.values()) {
                p.giveWin(21);
            }
        }
    }
}