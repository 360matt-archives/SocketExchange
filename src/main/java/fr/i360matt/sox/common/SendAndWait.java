package fr.i360matt.sox.common;

import java.util.function.Consumer;

public final class SendAndWait {
    final TemporaryEvent temporaryEvent;
    final Request request;
    public SendAndWait (final TemporaryEvent temporaryEvent, final Request request) {
        this.temporaryEvent = temporaryEvent;
        this.request = request;
    }

    public final void callback (final Consumer<Request> consumer) {
        temporaryEvent.register(request, consumer, 250);
    }

    public final void callback (final int expire, final Consumer<Request> consumer) {
        temporaryEvent.register(request, consumer, expire);
    }
}