package com.maxime.help.msnotification.domain.port.out;

import com.maxime.help.msnotification.domain.model.Email;

public interface EmailSender {

    /** Sends the email now; throws if the mail server can't take it. */
    void send(Email email);
}
