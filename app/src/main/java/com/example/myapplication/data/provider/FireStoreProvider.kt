package com.example.myapplication.data.provider

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.data.entity.Note
import com.example.myapplication.data.entity.User
import com.example.myapplication.data.errors.NoAuthException
import com.example.myapplication.data.model.NoteResult
import com.github.ajalt.timberkt.Timber
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class FireStoreProvider(private val firebaseAuth: FirebaseAuth, private val store: FirebaseFirestore) : RemoteDataProvider {

    companion object {
        private const val NOTES_COLLECTION = "notes"
        private const val USERS_COLLECTION = "users"
    }

    private val currentUser
        get() = firebaseAuth.currentUser

    override fun getCurrentUser(): LiveData<User?> = MutableLiveData<User>().apply {
        value = currentUser?.let {
            User(it.displayName ?: "", it.email ?: "")
        }
    }

    private fun getUserNotesCollection() = currentUser?.let {
        store.collection(USERS_COLLECTION).document(it.uid).collection(NOTES_COLLECTION)
    } ?: throw NoAuthException()

    override fun subscribeToAllNotes(): LiveData<NoteResult> = MutableLiveData<NoteResult>().apply {
        try {
            getUserNotesCollection()
                    .addSnapshotListener { snapshot, e ->
                        value = e?.let { NoteResult.Error(it) }
                                ?: let {
                                    snapshot?.let {
                                        val notes = it.documents.map { it.toObject(Note::class.java) }
                                        NoteResult.Success(notes)
                                    }
                                }
                    }
        } catch (e: Throwable) {
            value = NoteResult.Error(e)
        }
    }

    override fun getNoteById(id: String): LiveData<NoteResult> = MutableLiveData<NoteResult>().apply {
        try {
            getUserNotesCollection()
                    .document(id).get()
                    .addOnSuccessListener { snapshot ->
                        value = NoteResult.Success(snapshot.toObject(Note::class.java))
                    }.addOnFailureListener {
                        value = NoteResult.Error(it)
                    }
        } catch (e: Throwable) {
            value = NoteResult.Error(e)
        }
    }

    override fun saveNote(note: Note): LiveData<NoteResult> = MutableLiveData<NoteResult>().apply {
        try {
            getUserNotesCollection()
                    .document(note.id)
                    .set(note).addOnSuccessListener {
                        Timber.d { "Note $note is saved" }
                        value = NoteResult.Success(note)
                    }.addOnFailureListener {
                        Timber.e(it) { "Error saving note $note with message: ${it.message}" }
                        value = NoteResult.Error(it)
                    }
        } catch (e: Throwable) {
            value = NoteResult.Error(e)
        }
    }

    override fun deleteNote(noteId: String) = MutableLiveData<NoteResult>().apply {
        getUserNotesCollection().document(noteId).delete()
                .addOnSuccessListener {
                    value = NoteResult.Success(null)
                }.addOnFailureListener {
                    value = NoteResult.Error(it)
                }
    }
}