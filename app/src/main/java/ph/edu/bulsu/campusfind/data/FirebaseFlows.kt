package ph.edu.bulsu.campusfind.data

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads a value once. Used instead of DatabaseReference.get(), which was seen to never complete
 * in the emulator test (Post item hung while reading the poster's profile). Times out so the
 * screen can say "No connection" rather than spin forever.
 */
suspend fun Query.readOnce(timeoutMs: Long = READ_TIMEOUT_MS): DataSnapshot =
    withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (continuation.isActive) continuation.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (continuation.isActive) continuation.resumeWithException(error.toException())
                }
            }
            addListenerForSingleValueEvent(listener)
            continuation.invokeOnCancellation { removeEventListener(listener) }
        }
    } ?: throw IOException("Database read timed out")

private const val READ_TIMEOUT_MS = 15_000L
private const val WRITE_TIMEOUT_MS = 20_000L

/**
 * Waits for a database write to be confirmed by the server. Offline, the SDK keeps the write
 * queued and would wait indefinitely; after the timeout the screen shows "No connection".
 * A multi-path update is still all-or-nothing on the server, whenever it lands.
 */
suspend fun Task<Void>.awaitWrite(timeoutMs: Long = WRITE_TIMEOUT_MS) {
    // await() on Task<Void> returns null on success, so return Unit to tell success from timeout.
    withTimeoutOrNull(timeoutMs) { await(); Unit } ?: throw IOException("Database write timed out")
}

/** Live value updates for a query. The flow fails if the rules deny the read. */
fun Query.valueEvents(): Flow<DataSnapshot> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            close(error.toException())
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}
