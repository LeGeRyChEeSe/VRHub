use vrhub_core::{
    decode_base64_password, hmac_sha256, is_version_newer, md5, parse_catalog, resolve_tier,
    sha256_file, sha256_hex, validate_update_response,
};

#[test]
fn catalog_skips_header_and_parses_standard_fields() {
    let content = "Game Name;Release Name;Package Name;Version Code\nHalf-Life Alyx;hlalyx;hl.a;100;12345;7\n";
    let games = parse_catalog(content.to_string());
    assert_eq!(games.len(), 1);
    let g = &games[0];
    assert_eq!(g.game_name, "Half-Life Alyx");
    assert_eq!(g.release_name, "hlalyx");
    assert_eq!(g.package_name, "hl.a");
    assert_eq!(g.version_code, "100");
    assert_eq!(g.size_bytes, Some(12345));
    assert_eq!(g.popularity, 7);
}

#[test]
fn catalog_no_header_parses_from_first_line() {
    let content = "Beat Saber;beatsaber;beat;3;\n";
    let games = parse_catalog(content.to_string());
    assert_eq!(games.len(), 1);
    assert_eq!(games[0].popularity, 0);
    assert_eq!(games[0].size_bytes, None);
}

#[test]
fn catalog_handles_all_line_endings() {
    let content = "Game Name;R;P;V\nA;a;p;1\r\nB;b;p;2\rC;c;p;3\n";
    let games = parse_catalog(content.to_string());
    assert_eq!(games.len(), 3);
}

#[test]
fn catalog_dedupes_by_release_name_first_wins() {
    let content = "A;dup;p;1;10;5\nB;dup;q;2;20;9\n";
    let games = parse_catalog(content.to_string());
    assert_eq!(games.len(), 1);
    assert_eq!(games[0].game_name, "A");
    assert_eq!(games[0].size_bytes, Some(10));
}

#[test]
fn catalog_trims_fields_and_ignores_blanks() {
    let content = "  A ; dup1 ; p ; 1 ; 8 ; 3 \n\n   \nB;b;q;2\n";
    let games = parse_catalog(content.to_string());
    assert_eq!(games.len(), 2);
    assert_eq!(games[0].game_name, "A");
    assert_eq!(games[0].size_bytes, Some(8));
    assert_eq!(games[0].popularity, 3);
}

#[test]
fn catalog_requires_four_parts_min() {
    let content = "Game Name;Release;Package\n";
    let games = parse_catalog(content.to_string());
    assert!(games.is_empty());
}

#[test]
fn catalog_blank_content_returns_empty() {
    assert!(parse_catalog("".to_string()).is_empty());
    assert!(parse_catalog("   \n\n".to_string()).is_empty());
}

#[test]
fn resolve_tier_null_blank_defaults_to_standard() {
    assert_eq!(resolve_tier(None), "standard");
    assert_eq!(resolve_tier(Some("".to_string())), "standard");
    assert_eq!(resolve_tier(Some("   ".to_string())), "standard");
}

#[test]
fn resolve_tier_accepts_known_tiers() {
    assert_eq!(resolve_tier(Some("standard".to_string())), "standard");
    assert_eq!(resolve_tier(Some("supporter".to_string())), "supporter");
    assert_eq!(resolve_tier(Some("lucky".to_string())), "lucky");
}

#[test]
fn resolve_tier_unknown_falls_back_to_standard() {
    assert_eq!(resolve_tier(Some("unknown".to_string())), "standard");
    assert_eq!(resolve_tier(Some("VIP".to_string())), "standard");
}

#[test]
fn md5_matches_kotlin_cryptoutils() {
    // Kotlin CryptoUtils.md5 outputs lowercase hex.
    assert_eq!(md5("Hello".to_string()), "8b1a9953c4611296a827abf8c47804d7");
    assert_eq!(md5("".to_string()), "d41d8cd98f00b204e9800998ecf8427e");
}

#[test]
fn sha256_matches_kotlin_cryptoutils() {
    assert_eq!(sha256_hex("abc".to_string()), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
}

#[test]
fn sha256_file_matches_hash_of_same_bytes() {
    let path = std::env::temp_dir().join("vrhub_core_sha256_test.bin");
    std::fs::write(&path, b"abc").unwrap();
    assert_eq!(sha256_file(path.to_string_lossy().to_string()), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    let _ = std::fs::remove_file(&path);
}

#[test]
fn hmac_sha256_matches_kotlin_cryptoutils() {
    assert_eq!(hmac_sha256("1234".to_string(), "secret".to_string()), "55124a287e8ddc58a97eb3eea634a4d3185428d552de1a2b5bd49511355ababa");
}

#[test]
fn semver_newer_matches_kotlin_isversionnewer() {
    assert!(is_version_newer("1.2.0".to_string(), "1.1.9".to_string()));
    assert!(!is_version_newer("1.1.9".to_string(), "1.2.0".to_string()));
    assert!(!is_version_newer("1.2.0".to_string(), "1.2.0".to_string()));
    assert!(is_version_newer("v2.0".to_string(), "v1.9.3".to_string()));
    // pre-release: no-pre is newer than pre
    assert!(is_version_newer("1.2.0".to_string(), "1.2.0-beta".to_string()));
    assert!(!is_version_newer("1.2.0-beta".to_string(), "1.2.0".to_string()));
    // non-numeric segments map to 0 like Kotlin mapNotNull(filter digit)
    assert!(is_version_newer("1.2x.0".to_string(), "1.1.0".to_string()));
}

#[test]
fn update_response_validation_rejects_blank_fields() {
    assert!(!validate_update_response("".to_string(), "https://x".to_string(), "1.0.0".to_string()));
    assert!(!validate_update_response("1.1.0".to_string(), "".to_string(), "1.0.0".to_string()));
    assert!(validate_update_response("1.1.0".to_string(), "https://x".to_string(), "1.0.0".to_string()));
}

#[test]
fn base64_decode_password_roundtrip() {
    // Base64 NO_WRAP semantics: 'c2VjcmV0' -> "secret"
    assert_eq!(decode_base64_password("c2VjcmV0".to_string()), Some("secret".to_string()));
    assert_eq!(decode_base64_password("invalid!!!".to_string()), None);
    // NO_WRAP parity: unpadded input accepted (Android accepts it, strict STANDARD would fail)
    assert_eq!(decode_base64_password("YWJjZGU".to_string()), Some("abcde".to_string()));
}
