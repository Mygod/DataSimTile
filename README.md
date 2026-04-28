# Data SIM Tile

[![CI](https://github.com/Mygod/DataSimTile/actions/workflows/ci.yml/badge.svg)](https://github.com/Mygod/DataSimTile/actions/workflows/ci.yml)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Language: Java](https://img.shields.io/github/languages/top/Mygod/DataSimTile.svg)](https://github.com/Mygod/DataSimTile/search?l=java)

[![Get it on Obtainium](https://github.com/ImranR98/Obtainium/raw/main/assets/graphics/badge_obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Mygod/DataSimTile)

One quick settings tile to switch the default mobile data SIM. (**Shizuku required**)

This app is useful for:

* Switching mobile data between active SIMs without opening Settings;
* Keeping the tile label on the current data SIM name;
* Matching the Settings data-SIM picker behavior by enabling mobile data on the selected SIM after switching.

There is no launcher UI. Long-pressing the tile opens the system SIM settings screen. If Shizuku is unavailable or not
granted, tapping the tile opens the same settings screen.

## Getting started

1. Install and start [Shizuku](https://shizuku.rikka.app/).
2. Install Data SIM Tile.
3. Add the `Data SIM` quick settings tile.
4. Grant the Shizuku permission when prompted.
5. Tap the tile to switch to the next active SIM.

The app cycles through active subscriptions sorted by SIM slot. After switching the default data subscription, it enables
user mobile data on the selected subscription, following AOSP Settings' `SimDialogActivity` data picker behavior. It does
not intentionally disable mobile data on the previous SIM.

## Q & A

### No Shizuku?

The app cannot switch the data SIM by itself. Android keeps the required telephony calls behind privileged permissions,
so Shizuku is the only supported path.

### Why does tapping the tile open SIM settings?

The tile opens SIM settings when Shizuku is missing, permission was denied, the Shizuku user service cannot bind, or the
privileged telephony call fails.

### Why is mobile data enabled after switching?

This matches AOSP Settings' default data SIM picker: Settings changes the default data subscription and then enables user
mobile data for the chosen subscription. Without that second step, switching to a SIM whose per-subscription mobile data
flag is off can leave mobile data disabled.

### Android 6 plz...?

There is no quick setting tile on Android 6.

## Private APIs used / Assumptions for Android customizations

_a.k.a. things that can go wrong if this app doesn't work._

This is a list of stuff that might impact this app's functionality if unavailable.
This is only meant to be an index.
You can read more in the source code.
API restrictions are updated up to [SHA-256 checksum `9102af02fe6ab68b92464bdff5e5b09f3bd62c65d1130aaf85d3296f17d38074`](https://github.com/Mygod/hiddenapi/commit/2f90e9da30976febeb0630cba48c4da0116c323d).
API qualifiers below describe when this app uses each member.

Greylisted/blacklisted APIs or internal constants: (some constants are hardcoded or implicitly used)

* `Landroid/os/ServiceManager;->getService(Ljava/lang/String;)Landroid/os/IBinder;,unsupported`
* `Lcom/android/internal/telephony/ISub$Stub;->asInterface(Landroid/os/IBinder;)Lcom/android/internal/telephony/ISub;`
* (since API 35) `Lcom/android/internal/telephony/ISub;->getActiveSubscriptionInfoList(Ljava/lang/String;Ljava/lang/String;Z)Ljava/util/List;,blocked`
* (API 30-34) `Lcom/android/internal/telephony/ISub;->getActiveSubscriptionInfoList(Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;`
* (API 24-29) `Lcom/android/internal/telephony/ISub;->getActiveSubscriptionInfoList(Ljava/lang/String;)Ljava/util/List;`
* `Lcom/android/internal/telephony/ISub;->getDefaultDataSubId()I,blocked`
* `Lcom/android/internal/telephony/ISub;->setDefaultDataSubId(I)V,blocked`
* `Lcom/android/internal/telephony/ITelephony$Stub;->asInterface(Landroid/os/IBinder;)Lcom/android/internal/telephony/ITelephony;`
* (since API 33) `Lcom/android/internal/telephony/ITelephony;->setDataEnabledForReason(IIZLjava/lang/String;)V,blocked`
* (API 31-32) `Lcom/android/internal/telephony/ITelephony;->setDataEnabledForReason(IIZ)V`
* (API 28-30) `Lcom/android/internal/telephony/ITelephony;->setUserDataEnabled(IZ)V`
* (API 24-27) `Lcom/android/internal/telephony/ITelephony;->setDataEnabled(IZ)V`
