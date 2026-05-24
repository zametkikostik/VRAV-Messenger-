use anyhow::{Result, Context};
use serde::{Serialize, Deserialize};
use std::collections::HashMap;
use hkdf::Hkdf;
use sha2::Sha256;
use x25519_dalek::{StaticSecret, EphemeralSecret, PublicKey};
use rand::rngs::OsRng;

use pqcrypto_kyber::kyber768;

// -------------------------------------------------------------------------
// POST-QUANTUM KEY ENCAPSULATION MECHANISM (KEM) HELPER FUNCS
// -------------------------------------------------------------------------

/// Generates a post-quantum Kyber-768 keypair.
pub fn generate_pq_keypair() -> (Vec<u8>, Vec<u8>) {
    use pqcrypto_traits::kem::{PublicKey, SecretKey};
    let (pk, sk) = kyber768::keypair();
    (sk.as_bytes().to_vec(), pk.as_bytes().to_vec())
}

/// Encapsulates a shared secret against a post-quantum public key.
pub fn pq_encapsulate(remote_public_bytes: &[u8]) -> Result<(Vec<u8>, Vec<u8>)> {
    use pqcrypto_traits::kem::{PublicKey, SharedSecret, Ciphertext};
    let pk = kyber768::PublicKey::from_bytes(remote_public_bytes)
        .map_err(|_| anyhow::anyhow!("Failed to parse Kyber-768 public key"))?;
    let (ss, ct) = kyber768::encapsulate(&pk);
    Ok((ss.as_bytes().to_vec(), ct.as_bytes().to_vec()))
}

/// Decapsulates a post-quantum ciphertext using a post-quantum private key.
pub fn pq_decapsulate(ciphertext_bytes: &[u8], local_private_bytes: &[u8]) -> Result<Vec<u8>> {
    use pqcrypto_traits::kem::{Ciphertext, SecretKey, SharedSecret};
    let ct = kyber768::Ciphertext::from_bytes(ciphertext_bytes)
        .map_err(|_| anyhow::anyhow!("Failed to parse Kyber-768 ciphertext"))?;
    let sk = kyber768::SecretKey::from_bytes(local_private_bytes)
        .map_err(|_| anyhow::anyhow!("Failed to parse Kyber-768 private key"))?;
    let ss = kyber768::decapsulate(&ct, &sk);
    Ok(ss.as_bytes().to_vec())
}

// -------------------------------------------------------------------------
// RE-DESIGNED SOVEREIGN PACKET WITH HYBRID POST-QUANTUM PAYLOAD FIELDS
// -------------------------------------------------------------------------

/// Strongly typed container representing double ratchet serialized packets.
/// Delivered contiguous in-flight over libp2p nodes or local overlays.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct DoubleRatchetMessage {
    /// Local ephemeral custom public key of the sender for DH Ratchet transition tracking
    pub ephemeral_public: [u8; 32],
    /// Previous sent chain length
    pub pn: u32,
    /// Message index within the current chain
    pub n: u32,
    /// Memory contiguous cipher payload [12-byte Nonce] + [AES-GCM-256 encrypted message metadata]
    pub ciphertext: Vec<u8>,
    
    /// Sender's Kyber-768 public key (for the receiver's future steps)
    #[serde(default)]
    pub pq_public_key: Vec<u8>,
    
    /// Kyber-768 ciphertext (the "Kyber-конверт" containing the encapsulated secret for the receiver)
    #[serde(default)]
    pub pq_ciphertext: Vec<u8>,
}

