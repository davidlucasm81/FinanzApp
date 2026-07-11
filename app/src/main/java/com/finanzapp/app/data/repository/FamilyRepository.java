package com.finanzapp.app.data.repository;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FamilyRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public FamilyRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    public void createFamily(String name, String currencyCode, FamilyCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        String uid = currentUser.getUid();
        DocumentReference familyRef = db.collection(FirestorePaths.FAMILIES).document();
        String familyId = familyRef.getId();
        String inviteCode = generateInviteCode();

        Family family = new Family(
                familyId,
                name,
                currencyCode,
                inviteCode,
                uid,
                Timestamp.now()
        );

        String displayName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User";
        Member adminMember = new Member(
                uid,
                displayName,
                currentUser.getEmail(),
                "admin",
                "approved",
                Timestamp.now()
        );

        WriteBatch batch = db.batch();
        batch.set(familyRef, family);
        batch.set(db.collection(FirestorePaths.getMembersPath(familyId)).document(uid), adminMember);
        batch.update(db.collection(FirestorePaths.USERS).document(uid), "familyId", familyId);

        // Seed categories
        seedDefaultCategories(batch, familyId);

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new Result.Success<>(family));
            } else {
                callback.onResult(new Result.Error<>(task.getException()));
            }
        });
    }

    public void joinByCode(String code, JoinCallback callback) {
        String uid = auth.getUid();
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        db.collection(FirestorePaths.FAMILIES)
                .whereEqualTo("inviteCode", code)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        QueryDocumentSnapshot familyDoc = (QueryDocumentSnapshot) task.getResult().getDocuments().get(0);
                        String familyId = familyDoc.getId();
                        
                        // Create code_request invitation
                        DocumentReference inviteRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document();
                        com.finanzapp.app.data.model.Invitation invitation = new com.finanzapp.app.data.model.Invitation(
                                inviteRef.getId(),
                                "code_request",
                                null,
                                uid,
                                null,
                                "pending",
                                Timestamp.now()
                        );
                        
                        inviteRef.set(invitation)
                                .addOnSuccessListener(aVoid -> callback.onResult(new Result.Success<>(true)))
                                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
                    } else {
                        callback.onResult(new Result.Error<>(new Exception("Invalid invite code")));
                    }
                });
    }

    private void seedDefaultCategories(WriteBatch batch, String familyId) {
        CollectionReference categoriesRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.CATEGORIES);
        
        // Income categories
        String[] incomeCategories = {"Nómina", "Otros ingresos", "Ingresos extra / Freelance", "Alquileres (ingreso)", "Devoluciones / Reembolsos"};
        for (String name : incomeCategories) {
            DocumentReference ref = categoriesRef.document();
            Category cat = new Category(ref.getId(), name, "income", "ic_income", "#4CAF50", true, null);
            batch.set(ref, cat);
        }

        // Expense categories
        String[] expenseCategories = {
                "Hipoteca", "Reformas", "Servicios", "Internet", "Seguros", 
                "Supermercado", "Restaurantes", "Alcohol", "Transporte", "Salud", 
                "Ropa", "Educación", "Ocio", "Viajes", "Ahorros", 
                "Informática", "Libros", "Streaming", "Deporte", "Bebidas", 
                "Peluquería", "Regalos", "Hogar", "Misceláneo", "Impuestos", 
                "Comunidad", "Mascotas", "Donaciones"
        };
        for (String name : expenseCategories) {
            DocumentReference ref = categoriesRef.document();
            Category cat = new Category(ref.getId(), name, "expense", "ic_expense", "#F44336", true, null);
            batch.set(ref, cat);
        }
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public void getPendingJoinRequests(String familyId, RequestsCallback callback) {
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "code_request")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        callback.onResult(new Result.Error<>(error != null ? error : new Exception("Empty result")));
                        return;
                    }
                    List<Invitation> requests = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                        requests.add(doc.toObject(Invitation.class));
                    }
                    callback.onResult(new Result.Success<>(requests));
                });
    }

    public void approveJoinRequest(String familyId, Invitation invitation, ApproveCallback callback) {
        String adminUid = auth.getUid();
        if (adminUid == null) return;

        // We need to fetch the user data first to create the member doc
        db.collection(FirestorePaths.USERS).document(invitation.getRequestedByUid()).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        User user = task.getResult().toObject(User.class);
                        if (user == null) return;

                        WriteBatch batch = db.batch();

                        // 1. Update invitation
                        batch.update(db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document(invitation.getId()),
                                "status", "approved",
                                "resolvedAt", Timestamp.now(),
                                "resolvedByUid", adminUid);

                        // 2. Create member
                        Member member = new Member(
                                user.getUid(),
                                user.getDisplayName(),
                                user.getEmail(),
                                "member",
                                "approved",
                                Timestamp.now()
                        );
                        batch.set(db.collection(FirestorePaths.getMembersPath(familyId)).document(user.getUid()), member);

                        // 3. Update user
                        batch.update(db.collection(FirestorePaths.USERS).document(user.getUid()), "familyId", familyId);

                        batch.commit().addOnCompleteListener(task2 -> {
                            if (task2.isSuccessful()) {
                                callback.onResult(new Result.Success<>(true));
                            } else {
                                callback.onResult(new Result.Error<>(task2.getException()));
                            }
                        });
                    }
                });
    }

    public void rejectJoinRequest(String familyId, String invitationId, ApproveCallback callback) {
        String adminUid = auth.getUid();
        if (adminUid == null) return;

        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document(invitationId)
                .update("status", "rejected",
                        "resolvedAt", Timestamp.now(),
                        "resolvedByUid", adminUid)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(new Result.Success<>(true));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public void inviteByEmail(String familyId, String email, ApproveCallback callback) {
        String adminUid = auth.getUid();
        if (adminUid == null) return;

        DocumentReference inviteRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document();
        Invitation invitation = new Invitation(
                inviteRef.getId(),
                "email_invite",
                email,
                null,
                adminUid,
                "pending",
                Timestamp.now()
        );

        inviteRef.set(invitation)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(new Result.Success<>(true));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public void getMembers(String familyId, MembersCallback callback) {
        db.collection(FirestorePaths.getMembersPath(familyId))
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        callback.onResult(new Result.Error<>(error != null ? error : new Exception("Empty result")));
                        return;
                    }
                    List<Member> members = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                        members.add(doc.toObject(Member.class));
                    }
                    callback.onResult(new Result.Success<>(members));
                });
    }

    public void updateFamily(String familyId, String name, String currencyCode, ApproveCallback callback) {
        db.collection(FirestorePaths.FAMILIES).document(familyId)
                .update("name", name, "currencyCode", currencyCode)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(new Result.Success<>(true));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public void leaveFamily(String familyId, ApproveCallback callback) {
        String uid = auth.getUid();
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        db.collection(FirestorePaths.getMembersPath(familyId)).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<DocumentSnapshot> memberDocs = task.getResult().getDocuments();
                        if (memberDocs.size() <= 1) {
                            // Last member, delete family
                            deleteFamily(familyId, callback);
                        } else {
                            // More members exist
                            checkAndLeave(familyId, uid, memberDocs, callback);
                        }
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    private void checkAndLeave(String familyId, String uid, List<DocumentSnapshot> memberDocs, ApproveCallback callback) {
        DocumentSnapshot myDoc = null;
        DocumentSnapshot anotherAdmin = null;
        DocumentSnapshot anotherMember = null;

        for (DocumentSnapshot doc : memberDocs) {
            if (doc.getId().equals(uid)) {
                myDoc = doc;
            } else {
                Member m = doc.toObject(Member.class);
                if (m != null) {
                    if ("admin".equals(m.getRole())) {
                        anotherAdmin = doc;
                    } else {
                        anotherMember = doc;
                    }
                }
            }
        }

        WriteBatch batch = db.batch();
        batch.delete(db.collection(FirestorePaths.getMembersPath(familyId)).document(uid));
        batch.update(db.collection(FirestorePaths.USERS).document(uid), "familyId", null);

        if (myDoc != null && "admin".equals(myDoc.getString("role")) && anotherAdmin == null) {
            // I was the only admin, promote someone
            if (anotherMember != null) {
                batch.update(db.collection(FirestorePaths.getMembersPath(familyId)).document(anotherMember.getId()), "role", "admin");
            }
        }

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new Result.Success<>(true));
            } else {
                callback.onResult(new Result.Error<>(task.getException()));
            }
        });
    }

    private void deleteFamily(String familyId, ApproveCallback callback) {
        // Simple delete for now (only members and family doc)
        // In a real app, we should delete all subcollections (accounts, transactions, etc.)
        String uid = auth.getUid();
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }
        WriteBatch batch = db.batch();
        batch.delete(db.collection(FirestorePaths.getMembersPath(familyId)).document(uid));
        batch.delete(db.collection(FirestorePaths.FAMILIES).document(familyId));
        batch.update(db.collection(FirestorePaths.USERS).document(uid), "familyId", null);

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new Result.Success<>(true));
            } else {
                callback.onResult(new Result.Error<>(task.getException()));
            }
        });
    }

    public interface FamilyCallback {
        void onResult(Result<Family> result);
    }

    public interface JoinCallback {
        void onResult(Result<Boolean> result);
    }

    public interface RequestsCallback {
        void onResult(Result<List<Invitation>> result);
    }

    public interface ApproveCallback {
        void onResult(Result<Boolean> result);
    }

    public interface MembersCallback {
        void onResult(Result<List<Member>> result);
    }
}
