package takagi.ru.monica.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.keepass.KeePassFieldChange

class KeePassNativeEntryEditorModelTest {

    @Test
    fun `new draft starts with password style fields and encrypted password`() {
        val draft = newNativeEntryEditorDraft()

        assertEquals(
            listOf("Title", "UserName", "Password", "URL", "Notes"),
            draft.fields.map { it.name },
        )
        assertEquals(NativeEntryStandardSlot.TITLE, draft.standard(NativeEntryStandardSlot.TITLE)?.slot)
        assertTrue(draft.standard(NativeEntryStandardSlot.PASSWORD)?.protected == true)
        assertTrue(draft.customFields.isEmpty())
    }

    @Test
    fun `existing aliases use password form slots without renaming fields`() {
        val draft = buildNativeEntryEditorDraft(
            listOf(
                KeePassFieldChange("Name", "Example"),
                KeePassFieldChange("Login", "alice"),
                KeePassFieldChange("pwd", "secret", protected = true),
                KeePassFieldChange("URI", "https://example.com"),
                KeePassFieldChange("Comment", "memo"),
                KeePassFieldChange("Recovery code", "1234", protected = true),
            ),
        )

        assertEquals("Name", draft.standard(NativeEntryStandardSlot.TITLE)?.name)
        assertEquals("Login", draft.standard(NativeEntryStandardSlot.USERNAME)?.name)
        assertEquals("pwd", draft.standard(NativeEntryStandardSlot.PASSWORD)?.name)
        assertEquals("URI", draft.standard(NativeEntryStandardSlot.URL)?.name)
        assertEquals("Comment", draft.standard(NativeEntryStandardSlot.NOTES)?.name)
        assertEquals(listOf("Recovery code"), draft.customFields.map { it.name })
        assertTrue(draft.customFields.single().protected)
    }

    @Test
    fun `second field for the same semantic slot remains an editable custom field`() {
        val draft = buildNativeEntryEditorDraft(
            listOf(
                KeePassFieldChange("Title", "Example"),
                KeePassFieldChange("UserName", "alice"),
                KeePassFieldChange("Login", "secondary"),
            ),
        )

        assertEquals("UserName", draft.standard(NativeEntryStandardSlot.USERNAME)?.name)
        assertEquals(listOf("Login"), draft.customFields.map { it.name })
    }

    @Test
    fun `save plan preserves field order names values and protection`() {
        val draft = buildNativeEntryEditorDraft(
            listOf(
                KeePassFieldChange("Plugin A", "opaque"),
                KeePassFieldChange("Title", "Before"),
                KeePassFieldChange("Password", "old", protected = true),
                KeePassFieldChange("Plugin Secret", "hidden", protected = true),
            ),
        )
        val updated = draft.copy(
            fields = draft.fields.map { field ->
                if (field.slot == NativeEntryStandardSlot.TITLE) field.copy(value = "After") else field
            },
        )

        assertEquals(
            listOf(
                KeePassFieldChange("Plugin A", "opaque"),
                KeePassFieldChange("Title", "After"),
                KeePassFieldChange("Password", "old", protected = true),
                KeePassFieldChange("Plugin Secret", "hidden", protected = true),
            ),
            updated.toFieldChanges(),
        )
    }

    @Test
    fun `validation requires title and unique nonblank field names`() {
        val emptyDraft = newNativeEntryEditorDraft()
        val titleId = emptyDraft.standard(NativeEntryStandardSlot.TITLE)!!.id
        val draft = emptyDraft.copy(
            fields = emptyDraft.fields.map { if (it.id == titleId) it.copy(value = "Example") else it },
        )

        assertEquals(
            NativeEntryDraftError.TITLE_REQUIRED,
            validateNativeEntryEditorDraft(
                draft.copy(fields = draft.fields.map { if (it.id == titleId) it.copy(value = "") else it }),
            ),
        )
        assertEquals(
            NativeEntryDraftError.FIELD_NAME_REQUIRED,
            validateNativeEntryEditorDraft(
                draft.copy(fields = draft.fields + newNativeCustomField(draft.fields, name = "")),
            ),
        )
        assertEquals(
            NativeEntryDraftError.DUPLICATE_FIELD_NAME,
            validateNativeEntryEditorDraft(
                draft.copy(fields = draft.fields + newNativeCustomField(draft.fields, name = " title ")),
            ),
        )
        assertNull(validateNativeEntryEditorDraft(draft))
    }

    @Test
    fun `new custom field is plain and ordered after existing fields`() {
        val draft = newNativeEntryEditorDraft()
        val custom = newNativeCustomField(draft.fields, name = "Account ID")

        assertEquals("Account ID", custom.name)
        assertFalse(custom.protected)
        assertEquals(draft.fields.maxOf { it.order } + 1, custom.order)
        assertNull(custom.slot)
    }
}
