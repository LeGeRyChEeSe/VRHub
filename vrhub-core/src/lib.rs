uniffi::setup_scaffolding!("vrhub_core");

use base64::{engine::general_purpose::{STANDARD, STANDARD_NO_PAD}, Engine};
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
pub fn sha256_file(path: String) -> String {
    // APK integrity check, 8 KB buffered reads like CryptoUtils.sha256
    use std::io::Read;
    let mut hasher = Sha256::new();
    let mut file = std::fs::File::open(&path).expect("cannot open file");
    let mut buffer = [0u8; 8192];
    loop {
        let n = file.read(&mut buffer).expect("read error");
        if n == 0 {
            break;
        }
        hasher.update(&buffer[..n]);
    }
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
    // Android Base64.NO_WRAP tolerates missing padding; match that parity.
    let decoded = STANDARD.decode(&encoded).or_else(|_| STANDARD_NO_PAD.decode(&encoded)).ok()?;
    String::from_utf8(decoded).ok()
}

#[uniffi::export]
pub fn is_version_newer(latest: String, current: String) -> bool {
    let latest_base = latest.split('-').next().unwrap_or("");
    let current_base = current.split('-').next().unwrap_or("");
    let parse = |base: &str| -> Vec<i64> {
        base.split('.')
            .filter_map(|p| {
                let digits: String = p.chars().filter(|c| c.is_ascii_digit()).collect();
                digits.parse::<i64>().ok()
            })
            .collect()
    };
    let latest_parts = parse(latest_base);
    let current_parts = parse(current_base);
    let max_len = latest_parts.len().max(current_parts.len());
    for i in 0..max_len {
        let l = latest_parts.get(i).copied().unwrap_or(0);
        let c = current_parts.get(i).copied().unwrap_or(0);
        if l > c {
            return true;
        }
        if l < c {
            return false;
        }
    }
    // equal bases: a version without pre-release is newer than one with
    let latest_has_pre = latest.contains('-');
    let current_has_pre = current.contains('-');
    if !latest_has_pre && current_has_pre {
        return true;
    }
    if latest_has_pre && !current_has_pre {
        return false;
    }
    false
}

#[uniffi::export]
pub fn validate_update_response(version: String, download_url: String, current_version: String) -> bool {
    if version.trim().is_empty() || download_url.trim().is_empty() {
        return false;
    }
    is_version_newer(
        version.to_lowercase().trim_start_matches('v').to_string(),
        current_version.to_lowercase().trim_start_matches('v').to_string(),
    )
}

fn hex_encode(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}
