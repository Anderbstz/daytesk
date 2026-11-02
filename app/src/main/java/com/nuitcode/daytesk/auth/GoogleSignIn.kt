package com.nuitcode.daytesk.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleSignIn {
    /**
     * Shows Google's account picker and exchanges the result for a session.
     *
     * [activityContext] must be an Activity: the picker is a UI over it.
     *
     * @return the session, or `null` when the user closed the picker.
     */
    suspend fun signIn(activityContext: Context): Result<AuthApi.LoginResult>? {
        val clientId = AuthApi.googleClientId().getOrElse { return Result.failure(it) }
        if (clientId.isBlank()) {
            return Result.failure(IllegalStateException("El login con Google no está configurado."))
        }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (_: GetCredentialCancellationException) {
            return null
        } catch (failure: Exception) {
            return Result.failure(
                IllegalStateException("No se pudo abrir el login de Google.", failure),
            )
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return Result.failure(IllegalStateException("Google devolvió una credencial inesperada."))
        }
        return AuthApi.loginWithGoogle(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    }
}
