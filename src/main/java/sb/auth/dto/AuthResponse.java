package sb.auth.dto;


public record AuthResponse(String jwtToken, String refreshToken, UserDetails userDetails) {

    public record UserDetails(String username,String role,String email) {

    }

}
