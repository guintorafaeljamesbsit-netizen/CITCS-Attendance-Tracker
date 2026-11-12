
package com.example.citcsattendancetracker

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseManager {

    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    val database: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }
}
