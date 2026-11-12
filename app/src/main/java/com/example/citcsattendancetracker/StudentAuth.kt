
package com.example.citcsattendancetracker

import com.google.firebase.auth.FirebaseAuth

object StudentAuth {

    private val auth: FirebaseAuth
        get() = FirebaseManager.auth

    private fun getEmail(studentNumber: String): String {
        return "${studentNumber.trim()}@citcsattendance.app"
    }

    fun register(
        studentNumber: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (studentNumber.isBlank() || !studentNumber.all { it.isDigit() }) {
            onResult(false, "Please enter a valid student number.")
            return
        }

        if (password.length < 6) {
            onResult(false, "Password must be at least 6 characters.")
            return
        }

        auth.createUserWithEmailAndPassword(
            getEmail(studentNumber),
            password
        ).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(true, "Registration successful!")
            } else {
                onResult(
                    false,
                    task.exception?.localizedMessage
                        ?: "Registration failed. Please try again."
                )
            }
        }
    }

    fun login(
        studentNumber: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (studentNumber.isBlank() || !studentNumber.all { it.isDigit() }) {
            onResult(false, "Please enter a valid student number.")
            return
        }

        if (password.isBlank()) {
            onResult(false, "Please enter your password.")
            return
        }

        auth.signInWithEmailAndPassword(
            getEmail(studentNumber),
            password
        ).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(true, "Login successful!")
            } else {
                onResult(
                    false,
                    "Invalid student number or password."
                )
            }
        }
    }

    fun logout() {
        auth.signOut()
    }
}