/// Sovereign cryptographic state representing a secure conversation channel between two peers.
#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct RatchetState {
    /// Root Key (RK) derived from post-quantum / pre-shared KEM master secrets
    pub root_key: [u8; 32],
    
    /// Local ephemeral private key representation kept in RAM memory
    pub local_dh_private: [u8; 32],
    
    /// Local ephemeral public key representation
    pub local_dh_public: [u8; 32],
    
    /// Remote sender public key representation
    pub remote_dh_public: Option<[u8; 32]>,
    
    /// Chain Key for sending messages (CKS)
    pub sending_chain: Option<[u8; 32]>,
    
    /// Chain Key for receiving messages (CKR)
    pub receiving_chain: Option<[u8; 32]>,
    
    /// Number of message packets sent in current chain
    pub ns: u32,
    
    /// Number of message packets received in current chain
    pub nr: u32,
    
    /// Sequence size of previous message chain
    pub pn: u32,
    
    /// Message keys associated with out-of-order/skipped deliveries.
    /// Serialized Map keys formatted as string `"hex_remote_pub_key:message_index"`.
    /// Preserves zero-leak and complete compatibility with standard JSON serializers.
    pub skipped_keys: HashMap<String, [u8; 32]>,

    // -- POST QUANTUM INTEGRATION FIELDS --
    /// Local ephemeral Kyber-768 private key
    #[serde(default)]
    pub local_pq_private: Option<Vec<u8>>,
    
    /// Local ephemeral Kyber-768 public key
    #[serde(default)]
    pub local_pq_public: Option<Vec<u8>>,
    
    /// Remote Kyber-768 public key
    #[serde(default)]
    pub remote_pq_public: Option<Vec<u8>>,

    /// Pending Kyber-768 ciphertext to send in next messages
    #[serde(default)]
    pub pending_pq_ciphertext: Option<Vec<u8>>,
}

// -------------------------------------------------------------------------
// CRYPTOGRAPHIC UTILITIES
// -------------------------------------------------------------------------

/// Converts a raw byte slice into a clean hexadecimal string.
fn bytes_to_hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

/// Generates a secure, cryptographically random X25519 DH ephemeral keypair directly on RAM.
fn generate_local_dh_keypair() -> ([u8; 32], [u8; 32]) {
    let secret = EphemeralSecret::random_from_rng(OsRng);
    let public = PublicKey::from(&secret);
    (secret.to_bytes(), *public.as_bytes())
}

/// Re-derives local public key bytes from a static private key byte structure.
fn derive_public_key(private_bytes: &[u8; 32]) -> [u8; 32] {
    let secret = StaticSecret::from(*private_bytes);
    let public = PublicKey::from(&secret);
    *public.as_bytes()
}

/// Computes x25519 Diffie-Hellman Shared Secret from local private and remote public bytes.
fn compute_dh(private_bytes: &[u8; 32], public_bytes: &[u8; 32]) -> [u8; 32] {
    let secret = StaticSecret::from(*private_bytes);
    let public = PublicKey::from(*public_bytes);
    let shared = secret.diffie_hellman(&public);
    *shared.as_bytes()
}

/// Core Key Derivation Function over Symmetric Ratchet (KDF-CK).
/// Derives the next Chain Key (CK) and the associated message decryption/encryption key.
fn kdf_ck(ck: &[u8; 32]) -> ([u8; 32], [u8; 32]) {
    let hk = Hkdf::<Sha256>::from_prk(ck).expect("PRK length of 32 bytes is cryptographically sound");
    let mut next_ck = [0u8; 32];
    let mut msg_key = [0u8; 32];
    hk.expand(&[1], &mut next_ck).expect("Static buffer expand size 32 is correct");
    hk.expand(&[2], &mut msg_key).expect("Static buffer expand size 32 is correct");
    (next_ck, msg_key)
}

/// Core Hybrid Key Derivation Function over DH + KEM Ratchet (KDF-RK Hybrid).
/// Combines root key, the new X25519 DH shared secret, and Kyber-768 decapsulated secret 
/// using strong SHA-256 HKDF parameters.
fn kdf_rk_hybrid(
    rk: &[u8; 32],
    dh_out: &[u8; 32],
    pq_out: &[u8],
) -> ([u8; 32], [u8; 32]) {
    // Pack contiguous hybrid secret in RAM to bypass leakage vectors
    let mut joint_ikm = Vec::with_capacity(32 + pq_out.len());
    joint_ikm.extend_from_slice(dh_out);
    joint_ikm.extend_from_slice(pq_out);

    let hk = Hkdf::<Sha256>::new(Some(rk), &joint_ikm);
    let mut output = [0u8; 64];
    hk.expand(b"VRAV_DOUBLE_RATCHET_KDF_RK_HYBRID", &mut output)
        .expect("Buffer expand size of 64 bytes is correct");
    
    let mut next_rk = [0u8; 32];
    let mut next_ck = [0u8; 32];
    next_rk.copy_from_slice(&output[0..32]);
    next_ck.copy_from_slice(&output[32..64]);
    (next_rk, next_ck)
}

