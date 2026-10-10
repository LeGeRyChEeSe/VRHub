uniffi::setup_scaffolding!("vrhub_core");

use base64::{engine::general_purpose::STANDARD, Engine};
use digest::Digest;
use md5::Md5;
use sha2::Sha256;
use hmac::{Hmac, Mac};
type HmacSha256 = Hmac<Sha256>;

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct GameData {
    pub game_name: String,
    pub package_name: String,
    pub version_code: String,
    pub release_name: String,
    pub size_bytes: Option<i64>,
    pub popularity: i32,
}

#[uniffi::export]
pub fn parse_catalog(content: String) -> Vec<GameData> {
    if content.trim().is_empty() {
        return Vec::new();
    }
    let lines: Vec<&str> = content.split(|c: char| c == '\n' || c == '\r').collect();
    let start = if lines.first().map_or(false, |l| l.contains("Game Name")) { 1 } else { 0 };
    let mut seen: std::collections::HashSet<String> = std::collections::HashSet::new();
    let mut games = Vec::new();
    for line in &lines[start..] {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let parts: Vec<&str> = line.split(';').collect();
        if parts.len() < 4 {
            continue;
        }
        let release_name = parts[1].trim().to_string();
        if seen.contains(&release_name) {
            continue;
        }
        seen.insert(release_name.clone());
        let size_bytes = parts.get(4).and_then(|s| s.trim().parse::<i64>().ok());
        let popularity = parts.get(5).and_then(|s| s.trim().parse::<i32>().ok()).unwrap_or(0);
        games.push(GameData {
            game_name: parts[0].trim().to_string(),
            release_name,
            package_name: parts[2].trim().to_string(),
            version_code: parts[3].trim().to_string(),
            size_bytes,
            popularity,
        });
    }
    games
}

#[uniffi::export]
pub fn resolve_tier(tier: Option<String>) -> String {
    match tier {
        None => "standard".to_string(),
        Some(t) if t.trim().is_empty() => "standard".to_string(),
        Some(t) if matches!(t.as_str(), "standard" | "supporter" | "lucky") => t,
        Some(_) => "standard".to_string(),
    }
}

#[uniffi::export]
pub fn md5(input: String) -> String {
    let mut hasher = Md5::new();
    hasher.update(input.as_bytes());
    hex_encode(&hasher.finalize())
}

#[uniffi::export]
pub fn sha256_hex(input: String) -> String {
    let mut hasher = Sha256::new();
    hasher.update(input.as_bytes());
    hex_encode(&hasher.finalize())
}

#[uniffi::export]
pub fn hmac_sha256(input: String, secret: String) -> String {
    let mut mac = HmacSha256::new_from_slice(secret.as_bytes())
        .expect("HMAC can take key of any size");
    mac.update(input.as_bytes());
    hex_encode(&mac.finalize().into_bytes())
}

#[uniffi::export]
pub fn decode_base64_password(encoded: String) -> Option<String> {
    let decoded = STANDARD.decode(encoded).ok()?;
    String::from_utf8(decoded).ok()
}

fn hex_encode(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}
