# Battery Fox

Battery Fox looks for battery-health evidence that Android does not expose
consistently through standard app APIs. It reads available Android telemetry,
offers local bug-report import for additional OEM/system data, and reports when
the device does not provide enough evidence for a health percentage.

## What a reading means

- Battery percentage, voltage, and temperature are Android-reported telemetry.
  Current is converted from Android's microamp unit when the raw magnitude
  supports it; small values use a labeled OEM-unit heuristic to preserve
  compatibility with devices that report milliamps. That heuristic is not
  independently validated and can be wrong on a device with very low current.
  Individual fields may be unavailable or differ by device.
- Battery health and capacity are only shown when a bug report contains a
  valid device-reported health value or both capacity fields needed to derive a
  ratio. The dashboard identifies which path was used and when the report was
  imported.
- A derived capacity ratio is still an estimate: a report's field labels and
  semantics can vary by manufacturer. It is not independently validated as
  usable battery capacity.
- Cycle count is not a battery-health percentage. A cycle count alone does not
  produce a health estimate.
- Health history records report imports on this device. It is a trend of
  reported values, not a continuous or laboratory measurement.
- BatteryFox can estimate capacity from Android's remaining-charge counter
  (available on Android versions before 14 as well as newer ones, when the
  device exposes it) while the dashboard is open. It needs at least three
  consistent windows spanning meaningful battery-level changes. Samples are
  taken while the dashboard is open and when the optional background monitor
  is running. The result
  and range are experimental; the charge counter may be unavailable,
  vendor-dependent, or noisy. Samples are retained locally for up to 60 days.
- The experimental current-pulse test is not calibrated against reference
  equipment and does not produce a health percentage.
- App foreground time is shown as usage history, not as measured battery
  drain. Ordinary apps cannot read all per-app battery attribution data.
- Ordinary apps cannot rewrite the phone's fuel-gauge/PMIC calibration
  registers. Battery Fox does not claim to calibrate them.

## Privacy

Bug-report ZIP files are parsed locally and are not uploaded by Battery Fox.
Bug reports can contain sensitive information unrelated to the battery; only
import a report you are comfortable processing on the device. Battery health
history and the most recent parsed fields are stored in the app's private
preferences.

The optional app-usage view requires the user to grant Android Usage Access.
Battery Fox uses the returned foreground-time totals to display screen time,
not a measured drain estimate.

## Compatibility and evidence

There is no promise that every Android phone exposes the same fields. OEM,
firmware, and Android-version differences affect the available telemetry and
bug-report format. Unsupported fields are left unavailable rather than filled
with generic battery-capacity or health defaults.

No physical-device compatibility or accuracy matrix has been established yet.
Before making accuracy claims, validate on named phone models and OS versions,
record which source worked, and compare health estimates to an appropriate
independent reference. Parser unit tests are useful regression checks, not
proof of accuracy on real phones.

| Validation area | Current evidence |
| --- | --- |
| JVM estimators and parsing | Tests cover parser fields, invalid/missing values, and charge-counter estimate windows; execution is handled by CI |
| Android build | Run `./gradlew assembleDebug` with JDK 17 and Android SDK 35 |
| Physical-device accuracy | Not yet validated |
| OEM/Android compatibility matrix | Not yet established |

## Build and test

Requirements: JDK 17, Android SDK 35, and the SDK path configured for Gradle
(for example, in a local `local.properties` file).

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Android Studio can configure the SDK path and launch the app on a connected
device or emulator. Device-specific behavior must still be verified on real
hardware.
