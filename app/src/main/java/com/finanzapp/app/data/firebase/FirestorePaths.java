package com.finanzapp.app.data.firebase;

public class FirestorePaths {
    public static final String USERS = "users";
    public static final String FAMILIES = "families";
    public static final String MEMBERS = "members";
    public static final String INVITATIONS = "invitations";
    public static final String ACCOUNTS = "accounts";
    public static final String CATEGORIES = "categories";
    public static final String TRANSACTIONS = "transactions";
    public static final String MEMBERSHIPS = "memberships";

    public static String getUserPath(String uid) {
        return USERS + "/" + uid;
    }

    public static String getMembershipsPath(String uid) {
        return getUserPath(uid) + "/" + MEMBERSHIPS;
    }

    public static String getMembershipPath(String uid, String familyId) {
        return getMembershipsPath(uid) + "/" + familyId;
    }

    public static String getFamilyPath(String familyId) {
        return FAMILIES + "/" + familyId;
    }

    public static String getMembersPath(String familyId) {
        return getFamilyPath(familyId) + "/" + MEMBERS;
    }

    public static String getMemberPath(String familyId, String uid) {
        return getMembersPath(familyId) + "/" + uid;
    }

    public static String getAccountsPath(String familyId) {
        return getFamilyPath(familyId) + "/" + ACCOUNTS;
    }

    public static String getCategoriesPath(String familyId) {
        return getFamilyPath(familyId) + "/" + CATEGORIES;
    }

    public static String getCategoryPath(String familyId, String categoryId) {
        return getCategoriesPath(familyId) + "/" + categoryId;
    }

}