// -------------------------------------------------------------------------
// RATCHET IMPLEMENTATION METHODS
// -------------------------------------------------------------------------

impl RatchetState {
    /// Instantiates a new Hybrid Double Ratchet state as the session Initiator (e.g., Alice).
    /// Accepts an optional remote Kyber public key. If present, immediately encapsulates post-quantum secrets.
    pub fn new_initiator(
        shared_root_key: [u8; 32],
        remote_public: [u8; 32],
        remote_pq_public: Option<Vec<u8>>,
    ) -> Self {
        let (local_priv, local_pub) = generate_local_dh_keypair();
        let (local_pq_priv, local_pq_pub) = generate_pq_keypair();
        
        // Compute hybrid secrets
        let dh_secret = compute_dh(&local_priv, &remote_public);
        let mut pq_secret = vec![0u8; 32];
        let mut pct = None;
        if let Some(ref r_pq_pub) = remote_pq_public {
            if let Ok((ss, ct)) = pq_encapsulate(r_pq_pub) {
                pq_secret = ss;
                pct = Some(ct);
            }
        }

        let (root_key, sending_chain) = kdf_rk_hybrid(&shared_root_key, &dh_secret, &pq_secret);

        Self {
            root_key,
            local_dh_private: local_priv,
            local_dh_public: local_pub,
            remote_dh_public: Some(remote_public),
            sending_chain: Some(sending_chain),
            receiving_chain: None,
            ns: 0,
            nr: 0,
            pn: 0,
            skipped_keys: HashMap::new(),
            local_pq_private: Some(local_pq_priv),
            local_pq_public: Some(local_pq_pub),
            remote_pq_public,
            pending_pq_ciphertext: pct,
        }
    }

    /// Instantiates a new Hybrid Double Ratchet state as the session Responder (e.g., Bob).
    pub fn new_responder(
        shared_root_key: [u8; 32],
        local_private: [u8; 32],
        local_pq_keypair: Option<(Vec<u8>, Vec<u8>)>,
    ) -> Self {
        let local_pub = derive_public_key(&local_private);
        let (pq_priv, pq_pub) = match local_pq_keypair {
            Some((priv_bytes, pub_bytes)) => (priv_bytes, pub_bytes),
            None => generate_pq_keypair(),
        };

        Self {
            root_key: shared_root_key,
            local_dh_private: local_private,
            local_dh_public: local_pub,
            remote_dh_public: None,
            sending_chain: None,
            receiving_chain: None,
            ns: 0,
            nr: 0,
            pn: 0,
            skipped_keys: HashMap::new(),
            local_pq_private: Some(pq_priv),
            local_pq_public: Some(pq_pub),
            remote_pq_public: None,
            pending_pq_ciphertext: None,
        }
    }

    /// Skips receiving keys up to a designated index limit. Stores keys to allow out-of-order delivery decryption.
    fn skip_message_keys(&mut self, until: u32) -> Result<()> {
        // Enforce limits to prevent memory exhaustion DoS vectors from bad actors requesting extreme sequence limits
        if self.nr + 1000 < until {
            return Err(anyhow::anyhow!("Skipping queue request exceeds security thresholds"));
        }
        if let Some(mut ckr) = self.receiving_chain {
            let remote_pub = self.remote_dh_public.ok_or_else(|| anyhow::anyhow!("Remote public key missing during symmetric key skip"))?;
            let remote_pub_hex = bytes_to_hex(&remote_pub);
            
            while self.nr < until {
                let (next_ckr, message_key) = kdf_ck(&ckr);
                let skip_key_identifier = format!("{}:{}", remote_pub_hex, self.nr);
                self.skipped_keys.insert(skip_key_identifier, message_key);
                ckr = next_ckr;
                self.nr += 1;
            }
            self.receiving_chain = Some(ckr);
        }
        Ok(())
    }

