import fr.i360matt.sox.client.SoxClient;
import fr.i360matt.sox.common.Request;
import fr.i360matt.sox.common.Session;
import fr.i360matt.sox.server.SoxServer;


public class ServerTest {

    static long start;

    public static void main (String[] args) throws InterruptedException {

        final SoxServer server = new SoxServer(25565, "key");
        server.addEvent(((request, client) -> {
            System.out.println("Recu server: " + request.content);

            client.sendRequest(new Request() {{
                this.action = "r";
                this.recipient = "test";
                this.channel = "hey";
                this.content = "martin ;) cc golfire";
            }});
        }));
        // Start le server




        final Session session = new Session() {{
            this.name = "360matt";
            this.group = "admin";
            this.token = server.getToken(this); // un passe-partout
        }};

        System.out.println(server.getToken(new Session() {{
            this.name = "test";
            this.group = "truc";
        }}));



        Thread.sleep(2000);

        final SoxClient client = new SoxClient("127.0.0.1", 25565, session);
        client.sendRequest(new Request() {{
            this.action = "t";
            this.recipient = "360matt";
            this.channel = "hey";
            this.content = "miaou";
        }}).callback(2000000, (request -> {
            System.out.println("reponse: " + request.content);
        }));

    }

}
