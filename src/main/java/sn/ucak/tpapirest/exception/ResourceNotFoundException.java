package sn.ucak.tpapirest.exception;

/** Ressource (produit ou utilisateur) inexistante : traduite en 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException product(Long id) {
        return new ResourceNotFoundException("Produit introuvable (id " + id + ")");
    }

    public static ResourceNotFoundException user(Long id) {
        return new ResourceNotFoundException("Utilisateur introuvable (id " + id + ")");
    }
}
