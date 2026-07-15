# Phase 5: Categories Implementation (Enhanced)

This phase implements the category management system, including the data model, repository, view model, and UI for listing, adding, editing, and deleting categories.

**Update**: Adding support for editing existing categories and associating a custom color.

## User Review Required

- Categories are associated with a family.
- Default categories are protected from editing and deletion.
- **Custom Color**: Users can select from a predefined list of colors when creating/editing a category.

## Proposed Changes

### UI Layer

#### [dialog_add_edit_category.xml](file:///S:/Proyectos/FinanzApp/app/src/main/res/layout/dialog_add_edit_category.xml)
- Add a HorizontalScrollView or Flexbox-like container with color bubbles for selection.

#### [AddEditCategoryDialogFragment.java](file:///S:/Proyectos/FinanzApp/app/src/main/java/com/finanzapp/app/ui/categories/AddEditCategoryDialogFragment.java)
- Implement color selection logic.
- Ensure the selected color is persisted in Firestore.

#### [ManageCategoriesFragment.java](file:///S:/Proyectos/FinanzApp/app/src/main/java/com/finanzapp/app/ui/categories/ManageCategoriesFragment.java)
- Ensure the edit button correctly passes the existing color to the dialog.

### Data Layer

#### [PLAN_DESARROLLO.md](file:///S:/Proyectos/FinanzApp/PLAN_DESARROLLO.md)
- Update Phase 5 description to explicitly include editing and color selection.

## Verification Plan

### Manual Verification
1. **Edit Category**: Open a custom category, change its name and color, and verify it updates in the list.
2. **Color Selection**: Verify that the selected color bubble is highlighted and correctly applied to the category icon/background in the list.
3. **Immutability of Defaults**: Verify that default categories still cannot be edited or deleted.
