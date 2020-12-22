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

    private Socket client;
    private PrintWriter out;
    private BufferedReader in;

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

    public void registerDefaultListener () {
        listen("isOnline", x -> reply(x, new HashMap<>()));
    }


    public Pattern patternListeners = Pattern.compile("(.*)#([0-9]*)");
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


    public void listen (String channel, Consumer<Map<String, String>> map) {
        int randomNum = ThreadLocalRandom.current().nextInt(20, 5001);
        listeners.put(channel + "#" + randomNum, map);
    }

    public void reply (Map<String, String> before, Map<String, String> reply) {
        if (!client.isClosed() && client.isConnected()) {
            reply.put("__id_reply", before.get("__id_reply"));
            reply.put("__recipient", before.get("__sender"));
            reply.put("__channel", "reply_" + before.get("__channel"));

            out.println(SerializeMap.map2str(reply));
            out.flush();
        }
    }

    public Callback send (String server, String channel, Map<String, String> data) {
        if (!client.isClosed() && client.isConnected()) {
            final int id = ThreadLocalRandom.current().nextInt(20, 10001);

            data.put("__id_reply", String.valueOf(id));
            data.put("__recipient", server);
            data.put("__channel", channel);

            out.println(SerializeMap.map2str(data));
            out.flush();

            return new Callback(data, id);
        }
        return new Callback();
    }

    public boolean isOnline (String server) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        send(server, "isOnline", new HashMap<>())
                .callback(x -> future.complete(true))
                .timeout(x -> future.complete(false));

        return future.join();
    }

    public int getPing (String server) {
        Long date = new Date().getTime();
        CompletableFuture<Long> future = new CompletableFuture<>();
        send(server, "isOnline", new HashMap<>())
                .callback(x -> future.complete(new Date().getTime()))
                .waiting(1)
                .timeout(x -> future.complete(new Date().getTime()));

        return (int) (future.join() - date);
    }

    public static class Callback {
        private final Map<String, String> map;
        private String id;
        private int rdmID;

        private Consumer<Map<String, String>> consumer;

        private Thread threadTimeout;
        private float wait = 0.05F;


        public Callback () { this.map = null; }
        public Callback (Map<String, String> json, int rdmID) { this.map = json; this.rdmID = rdmID; }

        public Callback callback (Consumer<Map<String, String>> callback) {
            this.consumer = callback;
            if (map != null) {
                this.id = "reply_" + map.get("__channel") + "#" + rdmID;
                callbacks.put(this.id, this);
            }
            return this;
        }

        public Callback waiting (float seconds) { wait = seconds; return this; }

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