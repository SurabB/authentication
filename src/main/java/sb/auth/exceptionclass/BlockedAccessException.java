package sb.auth.exceptionclass;

public class BlockedAccessException extends RuntimeException {
    public BlockedAccessException(String message) {
        super(message);
    }
}
