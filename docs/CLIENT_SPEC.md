# VRHub Client — Stats Collection API Specification

> **Version**: 1.0.0
> **Project**: VRHub (Android App)
> **Backend**: vrhub-monetization
> **Last Updated**: 2025-05-07

---

## Table of Contents

1. [Overview](#1-overview)
2. [Consent & Privacy Model](#2-consent--privacy-model)
3. [Data Collection Flow](#3-data-collection-flow)
4. [Network API](#4-network-api)
5. [Local Storage](#5-local-storage)
6. [Settings Integration](#6-settings-integration)
7. [Appendix: JSON Schemas](#7-appendix-json-schemas)

---

## 1. Overview

### 1.1 Architecture

```
┌─────────────────────┐                    ┌─────────────────────┐
│   VRHub Android App │                    │  vrhub-monetization │
│                     │                    │    (Rust Server)    │
│  •StatsCollector   │ ── POST /stats ──→ │                     │
│  •ConsentPrefs     │                    │  •Stats aggregation│
│  •DataStore        │ ←── GET /user/tier ─│  •Discord webhooks │
│                     │                    │  •Lucky role API   │
└─────────────────────┘                    └─────────────────────┘
```

### 1.2 Data Collected (Anonymized)

| Field | Type | Description |
|-------|------|-------------|
| `package_name` | String | Game package name (e.g., `com.beatgames.beatsaber`) |
| `is_favorite` | Boolean | User marked game as favorite |
| `tier` | Enum | `standard`, `supporter`, `lucky` |
| `timestamp` | Unix timestamp | Collection time |

### 1.3 Data NOT Collected

- User email or identifier
- Device ID or IMEI
- Location or IP address
- Personal information of any kind

### 1.4 Features to Implement

| Feature | Description |
|---------|-------------|
| Consent Dialog | First-launch dialog for opt-in |
| Consent Toggle | Settings screen toggle |
| StatsCollector | Background service for stats collection |
| DataStore | Local persistence of consent preference |
| Network Module | Retrofit client for stats API |

---

## 2. Consent & Privacy Model

### 2.1 Consent Flow

```
┌──────────────────┐
│  First App Launch │
└────────┬─────────┘
         │
         ▼
┌─────────────────────────────────┐
│  Consent Dialog (one-time)       │
│  "Help improve VRHub by sharing  │
│   anonymous usage statistics?"   │
│                                  │
│  [Accept]  [Decline]            │
└────────┬────────────────────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
┌────────┐  ┌────────────┐
│ Accept │  │  Decline   │
└───┬────┘  └─────┬──────┘
    │              │
    ▼              ▼
┌────────────┐  ┌────────────────────┐
│ Stats sent │  │ No data sent       │
│ to server │  │ Consent flagged    │
└────────────┘  │ as "declined"      │
                └────────────────────┘
```

### 2.2 User Consent Management

Users can change their consent at any time in:

**Settings > Privacy > Share anonymous statistics**

| State | Behavior |
|-------|----------|
| Accepted | Stats are collected and sent to server every collection cycle |
| Declined | No stats are collected; consent stored locally |

### 2.3 Privacy Notice Text

```
Help improve VRHub by sharing anonymous usage statistics.

We collect:
• Installed games on your device
• Your favorite games (marked with ⭐)
• Your tier (standard, supporter, or lucky)

We do NOT collect:
• Any personal information
• Device identifiers
• Your location

This data is used only for:
• Improving game recommendations
• Understanding VR game trends
• Discord community statistics

You can change this setting anytime in Settings.
```

---

## 3. Data Collection Flow

### 3.1 When to Collect

| Trigger | Description |
|---------|-------------|
| App First Launch (if consent given) | Initial collection |
| User Login | After authentication |
| Favorite Changed | When user marks/unmarks a game |
| Periodic (Daily) | Via WorkManager |

### 3.2 Collection Request Payload

```json
{
  "games": [
    {
      "package_name": "com.beatgames.beatsaber",
      "is_favorite": true
    },
    {
      "package_name": "com.vrchat.vrchat",
      "is_favorite": false
    }
  ],
  "tier": "lucky",
  "timestamp": 1746650400
}
```

### 3.3 Response Handling

| HTTP Status | Action |
|------------|--------|
| 200 | Success - stats received |
| 403 | Consent required - disable future collections |
| 5xx | Retry with backoff |

### 3.4 Error Handling

```
Collection Failure
     │
     ├── Network Error → Retry (max 3, exponential backoff)
     │
     ├── HTTP 403 → Consent revoked, stop collecting
     │
     ├── HTTP 5xx → Retry (max 3)
     │
     └── Other → Log error, skip (don't block UI)
```

---

## 4. Network API

### 4.1 Stats Collect

```
POST /stats/collect
Content-Type: application/json

Request Body: StatsCollectRequest (see Appendix)
Response: StatsCollectResponse
```

### 4.2 Consent Update

```
POST /stats/consent
Content-Type: application/json

Request Body: ConsentRequest
Response: ConsentResponse
```

### 4.3 Get User Tier

```
GET /user/tier?email=user@example.com
Response: UserTierResponse
```

### 4.4 API Base URL

The base URL should use the `monetizationUrl` from `ServerConfig`:

```kotlin
val apiBaseUrl = serverConfig.monetizationUrl ?: "https://vrhub.sunshine-aio.com"
```

### 4.5 Retrofit Interface

```kotlin
interface StatsApiService {

    @POST("stats/collect")
    suspend fun collectStats(@Body request: StatsCollectRequest): Response<StatsCollectResponse>

    @POST("stats/consent")
    suspend fun updateConsent(@Body request: ConsentRequest): Response<ConsentResponse>

    @GET("user/tier")
    suspend fun getUserTier(@Query("email") email: String): Response<UserTierResponse>
}
```

### 4.6 Models

```kotlin
@Serializable
data class StatsCollectRequest(
    val games: List<GameStat>,
    val tier: String,
    val timestamp: Long
)

@Serializable
data class GameStat(
    val package_name: String,
    val is_favorite: Boolean
)

@Serializable
data class StatsCollectResponse(
    val message: String
)

@Serializable
data class ConsentRequest(
    val consent: Boolean
)

@Serializable
data class ConsentResponse(
    val message: String,
    val consent: Boolean
)

@Serializable
data class UserTierResponse(
    val email: String,
    val tier: String?,
    val status: String
)
```

---

## 5. Local Storage

### 5.1 DataStore Keys

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `consent_enabled` | Boolean | `false` | User consent for stats collection |

### 5.2 DataStore Implementation

```kotlin
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vrhub_settings"
)

class ConsentPreferences(private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val CONSENT_ENABLED = booleanPreferencesKey("consent_enabled")
    }

    val consentEnabled: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[CONSENT_ENABLED] ?: false
        }

    suspend fun setConsentEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CONSENT_ENABLED] = enabled
        }
    }
}
```

### 5.3 Existing SharedPreferences

The app uses `context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)` for other settings. DataStore should be used for the consent preference to support Flow-based reactivity.

---

## 6. Settings Integration

### 6.1 Settings Screen Location

```
Main Screen
    └── Settings (gear icon)
            └── Privacy
                    └── Share anonymous statistics [Toggle]
```

### 6.2 Consent Toggle UI

```kotlin
@Composable
fun PrivacySettingsSection(
    consentEnabled: Boolean,
    onConsentChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Anonymous Statistics",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Help improve VRHub by sharing anonymous usage data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Share anonymous statistics")
                Switch(
                    checked = consentEnabled,
                    onCheckedChange = onConsentChanged
                )
            }

            if (consentEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ Your data is being shared anonymously",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}
```

### 6.3 ViewModel Integration

```kotlin
class SettingsViewModel(
    private val consentPreferences: ConsentPreferences
) : ViewModel() {

    val consentEnabled: StateFlow<Boolean> = consentPreferences.consentEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun setConsentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            consentPreferences.setConsentEnabled(enabled)
        }
    }
}
```

---

## 7. Appendix: JSON Schemas

### 7.1 Stats Collect Request

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["games", "tier", "timestamp"],
  "properties": {
    "games": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["package_name", "is_favorite"],
        "properties": {
          "package_name": {
            "type": "string",
            "pattern": "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$"
          },
          "is_favorite": {
            "type": "boolean"
          }
        }
      }
    },
    "tier": {
      "type": "string",
      "enum": ["standard", "supporter", "lucky"]
    },
    "timestamp": {
      "type": "integer",
      "description": "Unix timestamp of collection"
    }
  }
}
```

### 7.2 Consent Request

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["consent"],
  "properties": {
    "consent": {
      "type": "boolean"
    }
  }
}
```

### 7.3 User Tier Response

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["email", "tier", "status"],
  "properties": {
    "email": {
      "type": "string",
      "format": "email"
    },
    "tier": {
      "type": "string",
      "enum": ["standard", "supporter", "lucky"]
    },
    "status": {
      "type": "string",
      "enum": ["pending", "verified"]
    }
  }
}
```

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-05-07 | Initial specification |

---

## References

- [Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Retrofit](https://square.github.io/retrofit/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
