package sb.auth.dto;

public record PasswordResetRequest(String email, String newPassword,String token) {
}