    /// Encrypts an arbitrary cleartext payload, advancing the Symmetric Ratchet state.
    pub fn ratchet_encrypt(&mut self, plaintext: Vec<u8>) -> Result<Vec<u8>> {
        let cks = self.sending_chain.ok_or_else(|| anyhow::anyhow!("Symmetric sending chain has not been initialized"))?;
        let (next_cks, message_key) = kdf_ck(&cks);

        // Perform symmetric payload encryption with calculated key
        let ciphertext = crate::crypto_bridge::encrypt_message(message_key.to_vec(), plaintext);
        if ciphertext.is_empty() {
            return Err(anyhow::anyhow!("Double ratchet payload AES encryption failed"));
        }

        // Bundle Kyber key metadata into the active output envelope
        let local_pq_pub = self.local_pq_public.clone().unwrap_or_default();
        let pending_ct = self.pending_pq_ciphertext.clone().unwrap_or_default();

        let dr_message = DoubleRatchetMessage {
            ephemeral_public: self.local_dh_public,
            pn: self.pn,
            n: self.ns,
            ciphertext,
            pq_public_key: local_pq_pub,
            pq_ciphertext: pending_ct,
        };

        // Advance sending chain status
        self.sending_chain = Some(next_cks);
        self.ns += 1;

        // Contiguous serialization
        serde_json::to_vec(&dr_message).context("Failed to serialize DoubleRatchetMessage structure")
    }

    /// Decrypts a double ratchet ciphertext package, updating states and re-ratcheting DH chains if required.
    pub fn ratchet_decrypt(&mut self, dr_message_bytes: Vec<u8>) -> Result<Vec<u8>> {
        let dr_message: DoubleRatchetMessage = serde_json::from_slice(&dr_message_bytes)
            .context("Failed to parse DoubleRatchetMessage")?;

        let remote_pub_hex = bytes_to_hex(&dr_message.ephemeral_public);

        // Priority 1: Check skipped keys queue
        let skipped_key_str = format!("{}:{}", remote_pub_hex, dr_message.n);
        if let Some(msg_key) = self.skipped_keys.remove(&skipped_key_str) {
            let plaintext = crate::crypto_bridge::decrypt_message(msg_key.to_vec(), dr_message.ciphertext);
            if plaintext.is_empty() {
                return Err(anyhow::anyhow!("Symmetric decryption of skipped key envelope failed"));
            }
            return Ok(plaintext);
        }

        // Priority 2: Check if peer sent a new DH ephemeral key
        let is_new_dh_key = match self.remote_dh_public {
            None => true,
            Some(curr_pub) => curr_pub != dr_message.ephemeral_public,
        };

        if is_new_dh_key {
            // First, catch up and store keys left in our previous receiving chain
            if self.receiving_chain.is_some() {
                self.skip_message_keys(dr_message.pn)?;
            }

            // Perform DH Ratchet transition
            let new_remote_public = dr_message.ephemeral_public;
            self.remote_dh_public = Some(new_remote_public);

            // Decapsulate the post-quantum secret (Kyber-768 ciphertext) using our local PQ private key
            let mut pq_secret_recv = vec![0u8; 32];
            if !dr_message.pq_ciphertext.is_empty() {
                if let Some(ref local_pq_priv) = self.local_pq_private {
                    if let Ok(ss) = pq_decapsulate(&dr_message.pq_ciphertext, local_pq_priv) {
                        pq_secret_recv = ss;
                    }
                }
            }

            // Capture the remote PQ public key to use for future outbound encapsulations
            if !dr_message.pq_public_key.is_empty() {
                self.remote_pq_public = Some(dr_message.pq_public_key.clone());
            }

            // Derive new receiving chain key via KDF-RK + current local secret keys
            let dh_secret_recv = compute_dh(&self.local_dh_private, &new_remote_public);
            let (root_key_r, receiving_chain_new) = kdf_rk_hybrid(&self.root_key, &dh_secret_recv, &pq_secret_recv);
            self.root_key = root_key_r;
            self.receiving_chain = Some(receiving_chain_new);
            self.nr = 0;

            // Generate a fresh local keypair (Both X25519 & Kyber-768) for our next outbound messages
            let (local_priv_new, local_pub_new) = generate_local_dh_keypair();
            self.local_dh_private = local_priv_new;
            self.local_dh_public = local_pub_new;

            let (local_pq_priv_new, local_pq_pub_new) = generate_pq_keypair();
            self.local_pq_private = Some(local_pq_priv_new);
            self.local_pq_public = Some(local_pq_pub_new);

            // Encapsulate and derive new sending chain key via KDF-RK Hybrid
            let mut pq_secret_send = vec![0u8; 32];
            if let Some(ref remote_pq_pub) = self.remote_pq_public {
                if let Ok((ss, ct)) = pq_encapsulate(remote_pq_pub) {
                    pq_secret_send = ss;
                    self.pending_pq_ciphertext = Some(ct);
                }
            }

            let dh_secret_send = compute_dh(&self.local_dh_private, &new_remote_public);
            let (root_key_s, sending_chain_new) = kdf_rk_hybrid(&self.root_key, &dh_secret_send, &pq_secret_send);
            self.root_key = root_key_s;
            self.sending_chain = Some(sending_chain_new);
            self.pn = self.ns;
            self.ns = 0;
        } else {
            // Ephemeral key matches, so simply skip missing items up to the received message index
            self.skip_message_keys(dr_message.n)?;
        }

        // Run symmetric ratchet to calculate specific message key
        let ckr = self.receiving_chain.ok_or_else(|| anyhow::anyhow!("Symmetric receiving chain has not been initialized"))?;
        let (next_ckr, message_key) = kdf_ck(&ckr);
        self.receiving_chain = Some(next_ckr);
        self.nr += 1;

        // Perform symmetric decryption
        let plaintext = crate::crypto_bridge::decrypt_message(message_key.to_vec(), dr_message.ciphertext);
        if plaintext.is_empty() {
            return Err(anyhow::anyhow!("Symmetric double ratchet decryption of derived envelope failed"));
        }

        Ok(plaintext)
    }
}

