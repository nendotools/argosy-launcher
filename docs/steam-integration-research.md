# Steam Integration Research

## Executive Summary

**Feasibility: HIGH** - All components exist and have been proven to work on Android.

Steam games integrate as a first-class platform in Argosy, matching the existing RomM experience:
- Steam section in Settings for account management and driver setup
- Owned games appear in the existing `steam` platform filter
- Downloads use existing DownloadManager infrastructure
- Embedded translation layer (Winlator/Box64) for game execution

Reference implementation: **Pluvia** ([github.com/oxters168/Pluvia](https://github.com/oxters168/Pluvia))

---

## Integration Architecture

### Settings > Steam Section

```
Steam
+-- Account
|   +-- Login with QR Code (shows QR, user scans with Steam app)
|   +-- Logged in as: [username] (avatar, online status)
|   +-- Logout
|
+-- Library
|   +-- [X] games owned
|   +-- [X] games installed
|   +-- Sync Library (refresh from Steam)
|
+-- Translation Layer
|   +-- Status: [Not Installed / Installing / Ready]
|   +-- Install/Update Driver (~150MB)
|   +-- Version: Box64 vX.X + Wine vX.X
|
+-- Cloud Saves
|   +-- [Toggle] Enable Steam Cloud sync
|   +-- Last synced: [timestamp]
|
+-- Performance
|   +-- Default preset: [Compatibility / Balanced / Performance]
|   +-- (Per-game overrides in game modal)
```

### Platform Integration

Steam games bind to existing `steam` platform ID:
- Show alongside emulator platforms in Home/Library
- Use existing `GameEntity` with `source = STEAM`
- Cover art from Steam CDN
- Same UI patterns as RomM games

### Download Integration

Steam downloads use existing `DownloadManager`:
- Queue multiple depot downloads as single game
- Progress tracking in existing download UI
- Resume support via Steam's chunk-based CDN
- Storage in configurable location

---

## Component Breakdown

### 1. Authentication (QR Code Login)

**Solution: JavaSteam library** (`in.dragonbra:javasteam`)

Steam's IAuthenticationService API supports QR-based login:
- `BeginAuthSessionViaQR` - Initiates auth, returns challenge URL for QR code
- `PollAuthSessionStatus` - Polls until user scans QR from Steam mobile app
- Returns access token + refresh token for session persistence

**Pluvia implementation** (SteamService.kt:819-905):
```kotlin
val authSession = steamClient.authentication.beginAuthSessionViaQR(authDetails).await()
// authSession.challengeUrl contains the QR code data
val pollResult = authSession.pollAuthSessionStatus().await()
// pollResult contains accessToken, refreshToken, accountName
```

**Key points:**
- QR code refreshes every 30 seconds
- User scans with official Steam mobile app
- No password entry required on device
- Refresh tokens persist login across sessions

---

### 2. Game Library (Owned Games)

**Solution: JavaSteam + PICS (Package Info Cache System)**

Steam doesn't use a simple "get owned games" API. Instead:
1. Receive `LicenseListCallback` with all owned package IDs
2. Query PICS for package details (which apps each package contains)
3. Query PICS for app details (name, depots, launch configs)

**Pluvia stores locally:**
- `SteamLicense` - Package ownership info
- `SteamApp` - Game metadata (name, depots, install directories)

**Key classes:**
- `SteamApps.picsGetProductInfo()` - Fetch app/package metadata
- `PICSProductInfoCallback` - Returns KeyValue data about games

---

### 3. Game Downloads

**Solution: JavaSteam ContentDownloader**

JavaSteam includes a `ContentDownloader` class that handles:
- Depot manifest fetching
- CDN server selection
- Chunk downloading with verification
- Delta patching for updates

**Pluvia implementation** (SteamService.kt:465-517):
```kotlin
ContentDownloader(steamClient).downloadApp(
    appId = appId,
    depotId = depotId,
    installPath = installPath,
    stagingPath = stagingPath,
    branch = "public",
    onDownloadProgress = { progress -> ... }
)
```

**Storage considerations:**
- Games install to external storage (`/storage/emulated/0/Pluvia/Steam/steamapps/common/`)
- Depot manifests cached for update detection
- Only Windows x64 depots are downloaded (filtered by OS/arch)

---

### 4. x86 Translation Layer

**Solution: Winlator (embedded)**

Winlator is the de-facto standard for running Windows games on Android. It combines:
- **Wine** - Windows API compatibility layer
- **Box64** - x86_64 to ARM64 binary translation
- **DXVK/VKD3D** - DirectX to Vulkan translation

**FEX-Emu vs Box64**

Both are viable translation layers for Android:

| | FEX-Emu | Box64 |
|---|---------|-------|
| **Used by** | GameNative, GameHub, FEXDroid | Winlator, Pluvia |
| **Backing** | Valve funding (Steam Frame) | Community |
| **Approach** | Full x86/x86_64 emulation | Dynarec with native library wrapping |
| **Android** | Via proot + rootfs | Native integration |
| **Performance** | Excellent (Valve-optimized) | Good |

FEX is the default in GameNative and GameHub - Valve engineers actively contribute patches.
GameHub demonstrates 60fps gameplay (e.g., Hollow Knight) on Snapdragon 8 Gen 2.

**Recommendation:** Evaluate both. FEX may offer better compatibility long-term due to Valve investment,
but Box64/Winlator has more mature Android integration today (Pluvia's approach).

**Pluvia's approach:**
- Winlator is embedded directly in the app (not a separate APK)
- Ubuntu filesystem image (imagefs.txz) provides Linux environment
- Games run in isolated "containers" with configurable settings

**Performance notes:**
- Best on Snapdragon chips (Adreno GPUs)
- Games before 2013 / DX9 work best
- 10-20% overhead from translation
- Modern games (DX11/12) are playable but slower

---

### 5. Steam Cloud Saves

**Solution: JavaSteam SteamCloud handler**

Pluvia implements full Steam Cloud sync:
- `SteamCloud.signalAppLaunchIntent()` - Notify Steam of game launch
- `SteamAutoCloud.syncUserFiles()` - Upload/download save files
- `SteamCloud.signalAppExitSyncDone()` - Notify Steam of game exit

This allows saves to sync between Android and PC/Steam Deck.

---

### 6. Automatic Game Configuration (ProtonDB Integration)

**Goal: Zero-config game launching**

Users should not need to manually configure Wine/Box64 settings per game. We can leverage community knowledge from ProtonDB to automatically apply optimal settings.

**ProtonDB Community API**

ProtonDB maintains a community-driven database of game compatibility reports:
- Base URL: `https://protondb.max-p.me`
- `GET /games/` - List all games with ratings
- `GET /games/<appId>/reports/` - Detailed reports for a specific game

**Report data includes:**
```json
{
  "appId": 292030,
  "rating": "gold",
  "reports": [
    {
      "rating": "platinum",
      "notes": "Works perfectly with DXVK",
      "protonVersion": "Proton 8.0",
      "specs": { "gpu": "RTX 3080", "cpu": "Ryzen 5800X" }
    }
  ]
}
```

**Rating scale:**
- `platinum` - Works perfectly out of the box
- `gold` - Works with minor tweaks
- `silver` - Works with significant tweaks
- `bronze` - Runs but with issues
- `borked` - Does not run

**Implementation approach:**

1. **Pre-fetch compatibility data**
   - Sync ProtonDB ratings during library refresh
   - Store rating + common fixes per appId
   - Show rating badge on game cards (like Linux compatibility)

2. **Auto-apply common fixes**
   - Parse ProtonDB reports for common environment variables
   - Examples: `PROTON_USE_WINED3D=1`, `DXVK_ASYNC=1`
   - Map Proton settings to Wine/Box64 equivalents

3. **Fallback presets**
   - If no ProtonDB data: use conservative "Compatibility" preset
   - Users can still override per-game in advanced settings

**Environment variable mapping:**

| Proton Variable | Wine/Box64 Equivalent | Effect |
|-----------------|----------------------|--------|
| `PROTON_USE_WINED3D` | `WINEDLLOVERRIDES=d3d11=b` | Use OpenGL instead of DXVK |
| `DXVK_ASYNC` | `DXVK_ASYNC=1` | Async shader compilation |
| `PROTON_NO_ESYNC` | `WINEESYNC=0` | Disable esync |
| `PROTON_NO_FSYNC` | `WINEFSYNC=0` | Disable fsync |
| `PROTON_FORCE_LARGE_ADDRESS_AWARE` | `WINE_LARGE_ADDRESS_AWARE=1` | 4GB memory limit |

**ProtonTricks integration (future)**

ProtonTricks automates installing Windows dependencies (vcrun, dotnet, etc.):
- Could parse ProtonDB reports for required tricks
- Auto-download and install dependencies before first launch
- More complex - defer to Phase 5

---

## Libraries Required

| Library | Purpose | Maven/Gradle |
|---------|---------|--------------|
| JavaSteam | Steam network protocol | `in.dragonbra:javasteam` |
| Winlator | Windows game execution | Embedded (forked source) |
| BouncyCastle | Cryptography for Steam | `org.bouncycastle:*` |
| Wire | Protobuf serialization | `com.squareup.wire:*` |
| Ktor | HTTP client | `io.ktor:*` |

---

## Technical Challenges

### 1. APK Size
- Winlator + imagefs = 200MB+
- Consider dynamic feature modules (Play Feature Delivery)

### 2. Storage Permissions
- Steam games can be 10-50GB each
- Need robust storage access (SAF or MANAGE_EXTERNAL_STORAGE)

### 3. Performance Tuning
- Each game may need specific Box64/Wine settings
- Container configuration UI required

### 4. DRM Limitations
- Only DRM-free Steam games work
- Steam DRM (CEG) requires actual Steam client
- Most indie games work, AAA titles often don't

### 5. Legal Considerations
- Steam ToS allows personal use
- No distribution of Steam binaries
- Wine/Box64 are clean-room implementations

---

## Data Model

### GameEntity Extensions

```kotlin
// Existing GameEntity gains Steam-specific fields
data class GameEntity(
    // ... existing fields ...

    // Steam-specific
    val steamAppId: Int? = null,           // Steam app ID
    val steamDepots: List<Int>? = null,    // Downloaded depot IDs
    val steamBranch: String? = null,       // "public", "beta", etc.
    val steamBuildId: Long? = null,        // For update detection
    val steamLaunchConfigs: List<LaunchConfig>? = null,  // Multiple .exe options
    val protonDbRating: ProtonDbRating? = null,  // Compatibility rating
    val protonDbEnvVars: Map<String, String>? = null,  // Auto-applied settings
)

enum class GameSource {
    LOCAL,
    ROMM_SYNCED,
    STEAM  // New
}

enum class ProtonDbRating {
    PLATINUM,  // Works perfectly
    GOLD,      // Minor tweaks needed
    SILVER,    // Significant tweaks needed
    BRONZE,    // Runs with issues
    BORKED,    // Does not run
    UNKNOWN    // No data available
}
```

### SteamAccount (new entity)

```kotlin
@Entity(tableName = "steam_account")
data class SteamAccount(
    @PrimaryKey val steamId: Long,
    val username: String,
    val avatarHash: String?,
    val refreshToken: String,  // Encrypted
    val accessToken: String?,  // Encrypted
    val lastSync: Instant?
)
```

### SteamLicense (new entity)

```kotlin
@Entity(tableName = "steam_licenses")
data class SteamLicense(
    @PrimaryKey val packageId: Int,
    val appIds: List<Int>,
    val ownerAccountId: Int
)
```

---

## Implementation Phases

### Phase 1: Foundation
- [ ] Add JavaSteam dependency
- [ ] Create SteamService (background service for connection)
- [ ] Implement QR login flow
- [ ] Settings > Steam section (account only)
- [ ] Database migrations for new entities

### Phase 2: Library Sync
- [ ] Fetch owned games via PICS
- [ ] Map to GameEntity with source=STEAM
- [ ] Display in existing platform filter
- [ ] Cover art fetching from Steam CDN
- [ ] Library refresh mechanism
- [ ] ProtonDB API integration (fetch ratings during sync)
- [ ] ProtonDB rating badge on game cards

### Phase 3: Downloads
- [ ] Integrate ContentDownloader with DownloadManager
- [ ] Depot selection (Windows x64 only)
- [ ] Progress tracking and resume
- [ ] Storage location configuration
- [ ] Update detection via buildId changes

### Phase 4: Translation Layer
- [ ] Embed Winlator components (Box64, Wine, DXVK)
- [ ] ImageFS setup/installation
- [ ] Container management
- [ ] Launch game with proper environment
- [ ] Performance presets
- [ ] Auto-apply ProtonDB environment variables
- [ ] Parse ProtonDB reports for common fixes

### Phase 5: Polish
- [ ] Steam Cloud save sync
- [ ] Per-game launch config selection
- [ ] Per-game performance overrides
- [ ] ProtonTricks auto-install (vcrun, dotnet, etc.)
- [ ] Friend activity (optional)
- [ ] Playtime tracking to Steam

---

## File Structure

```
data/
  steam/
    SteamService.kt           # Background connection service
    SteamRepository.kt        # High-level Steam operations
    SteamAuthManager.kt       # QR login, token management
    SteamLibraryManager.kt    # PICS queries, game metadata
    SteamContentManager.kt    # Downloads, depot management

  protondb/
    ProtonDbApi.kt            # REST client for protondb.max-p.me
    ProtonDbRepository.kt     # Caching and lookup
    ProtonDbConfigMapper.kt   # Map Proton settings to Wine/Box64

  local/
    entity/
      SteamAccount.kt
      SteamLicense.kt
      ProtonDbCache.kt        # Cached compatibility data
    dao/
      SteamAccountDao.kt
      SteamLicenseDao.kt
      ProtonDbCacheDao.kt

  translation/
    TranslationLayerManager.kt  # Box64/Wine setup
    ContainerManager.kt         # Game containers
    WineEnvironment.kt          # Wine configuration

ui/
  screens/
    settings/
      steam/
        SteamSettingsScreen.kt
        SteamLoginScreen.kt     # QR code display
        SteamDriverScreen.kt    # Translation layer setup
  components/
    ProtonDbRatingBadge.kt      # Visual indicator for compatibility
```

---

## Component Distribution

Translation layer components are **bundled in assets**, not fetched from external sources.

### Pluvia's Asset Structure (reference)

```
app/src/main/assets/
  box86_64/
    box64-0.2.8.tzst         (3.1 MB)
    box64-0.2.9.tzst         (3.4 MB)
    box86-0.3.2.tzst         (1.6 MB)
    box86-0.3.7.tzst         (1.8 MB)
  dxwrapper/
    dxvk-0.96.tzst           (1.8 MB)
    dxvk-1.10.3.tzst         (3.4 MB)
    dxvk-2.3.1.tzst          (3.7 MB)
    vkd3d-2.12.tzst          (3.5 MB)
    d8vk-1.0.tzst            (1.1 MB)
  graphics_driver/
    turnip-24.1.0.tzst       (3.1 MB)
    virgl-23.1.9.tzst        (6.1 MB)
    zink-22.2.5.tzst         (6.8 MB)
  wincomponents/
    direct3d.tzst            (29.8 MB) - d3dx9_*, d3dcompiler_*, etc.
    vcrun2010.tzst           (1.0 MB)
    directsound.tzst, directmusic.tzst, etc.
  container_pattern.tzst     (7.9 MB) - Base container template
  imagefs_patches.tzst       (4.1 MB)

ubuntufs/src/main/assets/
  imagefs.txz                (172 MB) - Ubuntu rootfs with Wine
```

### Source of Components

| Component | Source | Distribution |
|-----------|--------|--------------|
| Box64/Box86 | [ptitSeb/box64](https://github.com/ptitSeb/box64) | Build from source, compress as .tzst |
| DXVK | [doitsujin/dxvk](https://github.com/doitsujin/dxvk) | Build or use releases, compress |
| VKD3D | [HansKristian-Work/vkd3d-proton](https://github.com/HansKristian-Work/vkd3d-proton) | Build or use releases |
| Turnip | Mesa source | Build Turnip driver for Adreno GPUs |
| Wine | [wine-mirror/wine](https://github.com/wine-mirror/wine) | Build with Android patches |
| ImageFS | Ubuntu + Wine | Custom rootfs, ~172MB compressed |

### Winlator101 .wcp Files (alternative)

[Winlator101](https://github.com/K11MCH1/Winlator101/releases) distributes components as `.wcp` files:
- Ready-to-use Box64, Wine, DXVK, FEXCore builds
- Frequently updated with latest versions
- Can be downloaded at runtime instead of bundling

### GameNative Approach

GameNative requires certain assets to be placed in `src/main/assets/` but distributes them
privately via Discord to contributors, not via GitHub releases.

### Recommendation

**For Argosy:**
1. **Bundle core components** in assets (like Pluvia)
2. **Use dynamic feature module** for `imagefs.txz` (~172MB)
3. **Consider Winlator101 .wcp format** for optional runtime updates
4. Components are tzst (zstd compressed) for smaller APK size

---

## APK Size Considerations

| Component | Size (compressed) | Notes |
|-----------|-------------------|-------|
| JavaSteam | ~2MB | Network protocol only |
| Box64 | ~3.5MB | ARM64 binary translator |
| DXVK/VKD3D | ~12MB | DirectX translation (multiple versions) |
| Turnip/Virgl/Zink | ~16MB | GPU drivers |
| WinComponents | ~35MB | DirectX runtimes, vcrun, etc. |
| ImageFS | ~172MB | Ubuntu rootfs with Wine |
| **Total** | ~240MB | Use dynamic delivery for imagefs |

**Mitigation:**
- Use Play Feature Delivery for `ubuntufs` module (imagefs.txz)
- Download on first Steam game install
- Base APK increases ~70MB without imagefs

---

## Design Decisions

1. **Embedded translation layer**
   - Full Winlator integration (Box64 + Wine + DXVK)
   - Smoother UX, no external dependencies
   - Download on first Steam game install via dynamic delivery

2. **License: GPL3**
   - Argosy is already GPL3 - fully compatible with Winlator
   - No licensing concerns

3. **Show all owned games**
   - Display entire Steam library
   - Mark games with known DRM issues (like Linux compatibility warnings)
   - Let users attempt any game - some may work unexpectedly

4. **Storage: Steam subdirectory**
   - Install to `[ROM storage]/steam/` alongside other platforms
   - Follows existing ROM organization pattern
   - Same storage selection UI as ROMs

---

## References

- [Pluvia Source Code](https://github.com/oxters168/Pluvia)
- [JavaSteam](https://github.com/Longi94/JavaSteam)
- [Winlator](https://github.com/brunodev85/winlator)
- [Steam Web API Docs](https://steamapi.xpaw.me/)
- [SteamKit2 (.NET reference)](https://github.com/SteamRE/SteamKit)
- [kSteam (Kotlin alternative)](https://github.com/iTaysonLab/kSteam)
- [ProtonDB Community API](https://protondb.max-p.me/) - Game compatibility ratings
- [ProtonDB](https://www.protondb.com/) - Community compatibility reports
- [ProtonTricks](https://github.com/Matoking/protontricks) - Windows dependency installer
- [FEX-Emu](https://fex-emu.com/) - Valve-backed x86 emulator for ARM64
- [FEXDroid](https://github.com/gamextra4u/FEXDroid) - FEX-Emu on Android
- [GameNative](https://gamenative.app/) - Open-source FEX-based Windows gaming on Android
- [GameHub](https://gamehubemulator.com/) - FEX-based emulator with Steam integration
