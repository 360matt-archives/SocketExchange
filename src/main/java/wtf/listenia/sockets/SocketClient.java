package wtf.listenia.sockets;


import wtf.listenia.sockets.converter.SerializeMap;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SocketClient {
    private static final ConcurrentHashMap<String, Consumer<Map<String, String>>> listeners = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Callback> callbacks = new ConcurrentHashMap<>();

    private static final Pattern patternListeners = Pattern.compile("(.*)#([0-9]*)");


    private Socket client;
    private PrintWriter out;
    private BufferedReader in;

    /**
     * Le constructeur connecte le client au serveur et s'y authentifie
     *
     * @param  port le port du serveur à lancer
     */
    public SocketClient (String name, String host, int port) {
        new Thread(() -> {
            try {
                registerDefaultListener();
                while (true) {
                    // auto-reconnect
                    try {
                        client = new Socket(host, port);
                        client.setTcpNoDelay(true);

                        out = new PrintWriter(new BufferedOutputStream(client.getOutputStream()), true);
                        in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                        out.println("__auth=" + name);
                        out.flush();

                        System.out.printf( "[Sockets - %s] Connecté à %s:%s%n", name, host, port );

                        while (client.isConnected() && !client.isClosed()) {
                            // listen futures requests
                            processRequest(SerializeMap.str2map(in.readLine()));
                        }

                        System.out.printf( "[Sockets - %s] Connexion du serveur perdue%n", name );

                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.printf( "[Sockets - %s] Essais de reconnexion dans 5 secondes ...%n", name );
                        TimeUnit.SECONDS.sleep(5L);
                    } finally {
                        try {
                            out.close();
                            in.close();
                            client.close();
                        } catch (Exception ignored) { }
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void registerDefaultListener () {
        listen("isOnline", x -> reply(x, new HashMap<>()));
    }




    /**
     * Traite une requête reçu depuis le serveur.
     * Et envoie la requête vers un listener ou un callback
     *
     * @param  request  la requête que le client a reçu au format Map<></>
     */

    public void processRequest (Map<String, String> request) {
        String channel = request.get("__channel");
        String id = request.get("__id_reply");

        if (!channel.startsWith("reply_")) {
            /* permanent listeners */
            listeners.forEach((k, v) -> {
                Matcher matcher = patternListeners.matcher(k);

                if (matcher.find() && matcher.group(1).equals(channel))
                    listeners.get(k).accept(request);
            });
        } else {
            /* one time listeners */
            Iterator<String> it = callbacks.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();

                Matcher matcher = patternListeners.matcher(key);

                if (matcher.find() && matcher.group(1).equals(channel) && matcher.group(2).equals(id)) {
                    Callback callback = callbacks.get(key);
                    callback.consumer.accept(request);
                    if (callback.threadTimeout != null)
                        callback.threadTimeout.interrupt();
                    it.remove();
                    break;
                }
            }
        }
    }

    /**
     * Initialise un listener qui sera prêt pour reçevoir des requêtes
     * Chaque listener ne peut prendre en charge qu'un channel
     *
     * @param  channel  le nom du channel qui sera écouté
     * @param  map  la requête sous forme de Map<> que le client recevra
     */
    public void listen (String channel, Consumer<Map<String, String>> map) {
        int randomNum = ThreadLocalRandom.current().nextInt(20, 5001);
        listeners.put(channel + "#" + randomNum, map);
    }

    /**
     * Répond à une requête receptionnée par un listener
     *
     * @param  before  la requête qui en est l'origine
     * @param  reply la réponse à cette requête
     */
    public void reply (Map<String, String> before, Map<String, String> reply) {
        if (!client.isClosed() && client.isConnected()) {
            reply.put("__id_reply", before.get("__id_reply"));
            reply.put("__recipient", before.get("__sender"));
            reply.put("__channel", "reply_" + before.get("__channel"));

            out.println(SerializeMap.map2str(reply));
            out.flush();
        }
    }

    /**
     * Répond à une requête receptionnée par un listener
     *
     * @param  target le client qui doit reçevoir cette requête
     * @param  channel le channel à laquelle la requête doit véhiculer
     * @param  data le contenu de la requête sous la forme Map<>
     * @return un Callback permettant de capturer une réponse
     */
    public Callback send (String target, String channel, Map<String, String> data) {
        if (!client.isClosed() && client.isConnected()) {
            final int id = ThreadLocalRandom.current().nextInt(20, 10001);

            data.put("__id_reply", String.valueOf(id));
            data.put("__recipient", target);
            data.put("__channel", channel);

            out.println(SerializeMap.map2str(data));
            out.flush();

            return new Callback(data, id);
        }
        return new Callback();
    }

    /**
     * Récupère le status d'un client quelquonque, réel ou non
     *
     * @param  target le client qu'on questionne
     * @return le status du client
     */
    public boolean isOnline (String target) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        send(target, "isOnline", new HashMap<>())
                .callback(x -> future.complete(true))
                .timeout(x -> future.complete(false));

        return future.join();
    }

    /**
     * Récupère le ping réel aller-retour d'un client réel.
     * Si le client n'est pas joignable, le ping sera de 1s (1000ms)
     *
     * @param  target le client qu'on questionne
     * @return un entier compris dans le rang [0;1000] représentant le ping
     */
    public int getPing (String target) {
        Long date = new Date().getTime();
        CompletableFuture<Long> future = new CompletableFuture<>();
        send(target, "isOnline", new HashMap<>())
                .callback(x -> future.complete(new Date().getTime()))
                .waiting(1)
                .timeout(x -> future.complete(new Date().getTime()));

        return (int) (future.join() - date);
    }

    /**
     * La class de Callback permettant de capturer une réponse suite à une requuête.
     */
    public static class Callback {
        private final Map<String, String> map;
        private String id;
        private int rdmID;

        private Consumer<Map<String, String>> consumer;

        private Thread threadTimeout;
        private float wait = 0.05F;


        public Callback () { this.map = null; }
        public Callback (Map<String, String> json, int rdmID) { this.map = json; this.rdmID = rdmID; }

        /**
         * Enregistre le callback dans un listener unique
         *
         * @param  callback le consumer de type Map<> qui sera call lors de la réponse
         * @return l'instance de cette class
         */
        public Callback callback (Consumer<Map<String, String>> callback) {
            this.consumer = callback;
            if (map != null) {
                this.id = "reply_" + map.get("__channel") + "#" + rdmID;
                callbacks.put(this.id, this);
            }
            return this;
        }

        /**
         * Défini le temps d'attente
         *
         * @param  seconds le délais s'exprimant en seconde
         * @return l'instance de cette class
         */
        public Callback waiting (float seconds) { wait = seconds; return this; }

        /**
         * Execute le consumer du timeout si aucune réponse n'a été reçu pendant le délais.
         *
         * Elle doit être impérativement executée
         * pour retirer le consumer du callback de la liste
         * des listener temporaires.
         * Dans le cas contraire il y aurait une fuite de mémoire.
         *
         * @param  fail le consumer de type Void qui sera call si aucune réponse n'est parvenue.
         * @return ne renvoie pas l'instance de la class
         *
         */
        public void timeout (Consumer<Void> fail) {
            if (map != null) {
                threadTimeout = new Thread(() -> {
                    try {
                        TimeUnit.MILLISECONDS.sleep((long) (this.wait * 1000));
                        if (callbacks.containsKey(id)) {
                            callbacks.remove(id);
                            fail.accept(null);
                        }
                    } catch (Exception ignored) { }
                });
                threadTimeout.start();
            } else
                fail.accept(null);
        }
    }

}