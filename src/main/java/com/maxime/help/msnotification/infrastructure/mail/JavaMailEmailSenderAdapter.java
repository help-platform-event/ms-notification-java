package com.maxime.help.msnotification.infrastructure.mail;

import com.maxime.help.msnotification.domain.model.EmailRequest;
import com.maxime.help.msnotification.domain.port.out.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends emails over SMTP (Mailpit in dev). Plain text for now. */
@Component
class JavaMailEmailSenderAdapter implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    JavaMailEmailSenderAdapter(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.to());
        message.setSubject(request.subject());
        message.setText(request.body());
        mailSender.send(message);
    }
}
