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

/**
 * Permet de creer un client Socket de SocketExchange
 * Avec multiples methodes pour communiquer avec d'autre clients
 * Tout en restant productif et rapide
 *
 * @author 360matt
 */
public class SocketClient {
    private static final Map<String, Consumer<Map<String, String>>> listeners = new HashMap<>();
    private static final ConcurrentHashMap<String, Callback> callbacks = new ConcurrentHashMap<>();

    private Socket client;
    private PrintWriter out;
    private BufferedReader in;

    /**
     * Le constructeur connecte le client au serveur et s'y authentifie
     *
     * @param  name le nom du client
     * @param  host l'adresse du serveur a se connecter
     * @param  port le port du serveur a se connecter
     * @author 360matt
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

    protected void registerDefaultListener () {
        listen("isOnline", x -> reply(x, new HashMap<>()));
    }




    /**
     * Traite une requete recu depuis le serveur.
     * Et envoie la requete vers un listener ou un callback
     *
     * @param  request  la requete que le client a recu au format Map
     * @author 360matt
     */

    protected void processRequest (Map<String, String> request) {
        final String channel = request.get("__channel");

        if (!channel.startsWith("reply_")) {
            /* permanent listeners */

            for (Map.Entry<String, Consumer<Map<String, String>>> entry : listeners.entrySet()) {
                if (entry.getKey().startsWith(channel)) {
                    entry.getValue().accept(request);
                    break;
                }
            }

        } else {
            final String id = request.get("__id_reply");

            /* one time listeners */



            for (Map.Entry<String, Callback> entry : callbacks.entrySet()) {
                if (entry.getKey().startsWith(channel)) {
                    if (entry.getKey().split("#")[1].equals(id)) {
                         final Callback callback = entry.getValue();
                         callback.consumer.accept(request);
                         if (callback.threadTimeout != null)
                            callback.threadTimeout.interrupt();
                         callbacks.remove(entry.getKey());
                         break;
                    }
                }
            }
        }
    }

    /**
     * Initialise un listener qui sera pret pour recevoir des requetes
     * Chaque listener ne peut prendre en charge qu'un channel
     *
     * @param  channel  le nom du channel qui sera ecoute
     * @param  map  la requete sous forme de Map que le client recevra
     * @author 360matt
     */
    public void listen (String channel, Consumer<Map<String, String>> map) {
        int randomNum = ThreadLocalRandom.current().nextInt(20, 5001);
        listeners.put(channel + "#" + randomNum, map);
    }

    /**
     * Repond a une requete receptionnee par un listener
     *
     * @param  before  la requete qui en est l'origine
     * @param  reply la reponse a cette requete
     * @author 360matt
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
     * Repond a une requete receptionnee par un listener
     *
     * @param  target le client qui doit recevoir cette requete
     * @param  channel le channel a laquelle la requete doit vehiculer
     * @param  data le contenu de la requete sous la forme Map
     * @return un Callback permettant de capturer une reponse
     * @author 360matt
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
     * Recupere le status d'un client quelquonque, reel ou non
     *
     * @param  target le client qu'on questionne
     * @return le status du client
     * @author 360matt
     */
    public boolean isOnline (String target) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        send(target, "isOnline", new HashMap<>())
                .callback(x -> future.complete(true))
                .timeout(x -> future.complete(false));

        return future.join();
    }

    /**
     * Recupere le ping reel aller-retour d'un client reel.
     * Si le client n'est pas joignable, le ping sera de 1s (1000ms)
     *
     * @param  target le client qu'on questionne
     * @return un entier compris dans le rang [0;1000] representant le ping
     * @author 360matt
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
     * La class de Callback permettant de capturer une reponse suite a une requuete.
     * @author 360matt
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
         * @param  callback le consumer de type Map qui sera call lors de la reponse
         * @return l'instance de cette class
         * @author 360matt
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
         * Defini le temps d'attente
         *
         * @param  seconds le delais s'exprimant en seconde
         * @return l'instance de cette class
         * @author 360matt
         */
        public Callback waiting (float seconds) { wait = seconds; return this; }

        /**
         * Execute le consumer du timeout si aucune reponse n'a ete recu pendant le delais.
         *
         * Elle doit etre imperativement executee
         * pour retirer le consumer du callback de la liste
         * des listener temporaires.
         * Dans le cas contraire il y aurait une fuite de memoire.
         *
         * @param  fail le consumer de type Void qui sera call si aucune reponse n'est parvenue.
         * @author 360matt
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