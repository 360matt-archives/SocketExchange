package fr.i360matt.sox.client.exceptions;

public class BadLoginException extends Exception {
    public BadLoginException () {
        super("the token is not associated with the session data");
    }
}
