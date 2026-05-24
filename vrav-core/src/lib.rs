pub mod crypto_bridge;
pub mod p2p_bridge;
pub mod double_ratchet;

// Expose the functions directly so that flutter_rust_bridge acts on them
pub use crypto_bridge::{generate_keypair, derive_shared_secret, encrypt_message, decrypt_message};
pub use p2p_bridge::{VravP2pNode, P2pNodeEvent, register_session_key, subscribe_channel, send_message, wipe_session_keys, panic_button_secure_wipe};
pub use double_ratchet::{RatchetState, DoubleRatchetMessage, serialize_ratchet_state, deserialize_ratchet_state, secure_wipe_state};


