package sn.ucak.tpapirest.exception;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import sn.ucak.tpapirest.dto.error.ApiError;

/**
 * Centralise la traduction des exceptions en réponses JSON {@link ApiError}.
 *
 * <p>Aucune réponse ne contient de mot de passe, de token, de hachage, de détail SQL
 * ni de trace technique : seuls des messages rédigés ici sont renvoyés.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String INVALID_DATA = "Données invalides";
    static final String BAD_CREDENTIALS = "Email ou mot de passe incorrect";

    // ----- 400 -----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.merge(fieldError.getField(), messageOf(fieldError), (first, second) -> first + " ; " + second);
        }
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.merge(error.getObjectName(), String.valueOf(error.getDefaultMessage()),
                        (first, second) -> first + " ; " + second));
        return respond(HttpStatus.BAD_REQUEST, INVALID_DATA, errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleInvalidParameters(HandlerMethodValidationException exception,
                                                     HttpServletRequest request) {
        Map<String, String> errors = new TreeMap<>();
        exception.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                errors.merge(result.getMethodParameter().getParameterName(), String.valueOf(error.getDefaultMessage()),
                        (first, second) -> first + " ; " + second)));
        return respond(HttpStatus.BAD_REQUEST, INVALID_DATA, errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                  HttpServletRequest request) {
        Map<String, String> errors = JsonErrorTranslator.fieldErrors(exception);
        String message = errors.isEmpty() ? JsonErrorTranslator.MALFORMED_BODY : INVALID_DATA;
        return respond(HttpStatus.BAD_REQUEST, message, errors, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                HttpServletRequest request) {
        String expected = exception.getRequiredType() != null
                && Number.class.isAssignableFrom(exception.getRequiredType())
                ? "un identifiant numérique entier est attendu"
                : "valeur de format incorrect";
        return respond(HttpStatus.BAD_REQUEST, "Paramètre invalide",
                Map.of(exception.getName(), "Valeur invalide : " + expected), request);
    }

    // ----- 401 / 403 -----

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException exception, HttpServletRequest request) {
        // Même message pour un email inconnu et un mauvais mot de passe.
        return respond(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS, Map.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "Authentification requise", Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "Accès refusé : rôle insuffisant", Map.of(), request);
    }

    // ----- 404 / 405 / 409 / 415 -----

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Ressource introuvable", Map.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                      HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED,
                "Méthode " + exception.getMethod() + " non prise en charge pour cette URI", Map.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException exception,
                                             HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Type de contenu non pris en charge : utilisez application/json", Map.of(), request);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    ResponseEntity<ApiError> handleEmailConflict(EmailAlreadyUsedException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, exception.getMessage(),
                Map.of("email", "Cet email est déjà utilisé"), request);
    }

    /**
     * Violation d'une autre contrainte de la base (le conflit d'email est identifié en amont,
     * dans le service, par le nom de sa contrainte). Le détail SQL n'est pas exposé.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception,
                                                 HttpServletRequest request) {
        log.warn("Violation de contrainte en base sur {} {}", request.getMethod(), request.getRequestURI());
        return respond(HttpStatus.CONFLICT, "La requête est en conflit avec les données existantes",
                Map.of(), request);
    }

    // ----- 500 -----

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Erreur inattendue sur {} {}", request.getMethod(), request.getRequestURI(), exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur", Map.of(), request);
    }

    private static String messageOf(FieldError fieldError) {
        if (fieldError.isBindingFailure()) {
            return "Valeur de format incorrect";
        }
        return String.valueOf(fieldError.getDefaultMessage());
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message, Map<String, String> errors,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), message, errors, request.getRequestURI()));
    }
}
