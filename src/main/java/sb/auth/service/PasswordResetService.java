package sb.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sb.auth.config.AppProperties;
import sb.auth.dto.AuthResponse;
import sb.auth.dto.EmailPasswordDto;
import sb.auth.dto.PasswordResetRequest;
import sb.auth.dto.UserVerification;
import sb.auth.repo.UserRepo;

import java.util.Locale;
import java.util.function.Supplier;

@Slf4j
@Service
public class PasswordResetService {
    UserRepo userRepo;
    UserService userService;
    MailVerifService mailVerifService;
    AppProperties.Redis.RedisService redisProperties;

    public PasswordResetService(UserRepo userRepo, UserService userService, MailVerifService mailVerifService, AppProperties appProperties) {
        this.userRepo = userRepo;
        this.userService = userService;
        this.mailVerifService = mailVerifService;
        this.redisProperties = appProperties.redis().services().get("password-reset");
    }

    public String sendVerificationCode(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        boolean userExist = userRepo.checkUserExistence(normalizedEmail);
        if (!userExist) {
            log.warn("{} not found", normalizedEmail);
            return "If Email is not already verified, A verification code has been sent to your email.";
        }
        UserVerification userVerification = new UserVerification(email, "");
         mailVerifService.sendRegistrationVerificationCode(userVerification, redisProperties);
         return "If Email is not already verified, A verification code has been sent to your email.";

    }
    public AuthResponse verifyRegistrationCode(PasswordResetRequest passwordResetRequest) {
        UserVerification userVerification = new UserVerification(passwordResetRequest.email(), passwordResetRequest.token());
        Supplier<?> onSuccessfulVerification=()->
          userService.resetPassword(new EmailPasswordDto(passwordResetRequest.email(), passwordResetRequest.newPassword()));


          return (AuthResponse)mailVerifService.verifyAndRegisterUser(redisProperties,onSuccessfulVerification,userVerification);

    }
}
