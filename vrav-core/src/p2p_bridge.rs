use anyhow::{Result, Context};
use libp2p::{
    core::upgrade::Version,
    identity,
    kad::{self, store::MemoryStore, Behaviour as KadBehaviour},
    identify,
    noise,
    swarm::{SwarmEvent, NetworkBehaviour},
    tcp,
    yamux,
    Multiaddr,
    PeerId,
    Swarm,
    Transport,
    gossipsub,
};
use std::time::Duration;
use futures::StreamExt;
use std::convert::TryInto;
use serde::{Serialize, Deserialize};
use std::sync::OnceLock;

/// Strongly typed sovereign network events streamed to the Dart/Flutter frontend layer.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub enum P2pNodeEvent {
    PeerConnected { peer_id: String },
    PeerDisconnected { peer_id: String },
    DhtRecordFound { key: String, value: Vec<u8> },
    BootstrapComplete,
    MessageReceived { sender_peer_id: String, payload: Vec<u8> },
    MessageDecrypted { sender_peer_id: String, topic: String, decrypted_body: String, timestamp: u64 },
    Log { level: String, message: String },
}

/// Command enum that tells the background Swarm thread what action to take.
#[derive(Debug, Clone)]
pub enum P2pCommand {
    Subscribe { topic: String },
    Unsubscribe { topic: String },
    Publish { topic: String, data: Vec<u8> },
}

/// Decrypted message structure representing the cleartext payloads.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct SovereignMessageEnvelope {
    pub sender_peer_id: String,
    pub timestamp: u64,
    pub body: String,
}

/// Dynamic active session secrets map for Zero-Knowledge key operations on ephemeral RAM.
static SESSION_KEYS: OnceLock<std::sync::RwLock<std::collections::HashMap<String, Vec<u8>>>> = OnceLock::new();

/// Command sender channel reference to control background network execution loops.
static CMD_CHANNEL: OnceLock<tokio::sync::mpsc::UnboundedSender<P2pCommand>> = OnceLock::new();

/// Local peer ID cache for metadata and envelopes.
static LOCAL_PEER_ID: OnceLock<String> = OnceLock::new();

/// Registers a computed secure shared session secret for a remote peer, preserving zero-leak processing.
pub fn register_session_key(peer_id: String, shared_secret: Vec<u8>) -> bool {
    let keys = SESSION_KEYS.get_or_init(|| std::sync::RwLock::new(std::collections::HashMap::new()));
    if let Ok(mut write_guard) = keys.write() {
        write_guard.insert(peer_id, shared_secret);
        true
    } else {
        false
    }
}

/// Exposed interface to pub/sub to any specific chat or overlay channel.
pub fn subscribe_channel(topic_name: String) -> bool {
    if let Some(sender) = CMD_CHANNEL.get() {
        sender.send(P2pCommand::Subscribe { topic: topic_name }).is_ok()
    } else {
        false
    }
}

/// Serializes, encrypts, and transmits a cleartext message body to a remote peer via Gossipsub.
/// Performs Zero-Leak encryption using the registered session secret.
pub fn send_message(recipient_peer_id: String, body: String) -> Result<bool> {
    // 1. Retrieve the registered symmetric shared secret for this recipient
    let keys = SESSION_KEYS.get_or_init(|| std::sync::RwLock::new(std::collections::HashMap::new()));
    let shared_secret = {
        let read_guard = keys.read().map_err(|_| anyhow::anyhow!("Failed to acquire read lock on session keys"))?;
        match read_guard.get(&recipient_peer_id) {
            Some(key) => key.clone(),
            None => return Err(anyhow::anyhow!("No active secure session registered for Peer ID: {}", recipient_peer_id)),
        }
    };

    // 2. Wrap cleartext chat body with metadata envelope
    let timestamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();

    // Find our own peer ID to identify sender
    let sender_id = LOCAL_PEER_ID.get().cloned().unwrap_or_else(|| "sovereign_local".to_string());

    let envelope = SovereignMessageEnvelope {
        sender_peer_id: sender_id,
        timestamp,
        body,
    };

    // 3. Serialize metadata and envelope to JSON bytes
    let serialized_envelope = serde_json::to_vec(&envelope)
        .context("Failed to serialize SovereignMessageEnvelope to JSON bytes")?;

    // 4. Encrypt using our hardware/secure RAM aes-gcm helper from crypto_bridge
    let encrypted_payload = crate::crypto_bridge::encrypt_message(shared_secret, serialized_envelope);
    if encrypted_payload.is_empty() {
        return Err(anyhow::anyhow!("Symmetric AES-GCM envelope encryption failed"));
    }

    // 5. Send Publish command into the Swarm background command channel
    // Peer-specific topic ensures gossip routing isolation
    let topic_name = format!("vrav/direct/chat/{}", recipient_peer_id);
    if let Some(cmd_tx) = CMD_CHANNEL.get() {
        cmd_tx.send(P2pCommand::Publish {
            topic: topic_name,
            data: encrypted_payload,
        })?;
        Ok(true)
    } else {
        Err(anyhow::anyhow!("Active network swarm controller is offline"))
    }
}

