package com.finanzapp.app.util;

import androidx.lifecycle.LiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * A lifecycle-aware LiveData for Firestore queries or document references.
 * It automatically starts listening when observed and stops when no longer observed.
 */
@SuppressWarnings("unchecked")
public class FirestoreLiveData<T> extends LiveData<T> {
    private final Query query;
    private final DocumentReference ref;
    private final Class<?> type;
    private final boolean isList;
    private ListenerRegistration registration;

    public FirestoreLiveData(Query query, Class<?> type, boolean isList) {
        this.query = query;
        this.ref = null;
        this.type = type;
        this.isList = isList;
    }

    public FirestoreLiveData(DocumentReference ref, Class<?> type) {
        this.query = null;
        this.ref = ref;
        this.type = type;
        this.isList = false;
    }

    private final EventListener<QuerySnapshot> queryListener = new EventListener<QuerySnapshot>() {
        @Override
        public void onEvent(QuerySnapshot value, com.google.firebase.firestore.FirebaseFirestoreException error) {
            if (error != null) return;
            if (value != null && isList) {
                List<Object> list = new ArrayList<>();
                for (DocumentSnapshot doc : value) {
                    list.add(doc.toObject(type));
                }
                setValue((T) list);
            }
        }
    };

    private final EventListener<DocumentSnapshot> docListener = new EventListener<DocumentSnapshot>() {
        @Override
        public void onEvent(DocumentSnapshot value, com.google.firebase.firestore.FirebaseFirestoreException error) {
            if (error != null) return;
            if (value != null && !isList) {
                setValue((T) value.toObject(type));
            }
        }
    };

    @Override
    protected void onActive() {
        if (query != null) {
            registration = query.addSnapshotListener(queryListener);
        } else if (ref != null) {
            registration = ref.addSnapshotListener(docListener);
        }
    }

    @Override
    protected void onInactive() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
