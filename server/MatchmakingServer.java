import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

class NPH {
    public static final byte NET_HOST = 0;
    public static final byte NET_GET = 1;
    public static final byte NET_JOIN = 2;
    public static final byte NET_START = 3;
    public static final byte NET_PING = 4;
}

class SClient {
    String uid;
    InetAddress ip;
    int port;
    long timeout;

    SClient(String uid, InetAddress ip, int port) {
        this.uid = uid;
        this.ip = ip;
        this.port = port;
        this.timeout = System.currentTimeMillis();
    }
}

class SLobby {
    String hostUid;
    ConcurrentHashMap<String, SClient> clients = new ConcurrentHashMap<>();

    SLobby(SClient host) {
        this.hostUid = host.uid;
        clients.put(host.uid, host);
    }
}

public class MatchmakingServer {

    static DatagramSocket socket;

    static ConcurrentHashMap<String, SClient> clients = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, SLobby> lobbies = new ConcurrentHashMap<>();

    static byte[] uidBuf = new byte[8];

    static String readUID(ByteBuffer buf) {
        buf.get(uidBuf);
        return new String(uidBuf, StandardCharsets.UTF_8).trim();
    }

    static void send(byte[] data, int len, InetAddress ip, int port) throws IOException {
        socket.send(new DatagramPacket(data, len, ip, port));
    }

    public static void main(String[] args) throws Exception {

        socket = new DatagramSocket(16969);
        System.out.println("Matchmaking server running on 22565");

        byte[] recvBuf = new byte[1024];

        while (true) {

            DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
            socket.receive(packet);

            ByteBuffer buf = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());

            if (buf.remaining() < 9) continue;

            byte header = buf.get();
            String uid = readUID(buf);

            InetAddress ip = packet.getAddress();
            int port = packet.getPort();

            SClient client = clients.computeIfAbsent(uid,
                    k -> new SClient(uid, ip, port));

            client.ip = ip;
            client.port = port;
            client.timeout = System.currentTimeMillis();

            switch (header) {

                // ---------------- HOST LOBBY ----------------
                case NPH.NET_HOST: {

                    SLobby lobby = new SLobby(client);
                    lobbies.put(uid, lobby);

                    System.out.println("Lobby created: " + uid);
                    break;
                }

                // ---------------- LIST LOBBIES ----------------
                case NPH.NET_GET: {

                    ByteBuffer out = ByteBuffer.allocate(512);
                    out.put(NPH.NET_GET);

                    out.put((byte) lobbies.size());

                    for (String id : lobbies.keySet()) {
                        byte[] b = id.getBytes(StandardCharsets.UTF_8);
                        out.put(Arrays.copyOf(b, 8));
                    }

                    send(out.array(), out.position(), ip, port);
                    break;
                }

                // ---------------- JOIN LOBBY ----------------
                case NPH.NET_JOIN: {

                    String lobbyId = readUID(buf);
                    SLobby lobby = lobbies.get(lobbyId);

                    if (lobby == null) break;

                    lobby.clients.put(uid, client);

                    ByteBuffer out = ByteBuffer.allocate(64);
                    out.put(NPH.NET_JOIN);
                    out.put(uid.getBytes(StandardCharsets.UTF_8));

                    byte[] data = out.array();

                    for (SClient c : lobby.clients.values()) {
                        send(data, out.position(), c.ip, c.port);
                    }

                    System.out.println(uid + " joined " + lobbyId);
                    break;
                }

                // ---------------- START LOBBY (IMPORTANT PART) ----------------
                case NPH.NET_START: {

                    SLobby lobby = lobbies.get(uid);
                    if (lobby == null) break;

                    ByteBuffer out = ByteBuffer.allocate(1024);
                    out.put(NPH.NET_START);

                    // client count (excluding host if you want — keep your original behavior)
                    out.put((byte) lobby.clients.size());

                    for (SClient c : lobby.clients.values()) {

                        // UID
                        out.put(Arrays.copyOf(c.uid.getBytes(StandardCharsets.UTF_8), 8));

                        // IP
                        out.put(c.ip.getAddress());

                        // PORT
                        out.putInt(c.port);
                    }

                    byte[] data = out.array();

                    for (SClient c : lobby.clients.values()) {
                        send(data, out.position(), c.ip, c.port);
                    }

                    System.out.println("Started lobby: " + uid);
                    break;
                }
            }
        }
    }
}
