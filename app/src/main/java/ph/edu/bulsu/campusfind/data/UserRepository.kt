package ph.edu.bulsu.campusfind.data

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ph.edu.bulsu.campusfind.core.DbPaths
import ph.edu.bulsu.campusfind.model.UserProfile

class UserRepository(private val root: DatabaseReference) {

    private fun userRef(uid: String) = root.child(DbPaths.USERS).child(uid)

    /** Written once at sign-up. The rules make name write-once and role 'student' only. */
    suspend fun createProfile(uid: String, name: String, email: String) {
        val profile = mapOf(
            "name" to name,
            "email" to email,
            "role" to "student",
            "createdAt" to ServerValue.TIMESTAMP,
        )
        userRef(uid).setValue(profile).awaitWrite()
    }

    suspend fun getProfile(uid: String): UserProfile? =
        UserProfile.from(userRef(uid).readOnce())

    /** Sets or, with null, removes the profile picture (Base64 JPEG from ProfilePhoto). */
    suspend fun setPhoto(uid: String, photo: String?) {
        userRef(uid).child("photo").setValue(photo).awaitWrite()
    }

    fun observeProfile(uid: String): Flow<UserProfile?> =
        userRef(uid).valueEvents().map { UserProfile.from(it) }
}
