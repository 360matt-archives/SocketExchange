import wtf.listenia.sockets.SocketClient;
import wtf.listenia.sockets.SocketServer;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ServerTest {

    public static void main (String[] args) throws InterruptedException {

        new Thread(() -> { // server
            SocketServer srv = new SocketServer(25565);
        }).start();


        new Thread(() -> { // client TWO
            try {
                SocketClient client = new SocketClient("two", "127.0.0.1", 25565);


                Thread.sleep(500);

                final int nb = 4;
                final int tests = 1;
                final List<Integer> moyennes = new ArrayList<>();


                for (int k = 0; k < tests; k++) {
                    final long start = System.currentTimeMillis();
                    for (int i = 0; i < nb; i++) {
                        client.send("hiboux", "0", new HashMap<String, String>() {{
                            put("action", "tuer");
                            put("cible", "Caporal");
                            put("caracteristique", "Jean du jardin marchait très droit avec ses amis les clown");
                        }}).callback(callback -> {
                            System.out.println(callback.get("message"));
                        });
                    }
                    moyennes.add((int) (System.currentTimeMillis()-start));
                    Thread.sleep(500);
                }


                System.out.println("Pour " + nb + " requêtes : " + Arrays.stream(moyennes.toArray(new Integer[]{})).mapToInt(Integer::intValue).average());




            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();


        new Thread(() -> { // client TWO
            try {
                SocketClient client = new SocketClient("hiboux", "127.0.0.1", 25565);


                    client.listen("0", map -> {
                        if (map.containsKey("action")) {
                            if (map.get("action").equals("tuer")) {
                                // commit homicid

                                client.reply(map, new HashMap<String, String>() {{
                                    put("message", map.get("cible") + " sera tué au coucher du soleil");
                                }});

                            }
                        }
                    });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        Thread.sleep(10_000___000);




    }

}
