# Refactoring: Join Requests into Members Tab

I have refactored the application to manage join requests (code requests) directly from the "Members" tab, as requested. This eliminates the need for a separate "Join Requests" screen and centralizes all family access management.

## Changes Accomplished

### 1. Model Updates
- Updated [Invitation.java](file:///S:/Proyectos/FinanzApp/app/src/main/java/com/finanzapp/app/data/model/Invitation.java) to include `requesterName` and `requesterEmail` fields. This ensures that when a user requests to join by code, their details are captured even before they are approved as members.

### 2. Repository Improvements
- Modified [FamilyRepository.java](file:///S:/Proyectos/FinanzApp/app/src/main/java/com/finanzapp/app/data/repository/FamilyRepository.java) in the `joinByCode` method to populate the new requester fields using the authenticated user's profile.

### 3. UI Consolidation
- **Centralized Management**: [MemberListFragment.java](file:///S:/Proyectos/FinanzApp/app/src/main/java/com/finanzapp/app/ui/family/MemberListFragment.java) now fetches and displays three types of entries:
    - Active Members
    - Pending Email Invitations (sent by admins)
    - Pending Join Requests (initiated by users via code)
- **Enhanced Item Layout**: Updated [item_member.xml](file:///S:/Proyectos/FinanzApp/app/src/main/res/layout/item_member.xml) to show both name and email for every entry. It also now contains "Approve" and "Reject" buttons for join requests.
- **Smart Adapter**: [MemberAdapter.java](file:///S:/Proyectos/FinanzApp/app/src/main/java/com/finanzapp/app/ui/family/MemberAdapter.java) handles the conditional logic to show the appropriate actions based on the entry type.

### 4. Cleanup
- Removed the obsolete `ManageJoinRequestsFragment` and its associated layout and adapter.
- Cleaned up the navigation graph and bottom menu to remove the now-redundant "Requests" tab.

## Verification Summary
- Verified that [MemberListFragment.java](file:///S:/Proyectos/FinanzApp/app/src/main/java/com/finanzapp/app/ui/family/MemberListFragment.java) correctly combines data from different Firestore queries.
- Ensured that `MemberAdapter` correctly displays names and emails as requested.
- Confirmed that "Approve" and "Reject" actions are correctly wired to the `FamilyViewModel`.
- Validated that the project structure is cleaner and matches the updated [AGENTS.md](file:///S:/Proyectos/FinanzApp/AGENTS.md) and [PLAN_DESARROLLO.md](file:///S:/Proyectos/FinanzApp/PLAN_DESARROLLO.md).
