package com.afterlifearchive.medmanager

data class AppConfig(
    val apiBaseUrl: String,
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val emailConfirmationRedirectUrl: String,
) {
    val hasSupabaseConfiguration: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

    companion object {
        fun fromBuildConfig() = AppConfig(
            apiBaseUrl = BuildConfig.API_BASE_URL.trailingSlash(),
            supabaseUrl = BuildConfig.SUPABASE_URL.trailingSlash(),
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            emailConfirmationRedirectUrl = BuildConfig.EMAIL_CONFIRMATION_REDIRECT_URL,
        )
    }
}

private fun String.trailingSlash() = if (isEmpty() || endsWith('/')) this else "$this/"
