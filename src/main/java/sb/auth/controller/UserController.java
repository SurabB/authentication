package sb.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import sb.auth.dto.RegisterRequest;
import sb.auth.dto.UserResponse;
import sb.auth.enums.Role;
import sb.auth.exceptionclass.BlockedAccessException;
import sb.auth.exceptionclass.VerificationFailedException;
import sb.auth.service.UserRegService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    private final UserRegService userRegService;



    @PostMapping("/sendRegCode")
    public ResponseEntity<String> sendRegistrationCode(@RequestBody String email ){
        try {
            String message = userRegService.sendVerificationCode(email);
            return ResponseEntity.ok(message);
        }
        catch (BlockedAccessException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }
    @PostMapping("/verifyAndRegisterUser")
    public ResponseEntity<String> verifyAndRegisterUser(@RequestBody RegisterRequest registerRequest){
        try {
            String msg = userRegService.verifyRegistrationCode(registerRequest, Role.USER);
            return ResponseEntity.status(HttpStatus.CREATED).body(msg);
        }
           catch (VerificationFailedException | BlockedAccessException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("username");
        String email = jwt.getClaimAsString("email");
        List<String> roles = jwt.getClaim("roles");

        if(username==null||email==null||roles==null){
            throw new BadCredentialsException("Missing username or email");
        }
        UserResponse user = new
                UserResponse(
                username,
                roles.getFirst(),
                email

        );

        return ResponseEntity.ok(user);
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/secure")
    public ResponseEntity<String> secure(){
        return ResponseEntity.ok("secure endpoint for testing");
    }

}