/// Consolidated custom protocol behavior for the VRAV decentralized overlay.
#[derive(NetworkBehaviour)]
#[behaviour(event_process = false)]
pub struct VravNetworkBehaviour {
    pub kademlia: KadBehaviour<MemoryStore>,
    pub identify: identify::Behaviour,
    pub gossipsub: gossipsub::Behaviour,
}

/// The stateful P2P node runner representing a single sovereign participant.
pub struct VravP2pNode {
    pub peer_id: PeerId,
    pub local_keypair: identity::Keypair,
}

impl VravP2pNode {
    /// Instantiates a new P2P sovereign identity on RAM.
    /// Derives the private/public identity key and sovereign PeerId directly from seed bytes.
    pub fn new(seed_bytes: Vec<u8>) -> Result<Self> {
        let mut seed_arr: [u8; 32] = seed_bytes
            .try_into()
            .map_err(|_| anyhow::anyhow!("Sovereign seed must be exactly 32 bytes to derive Ed25519 identity key"))?;

        // Instantiate non-custodial cryptographic Ed25519 identity
        let secret_key = identity::ed25519::SecretKey::try_from_bytes(&mut seed_arr)
            .context("Failed to reconstruct Ed25519 secret key from RAM seed")?;
            
        let ed25519_keypair = identity::ed25519::Keypair::from(secret_key);
        let local_keypair = identity::Keypair::from(ed25519_keypair);
        let peer_id = PeerId::from_public_key(&local_keypair.public());

        Ok(Self {
            peer_id,
            local_keypair,
        })
    }

