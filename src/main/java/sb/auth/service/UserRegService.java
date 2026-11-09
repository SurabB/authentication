package sb.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import sb.auth.config.AppProperties;
import sb.auth.dto.RegisterRequest;
import sb.auth.dto.UserVerification;
import sb.auth.enums.Role;
import sb.auth.exceptionclass.VerificationFailedException;
import sb.auth.repo.UserRepo;

import java.util.Locale;
import java.util.function.Supplier;

@Service
public class UserRegService {
    UserRepo userRepo;
    UserService userService;
    MailVerifService mailVerifService;
    AppProperties.Redis.RedisService redisProperties;

    public UserRegService(UserRepo userRepo, UserService userService, MailVerifService mailVerifService, AppProperties appProperties) {
        this.userRepo = userRepo;
        this.userService = userService;
        this.mailVerifService = mailVerifService;
        this.redisProperties = appProperties.redis().services().get("registration-verification");
    }

    public String sendVerificationCode(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        boolean userExist = userRepo.checkUserExistence(normalizedEmail);
        if (userExist) {
            return "If Email is not already verified, A verification code has been sent to your email.";
        }
        UserVerification userVerification = new UserVerification(email, "");
         mailVerifService.sendRegistrationVerificationCode(userVerification, redisProperties);
         return "If Email is not already verified, A verification code has been sent to your email.";

    }
    public String verifyRegistrationCode(RegisterRequest userToRegister, Role role) {
        UserVerification userVerification = new UserVerification(userToRegister.email(), userToRegister.token());
      Supplier<String> onSuccessfulVerification=()->{
          try{
              userService.createUser(userToRegister,role);
              return "User Registered Successfully";
      } catch (DataIntegrityViolationException e) {
            throw new VerificationFailedException("User already registered.");
        }

      };
        return (String) mailVerifService.verifyAndRegisterUser(redisProperties,onSuccessfulVerification,userVerification);

    }
}
