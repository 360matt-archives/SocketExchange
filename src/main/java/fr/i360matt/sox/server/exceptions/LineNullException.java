package fr.i360matt.sox.server.exceptions;

public final class LineNullException extends Exception {
    public LineNullException () {
        super("La connexion a été interrompue");
    }
}
