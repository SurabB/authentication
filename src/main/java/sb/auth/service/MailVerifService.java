package sb.auth.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import sb.auth.config.AppProperties;
import sb.auth.dto.UserVerification;
import sb.auth.exceptionclass.BlockedAccessException;
import sb.auth.exceptionclass.VerificationFailedException;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

@Slf4j
@Service
@AllArgsConstructor
public class MailVerifService {
    StringRedisTemplate stringRedisTemplate;
    MailService mailService;
    public void sendRegistrationVerificationCode(UserVerification userVerification,AppProperties.Redis.RedisService redisProps) {
        String normalizedEmail = userVerification.email().trim().toLowerCase(Locale.ROOT);
        String blockedKey = redisProps.blockedStorage() + normalizedEmail;

        if (stringRedisTemplate.hasKey(blockedKey)) {
            throw new BlockedAccessException(
                    "You have been blocked for registration and verification for %s hrs.".formatted(redisProps.blockTimeInMins()/60)
            );
        }

        var limits = redisProps.limits();
        String tokenStorageKey = redisProps.tokenStorage() + normalizedEmail;
        String limitStorageKey = redisProps.limitStorage() + normalizedEmail;
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();


        Long newSendCount = hashOps.increment(limitStorageKey, "sendCount", 1);
        log.info("email={} limitStorageKey={} newSendCount={} limit={}",
                normalizedEmail, limitStorageKey, newSendCount, limits.mailSentLimit());
        Long ttl = stringRedisTemplate.getExpire(limitStorageKey);
        log.info("redisProps={}", redisProps);

        if (ttl == null || ttl < 0) {
            stringRedisTemplate.expire(limitStorageKey, Duration.ofMinutes(redisProps.blockTimeInMins()));
        }

        if (newSendCount > limits.mailSentLimit()) {
            stringRedisTemplate.opsForValue().set(blockedKey, "true", Duration.ofMinutes(redisProps.blockTimeInMins()));
            stringRedisTemplate.delete(limitStorageKey);
            stringRedisTemplate.delete(tokenStorageKey);
            throw new BlockedAccessException("You have reached maximum number of attempts.");
        }

        String unhashedToken = TokenGeneration.generateOtp(6);
        String hashedToken = TokenGeneration.sha256(unhashedToken);

        hashOps.put(tokenStorageKey, "tokenHash", hashedToken);
        stringRedisTemplate.expire(tokenStorageKey, Duration.ofMinutes(15));

        mailService.sendMail(userVerification.email(), unhashedToken, redisProps.serviceMsg());
    }
    public Object verifyAndRegisterUser(AppProperties.Redis.RedisService redisProps, Supplier<?> onSuccessfulVerification, UserVerification userVerification) {
        String normalizedEmail = userVerification.email().trim().toLowerCase(Locale.ROOT);
        String blockedKey = redisProps.blockedStorage() + normalizedEmail;
        String limitStorageKey = redisProps.limitStorage() + normalizedEmail;
        String tokenStorageKey = redisProps.tokenStorage() + normalizedEmail;
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();

        if (!stringRedisTemplate.hasKey(tokenStorageKey) || !stringRedisTemplate.hasKey(limitStorageKey)) {
            throw new VerificationFailedException("Token is invalid for provided mail or expired.");
        }

        if (stringRedisTemplate.hasKey(blockedKey)) {
            throw new BlockedAccessException(
                    "You have been blocked for registration and verification for 12 hours."
            );
        }

        var limits = redisProps.limits();

        String attemptCount = hashOps.get(limitStorageKey, "attemptCount");
        int count = attemptCount == null ? 0 : Integer.parseInt(attemptCount);
        if (count > limits.serviceAttemptLimit()) {
            stringRedisTemplate.opsForValue().set(blockedKey, "true", Duration.ofMinutes(redisProps.blockTimeInMins()));
            stringRedisTemplate.delete(limitStorageKey);
            stringRedisTemplate.delete(tokenStorageKey);
            throw new BlockedAccessException("You have reached maximum number of attempts.");
        }

        String userProvidedToken = userVerification.token();
        String hashedUserToken = TokenGeneration.sha256(userProvidedToken);

        String tokenHash = hashOps.get(tokenStorageKey, "tokenHash");
        if (!hashedUserToken.equals(tokenHash)) {
            // Atomic increment; also (re)apply TTL so the counter itself expires
            hashOps.increment(limitStorageKey, "attemptCount", 1);
            Long ttl = stringRedisTemplate.getExpire(limitStorageKey);
            if (ttl == null || ttl < 0) {
                stringRedisTemplate.expire(limitStorageKey, Duration.ofMinutes(redisProps.blockTimeInMins()));
            }
            throw new VerificationFailedException("Invalid token provided");
        }
        Object val = onSuccessfulVerification.get();


        stringRedisTemplate.delete(tokenStorageKey);
        return val;
    }

}
