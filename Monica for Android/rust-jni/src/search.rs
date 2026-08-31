#![forbid(unsafe_code)]

/// Normalized, reusable password-list search query.
///
/// The Android boundary only passes display metadata into this module. Secret
/// fields and ciphertext never cross the JNI boundary for list filtering.
pub(crate) struct SearchQuery {
    normalized: String,
    ascii: bool,
}

impl SearchQuery {
    pub(crate) fn new(query: &str) -> Self {
        let normalized = query.trim().to_lowercase();
        let ascii = normalized.is_ascii();
        Self { normalized, ascii }
    }

    pub(crate) fn is_empty(&self) -> bool {
        self.normalized.is_empty()
    }

    pub(crate) fn matches_fields(&self, fields: &[&str]) -> bool {
        self.is_empty() || fields.iter().any(|value| self.matches_value(value))
    }

    pub(crate) fn matches_value(&self, value: &str) -> bool {
        if self.is_empty() {
            return true;
        }
        if self.ascii {
            let query = self.normalized.as_bytes();
            return value
                .as_bytes()
                .windows(query.len())
                .any(|window| window.eq_ignore_ascii_case(query));
        }

        value.to_lowercase().contains(&self.normalized)
    }
}

#[cfg(test)]
mod tests {
    use super::SearchQuery;

    #[test]
    fn blank_query_matches_without_metadata_work() {
        let query = SearchQuery::new("   ");
        assert!(query.is_empty());
        assert!(query.matches_fields(&[]));
    }

    #[test]
    fn ascii_query_is_case_insensitive() {
        let query = SearchQuery::new("GITHUB");
        assert!(query.matches_fields(&["GitHub", "octocat"]));
        assert!(!query.matches_fields(&["example.cn", "bank"]));
    }

    #[test]
    fn ascii_query_matches_inside_unicode_text() {
        let query = SearchQuery::new("MUN");
        assert!(query.matches_fields(&["账号 MUN-01 / München"]));
    }

    #[test]
    fn unicode_query_uses_lowercase_fallback() {
        let query = SearchQuery::new("münchen");
        assert!(query.matches_fields(&["MÜNCHEN"]));
        assert!(!query.matches_fields(&["Berlin"]));
    }
}
