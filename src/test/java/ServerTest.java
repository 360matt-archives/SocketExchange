import wtf.listenia.sockets.SocketClient;
import wtf.listenia.sockets.SocketServer;
import wtf.listenia.sockets.converter.SerializeMap;


import java.util.HashMap;

public class ServerTest {

    public static void main (String[] args) throws InterruptedException {

        new Thread(() -> { // server
            SocketServer srv = new SocketServer(2500);
        }).start();


        new Thread(() -> { // client ONE
            SocketClient client = new SocketClient("one", "127.0.0.1", 2500);

            client.listen("hello", map -> {
                System.out.println(SerializeMap.map2str(map));
            });

        }).start();


        new Thread(() -> { // client TWO

            try {
                SocketClient client = new SocketClient("two", "127.0.0.1", 2500);


                Thread.sleep(1___000);

                client.send("two", "hello", new HashMap<String, String>() {{
                    put("test", "truc");
                    put("jaaj", "kaak");
                }});

            } catch (Exception e) {
                e.printStackTrace();
            }





        }).start();


        Thread.sleep(10_000___000);

    }

}
