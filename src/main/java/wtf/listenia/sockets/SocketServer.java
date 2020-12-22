package wtf.listenia.sockets;

import wtf.listenia.sockets.converter.SerializeMap;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SocketServer {

    public boolean online = true;
    public static HashMap<String, ClientProcess> clients = new HashMap<>();

    public SocketServer (int port) {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                System.out.println("[Sockets] Serveur démarré");

                while (online)
                    new ClientProcess(server.accept());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }


    public static class ClientProcess {
        private Socket client;
        private String name = null;
        private PrintWriter out;
        private BufferedReader in;

        protected ClientProcess (Socket client) {
            new Thread(() -> {
                try {
                    this.client = client;
                    this.client.setTcpNoDelay(true);
                    this.out = new PrintWriter(new BufferedOutputStream(client.getOutputStream()), true);
                    this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                    while (client.isConnected() && !client.isClosed()) {
                        try {
                            executeCmd(SerializeMap.str2map(in.readLine()));
                        } catch (Exception e) { break; }
                    }

                    out.close();
                    in.close();

                    if (name != null)
                        System.out.printf("[Sockets] Le client %s s'est déconnecté%n", name);


                } catch (Exception ignored) { }
            }).start();
        }

        protected void executeCmd (Map<String, String> request) {
            if (request.containsKey("__auth")) {
                this.name = request.get("__auth");
                clients.put(this.name, this);

                System.out.printf("[Sockets] Client %s connecté sous le nom de %s%n", client.getRemoteSocketAddress(), name);
            } else if (this.name != null){
                transferData(request);
            }
        }

        private final List<String> requires = Arrays.asList("__recipient", "__channel");
        protected void transferData (Map<String, String> request) {
            if (request.keySet().containsAll(requires)) {

                String recipient = request.get("__recipient");
                if (clients.containsKey(recipient)) {
                    request.put("__sender", name);

                    if (recipient.equals("all")) {
                        clients.forEach((k, v) -> {
                            if (!v.client.isClosed()) {
                                v.out.println(SerializeMap.map2str(request));
                                v.out.flush();
                            }
                        });
                    } else {
                        ClientProcess destination = clients.get(recipient);
                        if (!destination.client.isClosed()) {
                            PrintWriter output = destination.out;
                            output.println(SerializeMap.map2str(request));
                            output.flush();
                        }
                    }
                }
            }
        }
    }
}