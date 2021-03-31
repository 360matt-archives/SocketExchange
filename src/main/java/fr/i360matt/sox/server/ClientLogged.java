package fr.i360matt.sox.server;

import fr.i360matt.sox.common.Request;
import fr.i360matt.sox.common.SendAndWait;
import fr.i360matt.sox.common.Session;
import fr.i360matt.sox.server.exceptions.LineNullException;

import java.io.*;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientLogged implements Closeable {
    private final ExecutorService sendService = Executors.newSingleThreadExecutor();

    private boolean isEnabled = true;
    public final SoxServer server;
    public Session session;

    private PrintWriter sender;
    private DataInputStream receiver;

    public ClientLogged (final SoxServer server, final Socket socket) {
        this.server = server;

        if (server.getServices().isShutdown())
            return;

        server.getServices().execute(() -> {
            try (
                    final PrintWriter sender = new PrintWriter(socket.getOutputStream());
                    final DataInputStream receiver = new DataInputStream(socket.getInputStream());
            ) {
                this.sender = sender;
                this.receiver = receiver;

                socket.setSoTimeout(21000); // timeout 1 seconds

                if ((this.isEnabled = waitLogin())) { // after the login, true if successfull
                    socket.setSoTimeout(6*3600*1000); // switch timeout to 6 hours

                    server.getUserManager().addUser(this);


                    sendRequest(new Request() {{
                        action = "auth";
                        content = "ok";
                    }});

                    // start requests listening
                    while (server.isOpen() && isEnabled) {
                        catchRequest();
                    }
                } else if (server.authShowError()) {
                    sendRequest(new Request() {{
                        action = "auth";
                        content = "bad";
                    }});
                }
            } catch (final Exception ignored) { }
            finally {
                try {
                    server.getUserManager().removeUser(this);
                    socket.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean waitLogin () {
        try {
            session = new Session();
            if ((session.name = getNextLine()) == null)
                return false;
            if ((session.group = getNextLine()) == null)
                return false;
            final String token;
            if ((token = getNextLine()) == null)
                return false;
            return server.getToken(session).equals(token);
        } catch (final IOException | LineNullException ignored) {
            return false;
        }
    }

    private void catchRequest () throws LineNullException, IOException {
        final Request request = new Request();

        request.action = getNextLine();
        if (request.action.equals("t") || request.action.equals("r")) {
            request.recipient = getNextLine();
            request.idReply = getNextLine();
            request.channel = getNextLine();
        }
        request.content = getNextLine();


        if (request.action.equals("t") || request.action.equals("r")) {
            // distribution

            final Set<ClientLogged> clients = server.getUserManager().getUser(request.recipient);
            final Request res = new Request() {{
                this.action = request.action;
                this.recipient = "";
                this.idReply = request.idReply;
                this.channel = request.channel;
                this.content = request.content;
                this.sender = session.name;
            }};

            clients.forEach(clientLogged -> {
                clientLogged.sendRequest(res);
            });

            if (!request.action.equals("r")) {
                server.events.forEach(event -> {
                    event.accept(request, this);
                });
            }
        } else {
            server.getTemporaryEvent().call(request);
        }


    }

    public final SendAndWait sendRequest (final Request request, final boolean callback) {
        if (request.idReply == null)
            this.server.getTemporaryEvent().generateID(request);

        sendService.submit(() -> {
            sender.println(request.action);
            if (request.action.equals("t") || request.action.equals("r")) {
                sender.println(request.sender);
                sender.println(request.idReply);
                sender.println(request.channel);
            }
            sender.println(request.content);
            sender.flush();
        });
        return (callback) ? new SendAndWait(server.getTemporaryEvent(), request) : null;
    }

    public final SendAndWait sendRequest (final Request request) {
        return sendRequest(request, true);
    }



    private String getNextLine () throws LineNullException, IOException {
        final String res;
        if ((res=receiver.readLine()) == null)
            throw new LineNullException();
        return res;
    }

    public void close () {
        this.sendService.shutdownNow();
        this.isEnabled = false;
    }

    public boolean isClosed () {
        return server.isClosed() || !this.isEnabled;
    }

}