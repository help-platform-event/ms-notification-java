package com.maxime.help.msnotification.application.service;

import com.maxime.help.msnotification.domain.model.EmailRequest;
import com.maxime.help.msnotification.domain.port.out.EmailSender;
import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers queued emails. A failure propagates, so the messaging layer retries later; a request
 * already delivered is skipped. At-least-once: a crash between sending and recording the delivery
 * can still send the same email twice, which is acceptable for notifications.
 */
@Service
public class EmailDeliveryService {

    private final EmailSender emailSender;
    private final ProcessedEventRepository processedEventRepository;

    EmailDeliveryService(EmailSender emailSender, ProcessedEventRepository processedEventRepository) {
        this.emailSender = emailSender;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void deliver(EmailRequest request) {
        if (processedEventRepository.isProcessed(request.requestId())) {
            return;
        }
        emailSender.send(request);
        processedEventRepository.markProcessed(request.requestId());
    }
}
