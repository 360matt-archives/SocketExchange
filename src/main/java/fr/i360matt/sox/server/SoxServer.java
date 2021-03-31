package fr.i360matt.sox.server;

import fr.i360matt.sox.common.Request;
import fr.i360matt.sox.common.Session;
import fr.i360matt.sox.common.TemporaryEvent;
import fr.i360matt.sox.utils.Sha256;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class SoxServer implements Closeable {
    protected final ExecutorService clientService = Executors.newCachedThreadPool();
    protected final Set<BiConsumer<Request, ClientLogged>> events = new HashSet<>();


    private final String privateKey;
    private boolean isEnabled = true;
    private boolean authShowError = true;

    protected ServerSocket server;
    private final UserManager userManager = new UserManager();
    private final TemporaryEvent temporaryEvent = new TemporaryEvent();


    public SoxServer (final int port, final String privateKey) {
        this.privateKey = privateKey;

        clientService.submit(() -> {
            try {
                server = new ServerSocket(port);
                while (isEnabled && !server.isClosed()) {
                    new ClientLogged(this, server.accept());
                }
            } catch (final IOException ignored) { }
            finally {
                try {
                    server.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public final void close () {
        this.isEnabled = false;
        this.clientService.shutdown();
        try {
            this.server.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public final boolean isClosed () {
        return !isEnabled;
    }
    public final boolean isOpen () {
        return isEnabled;
    }

    public final ExecutorService getServices () {
        return clientService;
    }

    public final UserManager getUserManager () {
        return userManager;
    }

    public final TemporaryEvent getTemporaryEvent () {
        return temporaryEvent;
    }

    protected final boolean authShowError () {
        return this.authShowError;
    }

    public final void authShowError (final boolean status) {
        this.authShowError = status;
    }

    public final String getToken (final Session session) {
        return Sha256.hash(session.name + session.group + privateKey);
    }


    public final void addEvent (final BiConsumer<Request, ClientLogged> request) {
        events.add(request);
    }

    public final void removeEvent (final BiConsumer<Request, ClientLogged> request) {
        events.remove(request);
    }



}
