package sn.ucak.tpapirest.bootstrap;

/** Arrêt explicite de l'initialisation du premier administrateur. */
public class BootstrapException extends RuntimeException {

    public BootstrapException(String reason) {
        super("Initialisation du premier ADMIN interrompue : " + reason + ".");
    }
}
