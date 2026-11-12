
package com.example.citcsattendancetracker

import com.google.firebase.firestore.SetOptions

object StudentProfileRepository {

    private val database
        get() = FirebaseManager.database

    fun saveProfile(
        profile: StudentProfile,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = FirebaseManager.auth.currentUser

        if (user == null) {
            onResult(false, "Please log in first.")
            return
        }

        val profileData = mapOf(
            "fullName" to profile.fullName,
            "studentNumber" to profile.studentNumber,
            "course" to profile.course,
            "section" to profile.section,
            "createdAt" to profile.createdAt
        )

        database.collection("students")
            .document(user.uid)
            .set(profileData, SetOptions.merge())
            .addOnSuccessListener {
                onResult(true, "Student profile saved successfully!")
            }
            .addOnFailureListener { error ->
                onResult(
                    false,
                    error.localizedMessage ?: "Unable to save profile."
                )
            }
    }

    fun getProfile(
        onResult: (StudentProfile?, String?) -> Unit
    ) {
        val user = FirebaseManager.auth.currentUser

        if (user == null) {
            onResult(null, "Please log in first.")
            return
        }

        database.collection("students")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profile = document.toObject(
                        StudentProfile::class.java
                    )
                    onResult(profile, null)
                } else {
                    onResult(null, null)
                }
            }
            .addOnFailureListener { error ->
                onResult(
                    null,
                    error.localizedMessage ?: "Unable to load profile."
                )
            }
    }
}
