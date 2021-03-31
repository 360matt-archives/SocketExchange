package fr.i360matt.sox.client.exceptions;

public class CorruptedLoginException extends Exception {
    public CorruptedLoginException () {
        super("an unknown authentication error has occurred");
    }
}
