package sb.auth.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;


@ConfigurationProperties(prefix="app")
public record AppProperties(
        Auth auth,
        Redis redis
) {
    public record Redis(Map<String, RedisService> services) {

        public record RedisService(
                String serviceName,
                String serviceMsg,
                String tokenStorage,
                Limits limits,
                String blockedStorage,
                String limitStorage,
                int blockTimeInMins
        ) {
            public RedisService{

                tokenStorage =serviceName+tokenStorage;
                limitStorage =serviceName+limitStorage;
                blockedStorage =serviceName+blockedStorage;

            }
            public record Limits(int mailSentLimit, int serviceAttemptLimit) { }
        }
    }
    public record Auth(

            ConfigAccessToken accessToken,
            ConfigRefreshToken refreshToken
    ){

        public record ConfigAccessToken(ConfigTime time){
            public record ConfigTime (int inMinutes){}
        }
        public record ConfigRefreshToken(ConfigTime time){
            public record ConfigTime (int inDays){}
        }

    }
}
