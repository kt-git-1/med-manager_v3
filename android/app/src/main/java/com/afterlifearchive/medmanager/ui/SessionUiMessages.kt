package com.afterlifearchive.medmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.afterlifearchive.medmanager.R
import com.afterlifearchive.medmanager.data.session.SessionUserMessage

@Composable
internal fun sessionUserMessageText(message: SessionUserMessage): String = when (message) {
    is SessionUserMessage.Raw -> message.value
    SessionUserMessage.InvalidEmail -> stringResource(R.string.session_error_invalid_email)
    SessionUserMessage.PasswordTooShort -> stringResource(R.string.session_error_password_too_short)
    SessionUserMessage.PasswordMismatch -> stringResource(R.string.session_error_password_mismatch)
    SessionUserMessage.ConfirmationSent -> stringResource(R.string.session_info_confirmation_sent)
    SessionUserMessage.ConfirmationResent -> stringResource(R.string.session_info_confirmation_resent)
    SessionUserMessage.Unexpected -> stringResource(R.string.session_error_unexpected)
    SessionUserMessage.MissingCredentials -> stringResource(R.string.session_error_missing_credentials)
    SessionUserMessage.InvalidInput -> stringResource(R.string.session_error_invalid_input)
    SessionUserMessage.MissingAuthConfiguration -> stringResource(R.string.session_error_missing_auth_configuration)
    SessionUserMessage.MissingAuthToken -> stringResource(R.string.session_error_missing_auth_token)
    SessionUserMessage.InvalidCredentials -> stringResource(R.string.session_error_invalid_credentials)
    SessionUserMessage.EmailNotConfirmed -> stringResource(R.string.session_error_email_not_confirmed)
    SessionUserMessage.ConfirmationEmailFailed -> stringResource(R.string.session_error_confirmation_email_failed)
    SessionUserMessage.EmailAlreadyRegistered -> stringResource(R.string.session_error_email_already_registered)
    SessionUserMessage.ConfirmationResendRateLimited -> stringResource(R.string.session_error_confirmation_resend_rate_limited)
    SessionUserMessage.ConfirmationResendFailed -> stringResource(R.string.session_error_confirmation_resend_failed)
    SessionUserMessage.RateLimited -> stringResource(R.string.session_error_rate_limited)
    SessionUserMessage.LoginFailed -> stringResource(R.string.session_error_login_failed)
    SessionUserMessage.Network -> stringResource(R.string.session_error_network)
}
