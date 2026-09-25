package com.maxime.help.msnotification.domain.port.out;

import com.maxime.help.msnotification.domain.model.EmailRequest;

/**
 * Queues an email for delivery instead of sending it inline: the delivery then gets its own
 * retries, without re-running (or blocking) whatever decided to send it.
 */
public interface EmailRequestPublisher {

    void publish(EmailRequest request);
}
