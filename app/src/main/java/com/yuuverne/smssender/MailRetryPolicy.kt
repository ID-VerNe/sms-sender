package com.yuuverne.smssender

import com.sun.mail.smtp.SMTPSendFailedException
import javax.mail.AuthenticationFailedException
import javax.mail.internet.AddressException

object MailRetryPolicy {

    fun shouldRetry(error: Throwable): Boolean {
        val causes = generateSequence(error as Throwable?) { it.cause }.toList()

        if (causes.any {
                it is AuthenticationFailedException ||
                    it is AddressException ||
                    it is IllegalArgumentException
            }
        ) {
            return false
        }

        val smtpError = causes.filterIsInstance<SMTPSendFailedException>().firstOrNull()
        if (smtpError != null) {
            val returnCode = smtpError.returnCode
            return returnCode !in 500..599
        }

        // Network, timeout and unknown transient failures should not drop the SMS.
        return true
    }
}
