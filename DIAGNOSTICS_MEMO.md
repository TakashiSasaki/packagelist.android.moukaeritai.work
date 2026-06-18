# Package Visibility Diagnostic Report & Manifest Memo

This document serves as a developer record documenting the diagnostic package visibility counts before and after introducing the broad package querying permission on the Android emulator environment.

---

## 1. Diagnostic Summary

| Permission State | `QUERY_ALL_PACKAGES` | Visibility Mode | Observed Package Count |
|:---|:---|:---|:---|
| **Initial (Default)** | ❌ Absent (`FALSE`) | **Limited** (Filtered) | **91 packages** |
| **Modified** |  Declared (`TRUE`) | **Broad** (Full Scan) | **254 packages** |

---

## 2. Default State (Limited Visibility)

In Android 11 (API level 30) and above, package visibility filtering is automatically enforced by the system to protect user privacy. Without explicit declarations, apps can only see a subset of system packages and packages they actively interact with.

### Manifest Configuration (Before)
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- No QUERY_ALL_PACKAGES is declared -->
    <application ...>
        ...
    </application>
</manifest>
```

* **Result:** **91** visible packages registered.

---

## 3. High-Visibility State (Broad Visibility)

Declaring the highly-privileged `QUERY_ALL_PACKAGES` permission allows the app to bypass Android's package visibility limitations and scan all installed packages on the system.

### Manifest Configuration (After)
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <application ...>
        ...
    </application>
</manifest>
```

* **Result:** **254** visible packages registered (full system list retrieved).

---

## 4. Understanding `tools:ignore="QueryAllPackagesPermission"`

### What is this attribute?
The attribute `tools:ignore="QueryAllPackagesPermission"` is an instructions flag processed by the Android **Lint** static analysis scanner.

### Why is it required?
1. **Google Play Policy Restrictions:** Since `QUERY_ALL_PACKAGES` exposes highly sensitive user information (their entire installed inventory of apps), Google Play restricts its usage to apps that absolutely require it for their core functionality (such as device managers, antivirus apps, or system search engines).
2. **Lint Warning / Error:** Due to these strict policies, Android's build system and Android Studio run a static check (Lint check) that outputs a warning/error when this permission is declared. 
3. **Suppression:** Adding `tools:ignore="QueryAllPackagesPermission"` explicitly tells the build system: 
   *"I am aware that this permission is restricted. Please suppress the warning/error and compile cleanly."*

> ⚠️ **Release Warning:** In a real production environment, keeping this permission active requires filling out a declarations form before publishing to Google Play, otherwise the app submission will be automatically rejected. For testing and debugging, suppressing the warning is the standard and correct way to proceed.
