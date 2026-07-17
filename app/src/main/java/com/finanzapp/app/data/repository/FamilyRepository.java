package com.finanzapp.app.data.repository;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.CategoryColorPalette;
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
                "owner",
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
                        
                        FirebaseUser currentUser = auth.getCurrentUser();
                        String displayName = currentUser != null && currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Usuario";
                        String email = currentUser != null ? currentUser.getEmail() : "";

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
                        invitation.setRequesterName(displayName);
                        invitation.setRequesterEmail(email);
                        
                        inviteRef.set(invitation)
                                .addOnSuccessListener(aVoid -> callback.onResult(new Result.Success<>(true)))
                                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
                    } else {
                        callback.onResult(new Result.Error<>(new Exception("Invalid invite code")));
                    }
                });
    }

    public void joinByCodeWithInvitation(String code, JoinWithInvitationCallback callback) {
        String uid = auth.getUid();
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")), null, null);
            return;
        }

        db.collection(FirestorePaths.FAMILIES)
                .whereEqualTo("inviteCode", code)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        QueryDocumentSnapshot familyDoc = (QueryDocumentSnapshot) task.getResult().getDocuments().get(0);
                        String familyId = familyDoc.getId();
                        
                        FirebaseUser currentUser = auth.getCurrentUser();
                        String displayName = currentUser != null && currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Usuario";
                        String email = currentUser != null ? currentUser.getEmail() : "";

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
                        invitation.setRequesterName(displayName);
                        invitation.setRequesterEmail(email);
                        
                        inviteRef.set(invitation)
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("FamilyRepository", "Code request invitation created successfully. InvId: " + invitation.getId() + ", FamilyId: " + familyId);
                                    callback.onResult(new Result.Success<>(true), invitation, familyId);
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("FamilyRepository", "Error creating code request invitation", e);
                                    callback.onResult(new Result.Error<>(e), null, null);
                                });
                    } else {
                        callback.onResult(new Result.Error<>(new Exception("Invalid invite code")), null, null);
                    }
                });
    }

    private void seedDefaultCategories(WriteBatch batch, String familyId) {
        CollectionReference categoriesRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.CATEGORIES);
        
        // Income categories
        String[] incomeCategories = {"Nómina", "Otros ingresos"};
        for (String name : incomeCategories) {
            DocumentReference ref = categoriesRef.document();
            String color = CategoryColorPalette.getColorForCategory(name);
            Category cat = new Category(ref.getId(), name, "income", "ic_income", color, true, null);
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
            String color = CategoryColorPalette.getColorForCategory(name);
            Category cat = new Category(ref.getId(), name, "expense", "ic_expense", color, true, null);
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

    public void getPendingEmailInvitations(String familyId, RequestsCallback callback) {
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "email_invite")
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

        // NOTE: Security rules in this project restrict direct read/write of other users' documents
        // (each user may only read/write their own user doc). To avoid permission errors when an admin
        // approves a code request, we avoid reading/updating /users/{uid} here. Instead we use the
        // information stored on the Invitation (requesterName/requesterEmail) to create the Member
        // document under the family. The client-side user should update their own /users/{uid}.familyId
        // when they observe they have been added to the family (or the backend/cloud function can do it
        // with elevated privileges).

        WriteBatch batch = db.batch();

        // 1. Update invitation status
        batch.update(db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document(invitation.getId()),
                "status", "approved",
                "resolvedAt", Timestamp.now(),
                "resolvedByUid", adminUid);

        // 2. Create member using data available on the invitation
        String userUid = invitation.getRequestedByUid();
        String displayName = invitation.getRequesterName() != null && !invitation.getRequesterName().isEmpty() ? invitation.getRequesterName() : "Usuario";
        String email = invitation.getRequesterEmail() != null ? invitation.getRequesterEmail() : "";

        Member member = new Member(
                userUid,
                displayName,
                email,
                "member",
                "approved",
                Timestamp.now()
        );
        batch.set(db.collection(FirestorePaths.getMembersPath(familyId)).document(userUid), member);

        // Also update the user's document to set familyId. The security rules were updated to
        // allow an admin to update only the `familyId` field on a user's document, so this
        // targeted update is permitted and keeps client `users/{uid}.familyId` in sync.
        try {
            batch.update(db.collection(FirestorePaths.USERS).document(userUid), "familyId", familyId);
        } catch (Exception e) {
            // If update fails (for example user doc does not exist), we continue and let the
            // commit report the error back via the callback.
        }

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new Result.Success<>(true));
            } else {
                callback.onResult(new Result.Error<>(task.getException()));
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

        String normalizedEmail = email.toLowerCase().trim();
        DocumentReference inviteRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document();
        Invitation invitation = new Invitation(
                inviteRef.getId(),
                "email_invite",
                normalizedEmail,
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

    public void acceptInvitation(Invitation invitation, String familyId, ApproveCallback callback) {
        String uid = auth.getUid();
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        // Fetch current user doc for member info
        db.collection(FirestorePaths.USERS).document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                User user = task.getResult().toObject(User.class);
                if (user == null) return;

                WriteBatch batch = db.batch();

                // 1. Update invitation status
                batch.update(db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document(invitation.getId()),
                        "status", "accepted",
                        "resolvedAt", Timestamp.now(),
                        "resolvedByUid", uid);

                // 2. Create member
                Member member = new Member(
                        uid,
                        user.getDisplayName(),
                        user.getEmail(),
                        "member",
                        "approved",
                        Timestamp.now()
                );
                batch.set(db.collection(FirestorePaths.getMembersPath(familyId)).document(uid), member);

                // 3. Update user familyId
                batch.update(db.collection(FirestorePaths.USERS).document(uid), "familyId", familyId);

                batch.commit().addOnCompleteListener(batchTask -> {
                    if (batchTask.isSuccessful()) {
                        callback.onResult(new Result.Success<>(true));
                    } else {
                        callback.onResult(new Result.Error<>(batchTask.getException()));
                    }
                });
            } else {
                callback.onResult(new Result.Error<>(task.getException() != null ? task.getException() : new Exception("User doc not found")));
            }
        });
    }

    public void deleteInvitation(String familyId, String invitationId, ApproveCallback callback) {
        android.util.Log.d("FamilyRepository", "Attempting to delete invitation: " + invitationId + " from family: " + familyId);
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document(invitationId)
                .delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        android.util.Log.d("FamilyRepository", "Invitation deleted successfully: " + invitationId);
                        callback.onResult(new Result.Success<>(true));
                    } else {
                        android.util.Log.e("FamilyRepository", "Error deleting invitation: " + invitationId, task.getException());
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public void findInvitationByEmail(String email, InvitationByEmailCallback callback) {
        String normalizedEmail = email.toLowerCase().trim();
        android.util.Log.d("FamilyRepository", "Searching for invitation for: " + normalizedEmail);
        
        db.collectionGroup(FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "email_invite")
                .whereEqualTo("targetEmail", normalizedEmail)
                .whereEqualTo("status", "pending")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            QueryDocumentSnapshot doc = (QueryDocumentSnapshot) task.getResult().getDocuments().get(0);
                            Invitation invitation = doc.toObject(Invitation.class);
                            String familyId = doc.getReference().getParent().getParent().getId();
                            android.util.Log.d("FamilyRepository", "Invitation found! FamilyId: " + familyId);
                            callback.onResult(new Result.Success<>(invitation), familyId);
                        } else {
                            android.util.Log.d("FamilyRepository", "No invitation found for email: " + normalizedEmail);
                            callback.onResult(new Result.Error<>(new Exception("No invitation found")), null);
                        }
                    } else {
                        android.util.Log.e("FamilyRepository", "Error searching for invitation", task.getException());
                        callback.onResult(new Result.Error<>(task.getException() != null ? task.getException() : new Exception("Search failed")), null);
                    }
                });
    }

    public void findPendingCodeRequest(String uid, CodeRequestCallback callback) {
        android.util.Log.d("FamilyRepository", "Searching for pending code request for uid: " + uid);

        db.collectionGroup(FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "code_request")
                .whereEqualTo("requestedByUid", uid)
                .whereEqualTo("status", "pending")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            QueryDocumentSnapshot doc = (QueryDocumentSnapshot) task.getResult().getDocuments().get(0);
                            Invitation invitation = doc.toObject(Invitation.class);
                            String familyId = doc.getReference().getParent().getParent().getId();
                            android.util.Log.d("FamilyRepository", "Pending code request found! FamilyId: " + familyId);
                            callback.onResult(new Result.Success<>(invitation), familyId);
                        } else {
                            android.util.Log.d("FamilyRepository", "No pending code request found for uid: " + uid);
                            callback.onResult(new Result.Error<>(new Exception("No pending code request found")), null);
                        }
                    } else {
                        android.util.Log.e("FamilyRepository", "Error searching for code request", task.getException());
                        callback.onResult(new Result.Error<>(task.getException() != null ? task.getException() : new Exception("Search failed")), null);
                    }
                });
    }

    public interface InvitationByEmailCallback {
        void onResult(Result<Invitation> result, String familyId);
    }

    public interface CodeRequestCallback {
        void onResult(Result<Invitation> result, String familyId);
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

    public void getFamily(String familyId, FamilyCallback callback) {
        db.collection(FirestorePaths.FAMILIES).document(familyId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        Family family = task.getResult().toObject(Family.class);
                        callback.onResult(new Result.Success<>(family));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException() != null ? task.getException() : new Exception("Family not found")));
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
        List<Member> others = new ArrayList<>();

        for (DocumentSnapshot doc : memberDocs) {
            if (doc.getId().equals(uid)) {
                myDoc = doc;
            } else {
                Member m = doc.toObject(Member.class);
                if (m != null) {
                    m.setUid(doc.getId());
                    others.add(m);
                }
            }
        }

        if (myDoc == null) {
            callback.onResult(new Result.Error<>(new Exception("Member document not found")));
            return;
        }

        String myRole = myDoc.getString("role");
        WriteBatch batch = db.batch();
        batch.delete(db.collection(FirestorePaths.getMembersPath(familyId)).document(uid));
        batch.update(db.collection(FirestorePaths.USERS).document(uid), "familyId", null);

        if ("owner".equals(myRole)) {
            // Owner is leaving, transfer ownership
            Member successor = findSuccessor(others);
            if (successor != null) {
                batch.update(db.collection(FirestorePaths.getMembersPath(familyId)).document(successor.getUid()), "role", "owner");
            }
        } else if ("admin".equals(myRole)) {
            // If I was the only admin/owner left, promote someone? 
            // Actually, if owner exists, no problem. If no owner (shouldn't happen) and I'm last admin, promote.
            boolean ownerOrAdminExists = false;
            for (Member other : others) {
                if ("owner".equals(other.getRole()) || "admin".equals(other.getRole())) {
                    ownerOrAdminExists = true;
                    break;
                }
            }
            if (!ownerOrAdminExists && !others.isEmpty()) {
                Member successor = findSuccessor(others);
                if (successor != null) {
                    batch.update(db.collection(FirestorePaths.getMembersPath(familyId)).document(successor.getUid()), "role", "admin");
                }
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

    private Member findSuccessor(List<Member> members) {
        if (members.isEmpty()) return null;

        Member bestMatch = null;
        for (Member m : members) {
            if (bestMatch == null) {
                bestMatch = m;
                continue;
            }

            // Priority 1: owner (should not be in 'others' if owner is leaving, but for robustness)
            // Priority 2: admin
            // Priority 3: member
            
            int bestScore = getRoleScore(bestMatch.getRole());
            int currentScore = getRoleScore(m.getRole());

            if (currentScore > bestScore) {
                bestMatch = m;
            } else if (currentScore == bestScore) {
                // Same role, compare joinedAt
                if (m.getJoinedAt() != null && bestMatch.getJoinedAt() != null) {
                    if (m.getJoinedAt().compareTo(bestMatch.getJoinedAt()) < 0) {
                        bestMatch = m;
                    }
                }
            }
        }
        return bestMatch;
    }

    private int getRoleScore(String role) {
        if ("owner".equals(role)) return 3;
        if ("admin".equals(role)) return 2;
        return 1;
    }

    /**
     * Expulsa a un miembro de la familia: borra su documento en members/ y limpia
     * su users/{uid}.familyId para que vuelva al flujo de onboarding. Las reglas de
     * seguridad exigen que quien llama sea admin/owner de esa familia.
     */
    public void removeMember(String familyId, String memberUid, ApproveCallback callback) {
        WriteBatch batch = db.batch();
        batch.delete(db.collection(FirestorePaths.getMembersPath(familyId)).document(memberUid));
        batch.update(db.collection(FirestorePaths.USERS).document(memberUid), "familyId", null);

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new Result.Success<>(true));
            } else {
                callback.onResult(new Result.Error<>(task.getException()));
            }
        });
    }

    public void updateMemberRole(String familyId, String memberUid, String newRole, ApproveCallback callback) {
        db.collection(FirestorePaths.getMembersPath(familyId)).document(memberUid)
                .update("role", newRole)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(new Result.Success<>(true));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    private void deleteFamily(String familyId, ApproveCallback callback) {
        String uid = auth.getUid();
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        // To delete a family, we must delete all its subcollections.
        // Firestore doesn't support recursive delete from client, so we fetch and delete.
        String familyPath = FirestorePaths.getFamilyPath(familyId);
        
        List<String> subcollections = List.of(
                FirestorePaths.MEMBERS,
                FirestorePaths.INVITATIONS,
                FirestorePaths.CATEGORIES,
                FirestorePaths.ACCOUNTS,
                FirestorePaths.TRANSACTIONS
        );

        // Fetch all documents in all subcollections
        List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
        for (String sub : subcollections) {
            tasks.add(db.collection(familyPath + "/" + sub).get());
        }

        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks).addOnCompleteListener(allTasks -> {
            WriteBatch batch = db.batch();

            for (com.google.android.gms.tasks.Task<?> t : tasks) {
                if (t.isSuccessful()) {
                    com.google.firebase.firestore.QuerySnapshot snapshot = (com.google.firebase.firestore.QuerySnapshot) t.getResult();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        batch.delete(doc.getReference());
                    }
                }
            }

            // Delete family doc
            batch.delete(db.collection(FirestorePaths.FAMILIES).document(familyId));
            
            // Update user
            batch.update(db.collection(FirestorePaths.USERS).document(uid), "familyId", null);

            batch.commit().addOnCompleteListener(commitTask -> {
                if (commitTask.isSuccessful()) {
                    callback.onResult(new Result.Success<>(true));
                } else {
                    callback.onResult(new Result.Error<>(commitTask.getException()));
                }
            });
        });
    }

    public interface FamilyCallback {
        void onResult(Result<Family> result);
    }

    public interface JoinCallback {
        void onResult(Result<Boolean> result);
    }

    public interface JoinWithInvitationCallback {
        void onResult(Result<Boolean> result, Invitation invitation, String familyId);
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
