#![forbid(unsafe_code)]

//! Native-side preparation for future crypto hot paths.
//! This module intentionally contains no plaintext secrets.

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SecureBufferStats {
    pub bytes: usize,
}

/// Describes an encrypted payload boundary without exposing content.
pub fn describe_ciphertext(bytes: &[u8]) -> SecureBufferStats {
    SecureBufferStats { bytes: bytes.len() }
}