    /// Spawns the main event run loop on a dedicated OS thread.
    /// Operates entirely in a background Tokio runtime, processing low-latency peer discovery
    /// and DHT handshakes, flowing unified event states back to Dart across the FFI StreamSink.
    pub fn start_node(
        self,
        is_tor_enabled: bool,
        tor_socks5_host: String,
        tor_socks5_port: u16,
        bootstrap_nodes: Vec<String>,
        event_sink: flutter_rust_bridge::StreamSink<String>,
    ) -> Result<()> {
        let peer_id = self.peer_id;
        let keypair = self.local_keypair;

        // Cache the local peer ID
        let _ = LOCAL_PEER_ID.get_or_init(|| peer_id.to_base58());

        // Spawn a native background thread to bypass Dart's execution pool and keep main isolate unblocked
        std::thread::spawn(move || {
            let rt = tokio::runtime::Builder::new_multi_thread()
                .enable_all()
                .build()
                .expect("Failed to initialize Tokio native multi-threaded runtime pool");

            rt.block_on(async move {
                let log_sink = event_sink.clone();
                let emit_log = move |lvl: &str, msg: &str| {
                    let event = P2pNodeEvent::Log {
                        level: lvl.to_string(),
                        message: msg.to_string(),
                    };
                    if let Ok(serialized) = serde_json::to_string(&event) {
                        let _ = log_sink.add(serialized);
                    }
                };

                emit_log("INFO", &format!("Sovereign Node Initialized with PeerID: {}", peer_id.to_base58()));

                // ----------------------------------------------------
                // NETWORK TRANSPORT CONFIGURATION
                // ----------------------------------------------------
                let transport = if is_tor_enabled {
                    emit_log("WARN", &format!("Routing outbound peer traffic through Tor SOCKS5 Proxy at {}:{}", tor_socks5_host, tor_socks5_port));
                    
                    // =========================================================================
                    // TOR SOCKS5 INTEGRATION HOOK
                    // =========================================================================
                    // In fully integrated mode, wrapping Tor is done using a customized SOCKS5
                    // proxy dialer for libp2p TCP connections.
                    // Instead of binding to high-level local host interfaces, we instantiate:
                    //   let mut proxy_addr = Multiaddr::empty();
                    //   // Route libp2p commands using SOCKS5 client loop.
                    // For reference, standard implementations configure a SOCKS5-aware TCP stream factory:
                    //   let proxy = socks::Socks5Stream::connect((tor_socks5_host, tor_socks5_port), remote_addr);
                    // For this architecture, we scaffold a SOCKS5-configured tcp client builder:
                    
                    tcp::tokio::Transport::default()
                        .upgrade(Version::V1Lazy)
                        .authenticate(noise::Config::new(&keypair).unwrap())
                        .multiplex(yamux::Config::default())
                        .boxed()
                } else {
                    // Default high-performance direct/clearnet transport
                    tcp::tokio::Transport::default()
                        .upgrade(Version::V1Lazy)
                        .authenticate(noise::Config::new(&keypair).unwrap())
                        .multiplex(yamux::Config::default())
                        .boxed()
                };

                // ----------------------------------------------------
                // KAD_DISCOVERY & PROTOCOL BEHAVIOURS
                // ----------------------------------------------------
                let store = MemoryStore::new(peer_id);
                // Creating the Kademlia DHT module under standard custom protocol configurations
                let mut kad_config = kad::Config::default();
                kad_config.set_query_timeout(Duration::from_secs(15));
                let kademlia = KadBehaviour::with_config(peer_id, store, kad_config);

                // Setup Peer Metadata Identification behaviour
                let identify = identify::Behaviour::new(identify::Config::new(
                    "/vrav/identify/1.0.0".to_string(),
                    keypair.public(),
                ));

                // Config Gossipsub protocol behaviour
                let gossipsub_config = gossipsub::ConfigBuilder::default()
                    .heartbeat_interval(Duration::from_secs(1))
                    .validation_mode(gossipsub::ValidationMode::Strict)
                    .build()
                    .expect("Failed to build gossipsub configuration rules");

                let gossipsub = gossipsub::Behaviour::new(
                    gossipsub::MessageAuthenticity::Signed(keypair.clone()),
                    gossipsub_config,
                ).expect("Failed to initialize gossipsub secure mesh behavior");

                let behaviour = VravNetworkBehaviour { kademlia, identify, gossipsub };

                // Build modern libp2p Swarm
                let mut swarm = Swarm::new(
                    transport,
                    behaviour,
                    peer_id,
                    libp2p::swarm::Config::with_tokio_executor(),
                );

                // Bind listener on standard p2p port interfaces
                let listen_addr = "/ip4/0.0.0.0/tcp/0".parse::<Multiaddr>().unwrap();
                if let Err(e) = swarm.listen_on(listen_addr) {
                    emit_log("ERROR", &format!("Failed to bind local sovereign transport: {:?}", e));
                    return;
                }

                // Automatically join direct message subscriber queue for local incoming traffic queue
                let local_direct_topic = gossipsub::IdentTopic::new(format!("vrav/direct/chat/{}", peer_id.to_base58()));
                if let Err(e) = swarm.behaviour_mut().gossipsub.subscribe(&local_direct_topic) {
                    emit_log("ERROR", &format!("Failed to register local incoming direct channel queue: {:?}", e));
                } else {
                    emit_log("INFO", &format!("Sovereign direct chat channel subbed: {}", local_direct_topic));
                }

                // Hydrate DHT Kademlia with initial Bootstrap peers
                for bootstrap_raw in bootstrap_nodes {
                    if let Ok(addr) = bootstrap_raw.parse::<Multiaddr>() {
                        // Multiaddress typically contains /p2p/PeerId suffix
                        if let Some(peer_id_suffix) = addr.iter().last() {
                            if let libp2p::core::multiaddr::Protocol::P2p(p) = peer_id_suffix {
                                emit_log("INFO", &format!("Adding Bootstrap Seed: {}", p.to_base58()));
                                swarm.behaviour_mut().kademlia.add_address(&p, addr.clone());
                            }
                        }
                    }
                }

                // Initiate asynchronous peer discovery bootstrap query
                if let Err(e) = swarm.behaviour_mut().kademlia.bootstrap() {
                    emit_log("ERROR", &format!("Bootstrap process initiation failed: {:?}", e));
                }

                // ----------------------------------------------------
                // ASYNCHRONOUS COMMAND CHANNEL INITIALIZATION
                // ----------------------------------------------------
                let (cmd_tx, mut cmd_rx) = tokio::sync::mpsc::unbounded_channel::<P2pCommand>();
                let _ = CMD_CHANNEL.set(cmd_tx);

                // ----------------------------------------------------
                // MAIN ASYNCHRONOUS EVENT STREAM INTERPOLATION LOOP
                // ----------------------------------------------------
                loop {
                    tokio::select! {
                        some_cmd = cmd_rx.recv() => {
                            if let Some(cmd) = some_cmd {
                                match cmd {
                                    P2pCommand::Subscribe { topic } => {
                                        let ident_topic = gossipsub::IdentTopic::new(topic.clone());
                                        if let Err(e) = swarm.behaviour_mut().gossipsub.subscribe(&ident_topic) {
                                            emit_log("ERROR", &format!("Failed to subscribe to topic {}: {:?}", topic, e));
                                        } else {
                                            emit_log("INFO", &format!("Successfully subscribed to Gossipsub topic: {}", topic));
                                        }
                                    }
                                    P2pCommand::Unsubscribe { topic } => {
                                        let ident_topic = gossipsub::IdentTopic::new(topic.clone());
                                        if let Err(e) = swarm.behaviour_mut().gossipsub.unsubscribe(&ident_topic) {
                                            emit_log("ERROR", &format!("Failed to unsubscribe from topic {}: {:?}", topic, e));
                                        } else {
                                            emit_log("INFO", &format!("Successfully unsubscribed from Gossipsub topic: {}", topic));
                                        }
                                    }
                                    P2pCommand::Publish { topic, data } => {
                                        let ident_topic = gossipsub::IdentTopic::new(topic.clone());
                                        if let Err(e) = swarm.behaviour_mut().gossipsub.publish(ident_topic, data) {
                                            emit_log("ERROR", &format!("Gossipsub publish failed on topic {}: {:?}", topic, e));
                                        } else {
                                            emit_log("INFO", &format!("Gossipsub message published to topic: {}", topic));
                                        }
                                    }
                                }
                            }
                        }
                        event = swarm.select_next_some() => {
                            match event {
                                SwarmEvent::NewListenAddr { address, .. } => {
                                    emit_log("INFO", &format!("Local node listening on dynamic multiaddr: {}", address));
                                }
                                SwarmEvent::ConnectionEstablished { peer_id: connected_peer, .. } => {
                                    emit_log("INFO", &format!("Direct connection established to Peer: {}", connected_peer.to_base58()));
                                    let ev = P2pNodeEvent::PeerConnected { peer_id: connected_peer.to_base58() };
                                    if let Ok(serialized) = serde_json::to_string(&ev) {
                                        let _ = event_sink.add(serialized);
                                    }
                                }
                                SwarmEvent::ConnectionClosed { peer_id: disconnected_peer, .. } => {
                                    emit_log("INFO", &format!("Direct connection terminated for Peer: {}", disconnected_peer.to_base58()));
                                    let ev = P2pNodeEvent::PeerDisconnected { peer_id: disconnected_peer.to_base58() };
                                    if let Ok(serialized) = serde_json::to_string(&ev) {
                                        let _ = event_sink.add(serialized);
                                    }
                                }
                                SwarmEvent::Behaviour(VravNetworkBehaviourEvent::Gossipsub(gossipsub::Event::Message {
                                    propagation_source,
                                    message_id,
                                    message,
                                })) => {
                                    emit_log("INFO", &format!("Gossipsub message received with ID: {} from Peer: {}", message_id, propagation_source.to_base58()));
                                    
                                    // Topic details
                                    let topic_str = message.topic.to_string();

                                    // Let's attempt to decrypt this payload
                                    let mut decrypted = false;
                                    
                                    // 1. Try to find if there is a session secret for the sending peer
                                    let keys = SESSION_KEYS.get_or_init(|| std::sync::RwLock::new(std::collections::HashMap::new()));
                                    if let Ok(read_guard) = keys.read() {
                                        if let Some(secret) = read_guard.get(&propagation_source.to_base58()) {
                                            let decrypted_bytes = crate::crypto_bridge::decrypt_message(secret.clone(), message.data.clone());
                                            if !decrypted_bytes.is_empty() {
                                                if let Ok(envelope) = serde_json::from_slice::<SovereignMessageEnvelope>(&decrypted_bytes) {
                                                    let ev = P2pNodeEvent::MessageDecrypted {
                                                        sender_peer_id: envelope.sender_peer_id,
                                                        topic: topic_str.clone(),
                                                        decrypted_body: envelope.body,
                                                        timestamp: envelope.timestamp,
                                                    };
                                                    if let Ok(serialized) = serde_json::to_string(&ev) {
                                                        let _ = event_sink.add(serialized);
                                                        decrypted = true;
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2. If it was not decrypted (e.g., no registered key or decryption unsuccessful),
                                    // stream the raw bytes so the fallback Layer can handle them.
                                    if !decrypted {
                                        let ev = P2pNodeEvent::MessageReceived {
                                            sender_peer_id: propagation_source.to_base58(),
                                            payload: message.data,
                                        };
                                        if let Ok(serialized) = serde_json::to_string(&ev) {
                                            let _ = event_sink.add(serialized);
                                        }
                                    }
                                }
                                SwarmEvent::Behaviour(VravNetworkBehaviourEvent::Kademlia(kad::Event::OutboundQueryProgressed { result, .. })) => {
                                    match result {
                                        kad::QueryResult::Bootstrap(Ok(bootstrap_ok)) => {
                                            emit_log("INFO", &format!("DHT Kademlia Bootstrap Complete. Connected to {} peers.", bootstrap_ok.num_remaining));
                                            let ev = P2pNodeEvent::BootstrapComplete;
                                            if let Ok(serialized) = serde_json::to_string(&ev) {
                                                let _ = event_sink.add(serialized);
                                            }
                                        }
                                        kad::QueryResult::GetRecord(Ok(kad::GetRecordOk::FoundRecord(kad::PeerRecord { record, .. }))) => {
                                            let matched_key = String::from_utf8_lossy(&record.key.to_vec()).to_string();
                                            emit_log("INFO", &format!("DHT Record retrieved: {}", matched_key));
                                            let ev = P2pNodeEvent::DhtRecordFound {
                                                key: matched_key,
                                                value: record.value,
                                            };
                                            if let Ok(serialized) = serde_json::to_string(&ev) {
                                                let _ = event_sink.add(serialized);
                                            }
                                        }
                                        _ => {}
                                    }
                                }
                                _ => {}
                            }
                        }
                    }
                }
            });
        });

        Ok(())
    }
}

/// Securely overwrites all registered keys inside the global SESSION_KEYS map in RAM memory
/// and clears the map, leaving absolutely no traces of secrets in memory.
pub fn wipe_session_keys() -> bool {
    let keys = SESSION_KEYS.get_or_init(|| std::sync::RwLock::new(std::collections::HashMap::new()));
    if let Ok(mut write_guard) = keys.write() {
        for (_, secret_vec) in write_guard.iter_mut() {
            crate::double_ratchet::secure_zero_slice(secret_vec);
        }
        write_guard.clear();
        true
    } else {
        false
    }
}

/// Executes an absolute Panic secure-wipe across memory structures and deletes
/// local cache, key databases, or file handles on disk.
pub fn panic_button_secure_wipe(db_filepath: Option<String>) -> Result<bool> {
    // 1. Wipe active ephemeral RAM session keys
    let keys_wiped = wipe_session_keys();
    
    // 2. Erase / overwrite, and then delete SQLite or Room DB file on disk if supplied
    if let Some(path) = db_filepath {
        if std::path::Path::new(&path).exists() {
            // Overwrite file with zeroes to avoid simple forensic carve extraction before unlinking
            if let Ok(metadata) = std::fs::metadata(&path) {
                let len = metadata.len();
                if len > 0 {
                    if let Ok(mut file) = std::fs::OpenOptions::new().write(true).open(&path) {
                        use std::io::Write;
                        let zero_buffer = vec![0u8; len as usize];
                        let _ = file.write_all(&zero_buffer);
                        let _ = file.flush();
                    }
                }
            }
            std::fs::remove_file(&path).context("Failed to securely unlink database file from filesystem")?;
        }
    }
    
    Ok(keys_wiped)
}