// -------------------------------------------------------------------------
// PERSISTENT DB FLUTTER-EXPOSURES & SECURITY DOCUMENTATION
// -------------------------------------------------------------------------

/// Serializes a RatchetState into a standard JSON text string, ready to store in local Room or SQLite.
pub fn serialize_ratchet_state(state: &RatchetState) -> Result<String> {
    serde_json::to_string(state).map_err(|e| anyhow::anyhow!("Serialization to JSON string failed: {:?}", e))
}

/// Restores a stateful context from a JSON string representation retrieved from Room or SQLite.
pub fn deserialize_ratchet_state(json: String) -> Result<RatchetState> {
    serde_json::from_str(&json).map_err(|e| anyhow::anyhow!("Deserialization from JSON string failed: {:?}", e))
}

/// Securely overwrites a mutable byte slice with zeroes using volatile writes.
/// Decorated with `#[inline(never)]` and utilizing `write_volatile` to ensure
/// the Rust compiler does not optimize away the operations as dead store writes.
#[inline(never)]
pub fn secure_zero_slice(slice: &mut [u8]) {
    for byte_ref in slice.iter_mut() {
        unsafe {
            std::ptr::write_volatile(byte_ref, 0u8);
        }
    }
}

/// Completely wipes all sensitive cryptographic values in RatchetState using volatile operations,
/// rendering the state fully deactivated and clean.
pub fn secure_wipe_state(state: &mut RatchetState) {
    // 1. Wipe root_key
    secure_zero_slice(&mut state.root_key);

    // 2. Wipe local_dh_private
    secure_zero_slice(&mut state.local_dh_private);

    // 3. Wipe local_dh_public
    secure_zero_slice(&mut state.local_dh_public);

    // 4. Wipe remote_dh_public
    if let Some(ref mut dh_pub) = state.remote_dh_public {
        secure_zero_slice(dh_pub);
    }
    state.remote_dh_public = None;

    // 5. Wipe sending_chain
    if let Some(ref mut chain) = state.sending_chain {
        secure_zero_slice(chain);
    }
    state.sending_chain = None;

    // 6. Wipe receiving_chain
    if let Some(ref mut chain) = state.receiving_chain {
        secure_zero_slice(chain);
    }
    state.receiving_chain = None;

    // 7. Wipe skipped_keys values
    for (_, key) in state.skipped_keys.iter_mut() {
        secure_zero_slice(key);
    }
    state.skipped_keys.clear();

    // 8. Wipe local_pq_private
    if let Some(ref mut pq_priv) = state.local_pq_private {
        secure_zero_slice(pq_priv);
    }
    state.local_pq_private = None;

    // 9. Wipe local_pq_public
    if let Some(ref mut pq_pub) = state.local_pq_public {
        secure_zero_slice(pq_pub);
    }
    state.local_pq_public = None;

    // 10. Wipe remote_pq_public
    if let Some(ref mut pq_pub) = state.remote_pq_public {
        secure_zero_slice(pq_pub);
    }
    state.remote_pq_public = None;

    // 11. Wipe pending_pq_ciphertext
    if let Some(ref mut ct) = state.pending_pq_ciphertext {
        secure_zero_slice(ct);
    }
    state.pending_pq_ciphertext = None;

    // Wipe metadata counts
    state.ns = 0;
    state.nr = 0;
    state.pn = 0;
}

