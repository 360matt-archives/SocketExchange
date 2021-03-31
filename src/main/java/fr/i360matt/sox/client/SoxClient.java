package fr.i360matt.sox.client;

import fr.i360matt.sox.client.exceptions.BadLoginException;
import fr.i360matt.sox.client.exceptions.CorruptedLoginException;
import fr.i360matt.sox.common.Request;
import fr.i360matt.sox.common.SendAndWait;
import fr.i360matt.sox.common.Session;
import fr.i360matt.sox.common.TemporaryEvent;
import fr.i360matt.sox.server.exceptions.LineNullException;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SoxClient implements Closeable {
    private final ExecutorService mainService = Executors.newSingleThreadExecutor();
    private ExecutorService sendService = Executors.newSingleThreadExecutor();
    private ExecutorService receiveService = Executors.newSingleThreadExecutor();

    private final TemporaryEvent temporaryEvent = new TemporaryEvent();
    private final Set<Consumer<Request>> events = new HashSet<>();
    private Consumer<Exception> onExept;


    private boolean isEnabled = true;
    private boolean inLoginPhase = true;
    private PrintWriter sender;
    private DataInputStream receiver;


    public SoxClient (final String host, final int port, final Session session) {
        mainService.submit(() -> {

            while (this.isEnabled) {

                try (
                        final Socket socket = new Socket(host, port);
                        final PrintWriter sender = new PrintWriter(socket.getOutputStream());
                        final DataInputStream receiver = new DataInputStream(socket.getInputStream());
                ) {
                    if (this.sendService.isShutdown()) {
                        this.sendService = Executors.newSingleThreadExecutor();
                        this.receiveService = Executors.newSingleThreadExecutor();
                    }

                    pauseServices();

                    this.sender = sender;
                    this.receiver = receiver;

                    sender.println(session.name);
                    sender.println(session.group);
                    sender.println(session.token);
                    sender.flush();

                    if (waitLogin()) {
                        while (this.isEnabled) {
                            catchRequest();
                        }
                    }
                    isEnabled = false;

                } catch (final IOException | LineNullException | CorruptedLoginException | BadLoginException e) {
                    if (this.onExept != null)
                        this.onExept.accept(e);
                    else
                        e.printStackTrace();
                } finally {
                    this.mainService.shutdown();
                    this.sendService.shutdown();
                    this.receiveService.shutdown();
                }

                if (this.isEnabled) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        });
    }

    public final void onException (final Consumer<Exception> consumer) {
        this.onExept = consumer;
    }

    private boolean waitLogin () throws LineNullException, IOException, BadLoginException, CorruptedLoginException {
        final Request request = getFutureRequest();

        if (request.action.equals("auth")) {
            if (request.content.equals("ok")) {
                this.inLoginPhase = false;
                return true;
            } else if (request.content.equals("bad")) {
                this.isEnabled = false;
                throw new BadLoginException();
            }
        }
        this.isEnabled = false;
        throw new CorruptedLoginException();
    }

    private void pauseServices () {
        this.inLoginPhase = true;
        sendService.submit(() -> {
            while (this.isEnabled && this.inLoginPhase) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) { }
            }
        });
        receiveService.submit(() -> {
            while (this.isEnabled && this.inLoginPhase) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) { }
            }
        });
    }

    private void catchRequest () throws LineNullException, IOException {
        final Request request = getFutureRequest();

        if (!this.getTemporaryEvent().call(request)) {
            events.forEach(event -> {
                event.accept(request);
            });
        }
    }

    private Request getFutureRequest () throws LineNullException, IOException {
        final Request request = new Request();

        request.action = getNextLine();
        if (request.action.equals("t") || request.action.equals("r")) {
            request.sender = getNextLine();
            request.idReply = getNextLine();
            request.channel = getNextLine();
        }
        request.content = getNextLine();

        return request;
    }

    public final SendAndWait sendRequest (final Request request, final boolean callback) {
        this.getTemporaryEvent().generateID(request);
        sendService.submit(() -> {
            sender.println(request.action);
            if (request.action.equals("t") || request.action.equals("r")) {
                sender.println(request.recipient);
                sender.println(request.idReply);
                sender.println(request.channel);
            }
            sender.println(request.content);
            sender.flush();
        });
        return (callback) ? new SendAndWait(this.getTemporaryEvent(), request) : null;
    }

    public final SendAndWait sendRequest (final Request request) {
        return sendRequest(request, true);
    }

    protected final String getNextLine () throws LineNullException, IOException {
        final String res;
        if ((res=receiver.readLine()) == null)
            throw new LineNullException();
        return res;
    }

    public final TemporaryEvent getTemporaryEvent () {
        return temporaryEvent;
    }

    public final void addEvent (final Consumer<Request> request) {
        events.add(request);
    }

    public final void removeEvent (final Consumer<Request> request) {
        events.remove(request);
    }


    @Override
    public final void close () throws IOException {
        this.mainService.shutdown();
        this.sendService.shutdown();
        this.receiveService.shutdown();
        this.isEnabled = false;
    }
}
