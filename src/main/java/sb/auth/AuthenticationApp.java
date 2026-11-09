package sb.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(proxyBeanMethods = false)
public class AuthenticationApp {

	 static void main(String[] args) {
		SpringApplication.run(AuthenticationApp.class, args);
	}


}