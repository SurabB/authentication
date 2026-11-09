package sb.auth.service;

import lombok.AllArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MailService {
    private JavaMailSender javaMailSender;

    @Async
    public void sendMail(String email, String token,String msg) {
        String completeMsg = serviceBasedVerificationMessage(msg, token);
        String subject = "Sent from App";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(subject);
        message.setText(completeMsg);
        javaMailSender.send(message);
    }
    public String serviceBasedVerificationMessage(String msg, String token) {
      return msg+" token:"+token;
    }


}
