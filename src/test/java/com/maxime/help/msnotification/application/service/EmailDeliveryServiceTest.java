package com.maxime.help.msnotification.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxime.help.msnotification.domain.model.EmailRequest;
import com.maxime.help.msnotification.domain.port.out.EmailSender;
import com.maxime.help.msnotification.domain.port.out.ProcessedEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    private static final EmailRequest REQUEST =
            new EmailRequest(UUID.randomUUID(), UUID.randomUUID(), "alice@example.com", "Subject", "Body");

    @Mock private EmailSender emailSender;
    @Mock private ProcessedEventRepository processedEventRepository;

    @InjectMocks private EmailDeliveryService service;

    @Test
    void sendsThenRecordsTheDelivery() {
        service.deliver(REQUEST);

        verify(emailSender).send(REQUEST);
        verify(processedEventRepository).markProcessed(REQUEST.requestId());
    }

    @Test
    void anAlreadyDeliveredRequest_isNotSentAgain() {
        when(processedEventRepository.isProcessed(REQUEST.requestId())).thenReturn(true);

        service.deliver(REQUEST);

        verify(emailSender, never()).send(any());
    }

    @Test
    void aSendFailure_propagates_andLeavesTheRequestUndelivered() {
        doThrow(new IllegalStateException("SMTP down")).when(emailSender).send(REQUEST);

        assertThatThrownBy(() -> service.deliver(REQUEST)).isInstanceOf(IllegalStateException.class);

        verify(processedEventRepository, never()).markProcessed(any());
    }
}