/*
================================================================================================
ARCHITECTURE DESIGN: SECURING RATCHETSTATE AT-REST PERSISTENCE IN ROOM / SQLITE
================================================================================================

To safely serialize and persist `RatchetState` locally without leaking secret variables:

1. COMPETE BACKWARD COMPATIBILITY:
   We have decorated all newly added post-quantum integration fields with `#[serde(default)]` so that 
   any existing session databases restored without Kyber keys fall back gracefully to `None` values 
   instead of failing serialization steps.

2. SAVE SKIPPED KEYS (CRITICAL FOR IN-ORDER & FORWARD SECRECY):
   Because double ratchet uses ephemeral keys, messages delayed or delivered out-of-order cannot be 
   decrypted if we discard the `skipped_keys` map. Correctly serializing `skipped_keys` in Room ensures
   zero loss of incoming delayed payloads when application threads resume.

3. ZERO-LEAK CRYPTOGRAPHIC ENCRYPTION-AT-REST (MANDATORY):
   Never save clean JSON plaintext containing root/chain/keypair variables. Prior to saving 
   the JSON string to SQLite/Room, the developer should:
     A. Integrate Android Keystore system providers: Instantiate a Master Secret Key on the 
        Tee/Secure Hardware module.
     B. Encrypt the retrieved JSON byte stream with AES-GCM-256 using the Keystore key.
     C. Persist the ciphertext blob inside the DB row.
     D. On resume, fetch from SQLite, decrypt the ciphertext block via Android Keystore, 
        and deserialize back into RAM directly.
*/

