package com.example.groups

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Single guarded doorway to Firebase for the Groups feature.
 *
 * Firebase auto-initialises from the bundled google-services.json. If that
 * config is missing or does not match this app, [isAvailable] is false and
 * every entry point here returns null, so the rest of Runway keeps working
 * exactly as before and Groups simply shows its "unavailable" state.
 */
object GroupsFirebase {

    val isAvailable: Boolean by lazy {
        try {
            FirebaseApp.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun auth(): FirebaseAuth? {
        if (!isAvailable) return null
        return try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    fun firestore(): FirebaseFirestore? {
        if (!isAvailable) return null
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Awaits a Play Services [Task] without depending on
 * kotlinx-coroutines-play-services. Read `task.result` after this returns —
 * on failure this throws the task's exception.
 */
internal suspend fun Task<*>.awaitCompletion() {
    suspendCancellableCoroutine<Unit> { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                cont.resume(Unit)
            } else {
                cont.resumeWithException(
                    task.exception ?: RuntimeException("Firebase task failed")
                )
            }
        }
    }
}
