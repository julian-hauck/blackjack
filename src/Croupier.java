import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Croupier {

    private static int myValue;
    private static int port;
    private static int maxPlayers = 6;
    private static int maxBet = 5000;
    private static int minBet = 1;
    private enum ClientType {PLAYER, COUNTER};
    private enum State {REGISTER, BET, PLAYING, PRIZE, WAIT}
    private static State state = State.REGISTER;
    private static final Map<String, Player> players = new HashMap<>();
    private static final Map<String, Counter> counters = new HashMap<>();
    private static final Map<String, Map<String, Client>> unacknowledged = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, Boolean> unacknowledgedWin = Collections.synchronizedMap(new HashMap<>());
    private static int decks = 10;
    static CardStack stack = new CardStack(decks);

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
        if (args.length != 1) {
            fatal("Arguments: \"<port number>\"");
        }
        if (!isPort(args[0])) {
            fatal("Invalid port number");
        } else {
            port = Integer.parseInt(args[0]);
        }

        System.out.println("Croupier (Port: " + port + ") is here, waiting for registrations.");
        System.out.println("When all players and card counters are registered, press <Enter> to start the game.");
        // Start a new thread to listen for messages
        new Thread(() -> receiveLines(port)).start();

        // Main thread continues to process user input
        try(BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) { // closes automatically
            String input;
            while (!(input = br.readLine()).equalsIgnoreCase("quit")) {
                sendCard(stack.pop("Croupier"));
                for (Player p : players.values()) {
                    System.out.println(p.getName());
                    p.init();
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
                String[] parts = line.split(" ");
                System.out.println(line);
                if (line.startsWith("register")) {
                    String name = parts[3];
                    String ip = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String message = "registration successful";
                    if (parts[0].equals("registerPlayer")) {
                        if (players.containsKey(name)) {
                            Player c = players.get(name);
                            if (ip.equals(c.getIp()) && port == c.getPort()) {
                                sendMessage(ip, port, message);  // Wiederholung der Bestaetigung
                            } else {
                                message = "registration declined Name bereits vergeben";
                                sendMessage(ip, port, message);
                            }
                        } else if (state != State.REGISTER) {
                            message = "registration declined Spiel hat bereits begonnen";
                            sendMessage(ip, port, message);
                        } else if (players.size() >= maxPlayers) {
                            message = "registration declined maximale Spieleranzahl erreicht";
                            sendMessage(ip, port, message);
                        } else {
                            players.put(name, new Player(ip, port, name, stack));
                            sendMessage(ip, port, message);
                            System.out.println("Spieler " + name + " registriert");
                        }
                    } else if (parts[0].equals("registerCounter")) {
                        if (counters.containsKey("name")) {
                            Counter c = counters.get(name);
                            if (ip.equals(c.getIp()) && port == c.getPort()) {
                                sendMessage(ip, port, message); // Wiederholung der Bestaetigung
                            } else {
                                message = "registration declined Spieler hat bereits einen Kartenzähler";
                                sendMessage(ip, port, message);
                            }
                        } else if (!players.containsKey("name")) {
                            message = "registration declined Spieler unbekannt";
                            sendMessage(ip, port, message);
                        } else {
                            counters.put(name, new Counter(ip, port, name));
                            sendMessage(ip, port, message);
                            System.out.println("Spieler " + name + " registriert");
                        }
                    }
                } else if (parts[0].equals("bet")) {
                    Player pl = players.get(parts[1]);
                    int bet = Integer.parseInt(parts[2]);
                    if (pl == null) {
                        System.out.println("Nachricht von unbekanntem Client erhalten.");
                    } else if (state != State.REGISTER && state != State.WAIT && state != State.BET) {
                        sendMessage(pl, "bet declined momentan nicht moeglich");
                    } else if (bet < minBet || bet > maxBet) {
                        sendMessage(pl, "bet declined Wette nicht zwischen " + minBet + " und " + maxBet);
                    } else {
                        pl.setBet(bet);
                        sendMessage(pl, "bet accepted");
                    }
                } else if (line.startsWith("numberOfDecks")) {
                    Counter c = counters.get(parts[1]);
                    if (c != null) {
                        sendMessage(c, "numberOfDecks " + decks);
                    }
                } else if (line.startsWith("counter")) {
                    Map<String, Client> map = unacknowledged.get(parts[3] + " " + " " + parts[4] + " "+ " " + parts[5]);
                    if (map != null) {
                        map.remove("counter " + parts [1]);
                    }
                } else if (line.startsWith("player")) {
                    Map<String, Client> map = unacknowledged.get(parts[3] + " " + " " + parts[4] + " " + " " + parts[5]);
                    if (map != null) {
                        map.remove("player " + parts[1]);
                    }
                } else if (line.startsWith("prize accepted")) {
                    unacknowledgedWin.put(parts[1], true);
                } else if (state == State.PLAYING) {
                    Player pl = players.get(parts[1]);
                    if (pl == null) {
                        System.out.println("Nachricht von unbekanntem Client erhalten.");
                    } else if (line.startsWith("hit")) {
                        pl.action(Player.Action.HIT, parts[3] + " " + parts[4], Integer.parseInt(parts[2]));
                    } else if (line.startsWith("stand")) {
                        pl.action(Player.Action.STAND, parts[3] + " " + parts[4], Integer.parseInt(parts[2]));
                    } else if (line.startsWith("doubleDown")) {
                        pl.action(Player.Action.DOUBLE, parts[3] + " " + parts[4], Integer.parseInt(parts[2]));
                    } else if (line.startsWith("surrender")) {
                        pl.action(Player.Action.SURRENDER, parts[3] + " " + parts[4], Integer.parseInt(parts[2]));
                    } else if (line.startsWith("split")) {
                        pl.action(Player.Action.SPLIT, parts[3] + " " + parts[4], Integer.parseInt(parts[2]));
                    }
                }
                System.out.println("received: " + line);
            } while (!line.equalsIgnoreCase("quit"));
        } catch (IOException e) {
            System.err.println("Unable to receive message on ownPort \"" + ownPort + "\".");
        }
    }

    public static void sendMessage(String ipStr, int port, String message) {
        try (DatagramSocket s = new DatagramSocket()) { // closes automatically
            InetAddress ip = InetAddress.getByName(ipStr);
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(buffer, buffer.length, ip, port);
            s.send(p);
            System.out.println("Message sent:" + message);
        } catch (IOException e) {
            System.err.println("Unable to send message to \"" + " " + ipStr + " " + port + "\".");
        }
    }

    public static void sendMessage(Client client, String message) {
        sendMessage(client.getIp(), client.getPort(), message);
    }

    public static void sendWin(Player p) {
        try {
            unacknowledgedWin.put(p.getName(), false);
            int counter = 0;
            while (counter < 5 && !unacknowledgedWin.get(p.getName())) {
                p.giveWin(myValue);
                TimeUnit.SECONDS.sleep(2);
                counter++;
            }
            //TODO remove clients
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendCard(Card card) {
        try {
            String serializedCard = card.toJSON();
            Map<String, Client> receivers = getClientsMap();
            unacknowledged.put(card.getDeck() + " " + card.toString(), receivers);
            int counter = 0;
            while (counter < 5 && !receivers.isEmpty()) {
                for (Client c : receivers.values()) {
                    sendMessage(c, serializedCard);
                }
                TimeUnit.SECONDS.sleep(2);
                counter++;
            }
            //TODO remove clients
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, Client> getClientsMap() {
        Map<String, Client> clients = Collections.synchronizedMap(new HashMap<>());
        for (Player p : players.values()) {
            clients.put("player " + p.getName(), p);
        }
        for (Counter c : counters.values()) {
            clients.put("counter " + c.getName(), c);
        }
        return clients;
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