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

    /**
     * Le constructeur lance le serveur
     *
     * @param  port le port du serveur à lancer
     */
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

        /**
         * Une nouvelle instance de cette class pour chaque client connecté
         *
         * @param  client l'objet Socket du client
         */
        protected ClientProcess (Socket client) {
            new Thread(() -> {
                try {
                    this.client = client;
                    this.client.setTcpNoDelay(true);
                    this.out = new PrintWriter(new BufferedOutputStream(client.getOutputStream()), true);
                    this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                    while (client.isConnected() && !client.isClosed()) {
                        try {
                            // traite la requête dans une méthode à part
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

        /**
         * Vérifie si la requête servait à s'authentifier, qui sera prise en compte le cas concluant.
         * Ou redirige la requête vers une autre méthode seulement si le client est authentifié.
         *
         * @param  request la requête provenant du client sous forme de Map
         */
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

        /**
         * Lis les entête de la requête
         * Afin d'envoyer aux clients correspondants la requête
         *
         * @param  request la requête provenant du client sous forme de Map
         */
        protected void transferData (Map<String, String> request) {
            if (request.keySet().containsAll(requires)) {
                // si la requête contient tous les entête requis

                request.put("__sender", name);

                String recipient = request.get("__recipient");

                if (recipient.equals("all")) {
                    // permet d'envoyer à tous les clients connectés au serveur
                    clients.forEach((name, clientProcess) -> {
                        if (!clientProcess.client.isClosed()) {
                            clientProcess.out.println(SerializeMap.map2str(request));
                            clientProcess.out.flush();
                        }
                    });
                } else if (clients.containsKey(recipient)) {
                    ClientProcess destination = clients.get(recipient);
                    if (!destination.client.isClosed()) {
                        destination.out.println(SerializeMap.map2str(request));
                        destination.out.flush();
                    }
                }
            }
        }
    }
}