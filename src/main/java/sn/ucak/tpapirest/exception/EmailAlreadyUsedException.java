package sn.ucak.tpapirest.exception;

/** L'email normalisé appartient déjà à un autre compte : traduite en 409. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Cet email est déjà utilisé par un autre compte");
    }
}
