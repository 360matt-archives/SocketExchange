package fr.i360matt.sox.common;

import fr.i360matt.sox.utils.ExpirableCache;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

public class TemporaryEvent implements Closeable {

    private final ExpirableCache<String, Consumer<Request>> events = new ExpirableCache<>(2); // 2ms

    public final void generateID (final Request sent) {
        sent.idReply = Integer.toString((int) (Math.random() * 99999999));
    }

    public final void register (final Request sent, final Consumer<Request> request, final int expire) {
        if (sent.recipient != null) {
            events.put(sent.recipient + "_" + sent.idReply, request, expire);
        }
    }

    public final boolean call (final Request request) {

        if (request.action.equals("r")) {
            final String id = request.sender + "_" + request.idReply;
            final Consumer<Request> consumer;
            if ((consumer = events.get(id)) != null) {
                events.remove(id);
                consumer.accept(request);
                return true;
            }
        }
        return false;
    }

    @Override
    public void close () throws IOException {
        events.quitMap();
    }
}
