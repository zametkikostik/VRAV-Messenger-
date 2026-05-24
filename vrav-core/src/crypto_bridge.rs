use anyhow::Result;
use x25519_dalek::{EphemeralSecret, StaticSecret, PublicKey};
use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce
};
use rand::{rngs::OsRng, RngCore};
use std::convert::TryInto;

/// Generates a local X25519 static/ephemeral keypair.
/// Returns (private_key, public_key) as Vec<u8> raw memory structures.
pub fn generate_keypair() -> (Vec<u8>, Vec<u8>) {
    let secret = EphemeralSecret::random_from_rng(OsRng);
    let public = PublicKey::from(&secret);
    (secret.to_bytes().to_vec(), public.as_bytes().to_vec())
}

/// Computes the Diffie-Hellman Shared Secret (X25519) using raw bytes.
/// Raw bytes are moved directly through RAM without any intermediate String encoding.
pub fn derive_shared_secret(priv_key: Vec<u8>, pub_key: Vec<u8>) -> Vec<u8> {
    let priv_array: [u8; 32] = match priv_key.try_into() {
        Ok(bytes) => bytes,
        Err(_) => return Vec::new(),
    };
    let pub_array: [u8; 32] = match pub_key.try_into() {
        Ok(bytes) => bytes,
        Err(_) => return Vec::new(),
    };

    let secret = StaticSecret::from(priv_array);
    let public = PublicKey::from(pub_array);
    let shared_secret = secret.diffie_hellman(&public);
    
    shared_secret.as_bytes().to_vec()
}

/// Encrypts an arbitrary message byte array using AES-GCM (256-bit key).
/// The secret is typically the derived shared secret.
/// Package output format is memory contiguous: [ 12-byte Nonce ] + [ Ciphertext ]
pub fn encrypt_message(secret: Vec<u8>, message: Vec<u8>) -> Vec<u8> {
    // Standard AES-256-GCM expects a 32-byte key
    let mut key_bytes = [0u8; 32];
    if secret.len() >= 32 {
        key_bytes.copy_from_slice(&secret[0..32]);
    } else {
        // Pad with zeros if less than 32 bytes (or handle error)
        key_bytes[..secret.len()].copy_from_slice(&secret);
    }

    let cipher = match Aes256Gcm::new_from_slice(&key_bytes) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };

    // Generate random 12-byte initialization vector (Nonce)
    let mut nonce_bytes = [0u8; 12];
    rand::thread_rng().fill_bytes(&mut nonce_bytes);
    let nonce = Nonce::from_slice(&nonce_bytes);

    // Perform symmetric encryption directly in RAM
    match cipher.encrypt(nonce, message.as_slice()) {
        Ok(ciphertext) => {
            // Memory efficient allocation: Nonce + Ciphertext (which includes the 16-byte authentication tag)
            let mut result = Vec::with_capacity(nonce_bytes.len() + ciphertext.len());
            result.extend_from_slice(&nonce_bytes);
            result.extend_from_slice(&ciphertext);
            result
        }
        Err(_) => Vec::new(),
    }
}

/// Decrypts an arbitrary message byte array using AES-GCM (256-bit key).
/// The secret is typically the derived shared secret.
/// Input format is memory contiguous: [ 12-byte Nonce ] + [ Ciphertext ]
pub fn decrypt_message(secret: Vec<u8>, payload: Vec<u8>) -> Vec<u8> {
    if payload.len() < 12 {
        return Vec::new();
    }
    let (nonce_bytes, ciphertext) = payload.split_at(12);

    let mut key_bytes = [0u8; 32];
    if secret.len() >= 32 {
        key_bytes.copy_from_slice(&secret[0..32]);
    } else {
        key_bytes[..secret.len()].copy_from_slice(&secret);
    }

    let cipher = match Aes256Gcm::new_from_slice(&key_bytes) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };

    let nonce = Nonce::from_slice(nonce_bytes);
    match cipher.decrypt(nonce, ciphertext) {
        Ok(plaintext) => plaintext,
        Err(_) => Vec::new(),
    }
}

