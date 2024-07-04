public class Counter implements Client {
    private String ip;
    private int port;
    private String name;

    public Counter(String ip, int port, String name) {
        this.ip = ip;
        this.port = port;
        this.name = name;
    }

    @Override
    public String getIp() {
        return ip;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getName() {
        return name;
    }
}