// -------------------------------------------------------------------------
// POST QUANTUM HYBRID UNIT TESTS
// -------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hybrid_pq_double_ratchet_flow() {
        // 1. Setup Pre-shared Hybrid Root Key master secret (e.g. from Kyber/ECDH hybrid X3DH handshake)
        let shared_root_key: [u8; 32] = [101u8; 32];

        // 2. Setup Bob's local key infrastructure
        let (bob_priv, bob_pub) = generate_local_dh_keypair();
        let (bob_pq_priv, bob_pq_pub) = generate_pq_keypair();

        // 3. Alice initializes her initiator ratchet state, supplying Bob's verified public keys
        let mut alice_state = RatchetState::new_initiator(
            shared_root_key,
            bob_pub,
            Some(bob_pq_pub.clone()),
        );

        // 4. Bob initializes his responder ratchet state, supplying current local private shares
        let mut bob_state = RatchetState::new_responder(
            shared_root_key,
            bob_priv,
            Some((bob_pq_priv, bob_pq_pub)),
        );

        // --- Alice Encrypts -> Bob Decrypts (Symmetric step inside Hybrid KDF-RK state) ---
        let msg_1 = b"PQC Hello from sovereign Alice!".to_vec();
        let payload_1 = alice_state.ratchet_encrypt(msg_1.clone()).unwrap();
        
        let decrypted_1 = bob_state.ratchet_decrypt(payload_1).unwrap();
        assert_eq!(decrypted_1, msg_1);

        // --- Alice sends second message advancing Symmetric step ---
        let msg_2 = b"Are we secure against quantum compute engines?".to_vec();
        let payload_2 = alice_state.ratchet_encrypt(msg_2.clone()).unwrap();

        let decrypted_2 = bob_state.ratchet_decrypt(payload_2).unwrap();
        assert_eq!(decrypted_2, msg_2);

        // --- Bob Replies to Alice (triggering BOTH DH & Kyber KEM asymmetric ratchets!) ---
        let msg_reply = b"Quantum-resistant handshake successfully negotiated!".to_vec();
        let payload_reply = bob_state.ratchet_encrypt(msg_reply.clone()).unwrap();

        let decrypted_reply = alice_state.ratchet_decrypt(payload_reply).unwrap();
        assert_eq!(decrypted_reply, msg_reply);

        // --- Out of Order Delivery on Hybrid PQC channels ---
        let msg_a = b"Contiguous Signal A".to_vec();
        let msg_b = b"Contiguous Signal B".to_vec();
        let msg_c = b"Contiguous Signal C".to_vec();

        let payload_a = alice_state.ratchet_encrypt(msg_a.clone()).unwrap();
        let payload_b = alice_state.ratchet_encrypt(msg_b.clone()).unwrap();
        let payload_c = alice_state.ratchet_encrypt(msg_c.clone()).unwrap();

        // Bob receives Signal C first (skipping A and B)
        let decrypted_c = bob_state.ratchet_decrypt(payload_c).unwrap();
        assert_eq!(decrypted_c, msg_c);

        // bob can decrypt A and B through cached skipped keys queue
        let decrypted_a = bob_state.ratchet_decrypt(payload_a).unwrap();
        assert_eq!(decrypted_a, msg_a);

        let decrypted_b = bob_state.ratchet_decrypt(payload_b).unwrap();
        assert_eq!(decrypted_b, msg_b);
    }

    #[test]
    fn test_serialization_compatibility_at_rest() {
        let shared_root_key: [u8; 32] = [202u8; 32];
        let (_priv, pub_key) = generate_local_dh_keypair();
        let state = RatchetState::new_initiator(shared_root_key, pub_key, None);

        // Verify JSON compliance
        let serialized = serialize_ratchet_state(&state).unwrap();
        assert!(!serialized.is_empty());

        let restored = deserialize_ratchet_state(serialized).unwrap();
        assert_eq!(restored.root_key, state.root_key);
        assert_eq!(restored.local_dh_public, state.local_dh_public);
    }

    #[test]
    fn test_secure_wipe_state() {
        let shared_root_key: [u8; 32] = [42u8; 32];
        let (_priv, pub_key) = generate_local_dh_keypair();
        // Setup state with Kyber identities
        let (_, local_pq_pub) = generate_pq_keypair();
        let mut state = RatchetState::new_initiator(shared_root_key, pub_key, Some(local_pq_pub));

        // Let's add some skipped keys to ensure they get wiped too
        state.skipped_keys.insert("some_peer_id:1".to_string(), [99u8; 32]);

        // Verify state is populated with non-zero keys
        assert_ne!(state.root_key, [0u8; 32]);
        assert_ne!(state.local_dh_private, [0u8; 32]);
        assert_eq!(state.skipped_keys.len(), 1);
        assert!(state.local_pq_private.is_some());

        // Perform volatile master secure zeroing
        secure_wipe_state(&mut state);

        // Assert all secret fields have been completely zeroed and cleared
        assert_eq!(state.root_key, [0u8; 32]);
        assert_eq!(state.local_dh_private, [0u8; 32]);
        assert_eq!(state.local_dh_public, [0u8; 32]);
        assert!(state.remote_dh_public.is_none());
        assert!(state.sending_chain.is_none());
        assert!(state.receiving_chain.is_none());
        assert_eq!(state.skipped_keys.len(), 0);
        assert!(state.local_pq_private.is_none());
        assert!(state.local_pq_public.is_none());
        assert!(state.remote_pq_public.is_none());
        assert!(state.pending_pq_ciphertext.is_none());
        assert_eq!(state.ns, 0);
        assert_eq!(state.nr, 0);
        assert_eq!(state.pn, 0);
    }
}
