package com.yuuverne.smssender

import com.sun.mail.smtp.SMTPSendFailedException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.mail.Address
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.internet.AddressException

class MailRetryPolicyTest {

    @Test
    fun networkFailuresAreRetryable() {
        assertTrue(MailRetryPolicy.shouldRetry(UnknownHostException("offline")))
        assertTrue(MailRetryPolicy.shouldRetry(SocketTimeoutException("timeout")))
    }

    @Test
    fun temporarySmtpFailuresAreRetryable() {
        assertTrue(MailRetryPolicy.shouldRetry(smtpFailure(421)))
        assertTrue(MailRetryPolicy.shouldRetry(MessagingException("send failed", smtpFailure(451))))
    }

    @Test
    fun permanentSmtpFailuresAreNotRetryable() {
        assertFalse(MailRetryPolicy.shouldRetry(smtpFailure(550)))
        assertFalse(MailRetryPolicy.shouldRetry(MessagingException("send failed", smtpFailure(553))))
    }

    @Test
    fun configurationAndAddressErrorsAreNotRetryable() {
        assertFalse(MailRetryPolicy.shouldRetry(AuthenticationFailedException("bad credentials")))
        assertFalse(MailRetryPolicy.shouldRetry(AddressException("invalid recipient")))
        assertFalse(MailRetryPolicy.shouldRetry(IllegalArgumentException("missing recipient")))
    }

    @Test
    fun nestedPermanentCauseIsNotRetryable() {
        val error = MessagingException("send failed", AuthenticationFailedException("bad credentials"))
        assertFalse(MailRetryPolicy.shouldRetry(error))
    }

    private fun smtpFailure(returnCode: Int): SMTPSendFailedException {
        return SMTPSendFailedException(
            "MAIL",
            returnCode,
            "SMTP failure",
            null,
            emptyArray<Address>(),
            emptyArray<Address>(),
            emptyArray<Address>()
        )
    }
}
