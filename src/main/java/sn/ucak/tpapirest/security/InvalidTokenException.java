package sn.ucak.tpapirest.security;

/** Token refusé : signature, algorithme, format ou date d'expiration incorrects. */
public class InvalidTokenException extends RuntimeException {

    public enum Reason {
        EXPIRED("Token expiré"),
        INVALID("Token invalide");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Reason reason;

    public InvalidTokenException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
