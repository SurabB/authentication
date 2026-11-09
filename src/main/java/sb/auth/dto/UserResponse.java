package sb.auth.dto;

public record UserResponse(String username, String authorities,String email) {
}
