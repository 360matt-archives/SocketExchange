package fr.i360matt.sox.server;

import java.util.*;

public class UserManager {
    final Map<String, ClientLogged> single = new HashMap<>();
    final Map<String, Set<ClientLogged>> multiple = new HashMap<>();

    /**
     * allows to add the connected customer to his name.
     * if a client with the same name already exists,
     * we place the two clients with the same name in the multiple Map
     * @param client an instance of the client
     */
    protected final void addUser (final ClientLogged client) {
        final ClientLogged probably = this.single.get(client.session.name);
        if (probably == null)
            this.single.put(client.session.name, client);
        else {
            final Set<ClientLogged> newList = new HashSet<>();
            newList.add(probably);
            newList.add(client);
            this.multiple.put(client.session.name, newList);
            this.single.remove(client.session.name);
        }
    }

    protected final void removeUser (final String name) {
        this.single.remove(name);
        this.multiple.remove(name);
    }

    protected final void removeUser (final ClientLogged client) {
        if (this.single.containsValue(client))
            this.single.remove(client.session.name);
        else if (multiple.containsKey(client.session.name))
            this.multiple.get(client.session.name).remove(client);

    }

    public final void disconnect (final String name) {
        final ClientLogged probably;
        if ((probably = this.single.get(name)) != null)
            this.single.get(name).close();
        else {
            final Set<ClientLogged> probably2;
            if ((probably2 = this.multiple.get(name)) != null)
                probably2.forEach(ClientLogged::close);
        }
    }

    public final void disconnectAll () {
        this.single.values().forEach(ClientLogged::close);
        this.multiple.values().forEach(clients -> {
            clients.forEach(ClientLogged::close);
        });
    }

    public final Set<ClientLogged> getUser (final String name) {
        final ClientLogged probably;
        if ((probably = this.single.get(name)) != null)
            return new HashSet<>(Collections.singletonList(probably));
        else {
            final Set<ClientLogged> probably2;
            if ((probably2 = this.multiple.get(name)) != null)
                return probably2;
        }
        return new HashSet<>();
    }

    public final Set<ClientLogged> getAllUsers () {
        final Set<ClientLogged> res = new HashSet<>(single.values());
        this.multiple.values().forEach(res::addAll);
        return res;
    }

    public final boolean exist (final String name) {
        return this.single.containsKey(name) || this.multiple.containsKey(name);
    }

}
