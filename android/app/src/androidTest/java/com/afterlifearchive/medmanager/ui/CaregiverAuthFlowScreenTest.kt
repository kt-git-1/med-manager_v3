package com.afterlifearchive.medmanager.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.afterlifearchive.medmanager.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterlifearchive.medmanager.data.auth.AuthService
import com.afterlifearchive.medmanager.data.auth.AuthSession
import com.afterlifearchive.medmanager.data.network.ApiClient
import com.afterlifearchive.medmanager.data.network.HttpResponse
import com.afterlifearchive.medmanager.data.session.SessionRepository
import com.afterlifearchive.medmanager.data.session.SessionStorage
import com.afterlifearchive.medmanager.ui.theme.MedicationAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred

class CaregiverAuthFlowScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginAndSignupDestinationsRenderCanonicalForms() {
        val repository = authRepository()
        render(repository)

        composeRule.onNodeWithText("ログイン").performClick()
        composeRule.onNodeWithText("家族ログイン").assertIsDisplayed()
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("戻る").performClick()
        composeRule.onNodeWithText("新規登録").performClick()
        composeRule.onNodeWithText("家族アカウント作成").assertIsDisplayed()
        composeRule.onNodeWithText("メールアドレス").assertIsDisplayed()
        composeRule.onNodeWithText("パスワード（6文字以上）").assertIsDisplayed()
        composeRule.onNodeWithText("パスワードをもう一度入力").assertIsDisplayed()
    }

    @Test
    fun signupValidationErrorIsRenderedFromTypedRepositoryState() {
        val repository = authRepository()
        render(repository)

        composeRule.onNodeWithText("新規登録").performClick()
        runBlocking { repository.signupCaregiver("invalid", "123456", "123456") }

        composeRule.onNodeWithText("メールアドレスの形式を確認してください。")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun confirmationRequiredAndResendCooldownRemainReachableAtTwoHundredPercentFontScale() {
        val repository = authRepository()
        render(repository, fontScale = 2f)

        composeRule.onNodeWithText("新規登録").performClick()
        composeRule.onNodeWithTag(AUTH_EMAIL_TAG).performScrollTo().performTextInput("care@example.com")
        composeRule.onNodeWithTag(AUTH_PASSWORD_TAG).performScrollTo().performTextInput("123456")
        composeRule.onNodeWithTag(AUTH_CONFIRMATION_TAG).performScrollTo().performTextInput("123456")
        composeRule.onNodeWithTag(AUTH_SUBMIT_TAG).performScrollTo().performClick()

        composeRule.onNodeWithText("確認メールを送信しました。", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("秒で再送", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        captureFixture("android-ui-005-caregiver-signup-confirmation-font-2.0.png")
    }

    @Test
    fun signupLoadingDisablesSubmitUntilAuthenticationCompletes() {
        val signupResult = CompletableDeferred<AuthSession>()
        val auth = object : AuthService {
            override suspend fun login(email: String, password: String) = error("unused")
            override suspend fun refresh(refreshToken: String) = error("unused")
            override suspend fun signup(email: String, password: String) = signupResult.await()
        }
        val repository = authRepository(auth)
        render(repository)

        composeRule.onNodeWithText("新規登録").performClick()
        composeRule.onNodeWithTag(AUTH_EMAIL_TAG).performScrollTo().performTextInput("care@example.com")
        composeRule.onNodeWithTag(AUTH_PASSWORD_TAG).performScrollTo().performTextInput("123456")
        composeRule.onNodeWithTag(AUTH_CONFIRMATION_TAG).performScrollTo().performTextInput("123456")
        composeRule.onNodeWithTag(AUTH_SUBMIT_TAG).performScrollTo().performClick()

        composeRule.waitUntil(5_000) { repository.state.value.loading }
        composeRule.onNodeWithTag(AUTH_SUBMIT_TAG).assertIsDisplayed().assertIsNotEnabled()
        signupResult.complete(AuthSession(null, null, null))
    }

    @Test
    fun leavingAndReenteringSignupDoesNotRestoreStaleConfirmationState() {
        val repository = authRepository()
        render(repository)

        composeRule.onNodeWithText("新規登録").performClick()
        composeRule.onNodeWithTag(AUTH_EMAIL_TAG).performTextInput("care@example.com")
        composeRule.onNodeWithTag(AUTH_PASSWORD_TAG).performTextInput("123456")
        composeRule.onNodeWithTag(AUTH_CONFIRMATION_TAG).performTextInput("123456")
        composeRule.onNodeWithTag(AUTH_SUBMIT_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("確認メールを送信しました。", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag(AUTH_FORM_LIST_TAG, useUnmergedTree = true).performScrollToIndex(0)
        composeRule.onNodeWithTag(AUTH_NAVIGATION_BACK_TAG, useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("新規登録").performClick()

        composeRule.onNodeWithText("確認メールを送信しました。", substring = true).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(false, repository.state.value.canResendConfirmation)
        }
    }

    @Test
    fun signupAndResendFailureCopyMatchesCurrentIos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = mapOf(
            R.string.session_error_email_not_confirmed to "メール確認が完了していません。受信メールを確認してください。",
            R.string.session_error_confirmation_email_failed to "確認メールを送信できませんでした。しばらくしてからもう一度お試しください。",
            R.string.session_error_email_already_registered to "このメールアドレスはすでに登録されています。ログインしてください。",
            R.string.session_error_password_too_short to "パスワードは6文字以上で入力してください。",
            R.string.session_error_invalid_email to "メールアドレスの形式を確認してください。",
            R.string.session_error_login_failed to "メールアドレスとパスワードを確認してください。",
            R.string.session_error_auth_forbidden to "このアカウントではログインできません。",
            R.string.session_error_auth_not_found to "ログイン機能に接続できません。しばらくしてからもう一度お試しください。",
            R.string.session_error_rate_limited to "ログイン試行が続いたため、少し時間をおいてからもう一度お試しください。",
            R.string.session_error_auth_unavailable to "ログイン機能が一時的に利用できません。しばらくしてからもう一度お試しください。",
            R.string.session_error_network to "通信に失敗しました。インターネット接続を確認して、もう一度お試しください。",
            R.string.session_error_confirmation_resend_rate_limited to "確認メールの送信上限に達しました。少し時間をおいてからもう一度お試しください。",
            R.string.session_error_confirmation_resend_failed to "確認メールの再送に失敗しました。しばらくしてからもう一度お試しください。",
        )

        expected.forEach { (resource, copy) -> assertEquals(copy, context.getString(resource)) }
    }

    private fun render(repository: SessionRepository, fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                val state by repository.state.collectAsStateWithLifecycle()
                MedicationAppTheme { CaregiverAuthFlow(state, repository) }
            }
        }
    }

    private fun captureFixture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.externalCacheDir ?: context.cacheDir, "screenshot-fixtures").apply { mkdirs() }
        val file = java.io.File(directory, name)
        val image = composeRule.onRoot().captureToImage()
        file.outputStream().use { stream ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        check(file.length() > 0)
    }
}

private fun authRepository(authOverride: AuthService? = null): SessionRepository {
    val storage = AuthFlowStorage()
    val auth = authOverride ?: object : AuthService {
        override suspend fun login(email: String, password: String) = AuthSession("access", "refresh", 3600)
        override suspend fun refresh(refreshToken: String) = AuthSession("access-2", "refresh-2", 3600)
        override suspend fun signup(email: String, password: String) = AuthSession(null, null, null)
        override suspend fun resendSignupConfirmation(email: String) = Unit
    }
    val api = ApiClient(
        baseUrl = "https://example.invalid",
        transport = { HttpResponse(500, "{}") },
    )
    return SessionRepository(storage, auth, api).also { it.restore() }
}

private class AuthFlowStorage : SessionStorage {
    override var mode: AppMode? = AppMode.CAREGIVER
    override var currentPatientId: String? = null
    private val secrets = mutableMapOf<String, String>()

    override fun getSecret(key: String): String? = secrets[key]

    override fun putSecret(key: String, value: String?) {
        if (value == null) secrets.remove(key) else secrets[key] = value
    }
}
