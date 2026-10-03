# BatteryFox

[![Build and tests](https://github.com/manuelmania93-svg/BatteryFox/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/manuelmania93-svg/BatteryFox/actions/workflows/build.yml)

**BatteryFox searches for battery evidence that Android's standard battery
screen may not show.** It combines Android telemetry, locally imported bug
reports, available OEM diagnostics, and an experimental capacity estimator
that learns from charge-counter readings over time.

The goal is to give people a more useful picture of their battery—not to imply
that every Android phone exposes the same data or that an estimate is a
laboratory measurement.

## Features

- **Live telemetry:** battery percentage, voltage, temperature, current, and
  power when Android and the device provide usable readings.
- **Bug-report analysis:** import a bug-report ZIP and look for supported
  battery-health, capacity, and cycle-count fields. Parsing happens locally.
- **Learned capacity:** estimate capacity from repeated Android charge-counter
  and percentage observations. The estimator requires multiple consistent
  charge-change windows and displays a range rather than treating one sample
  as definitive.
- **OEM diagnostics:** try opening selected manufacturer or Android diagnostic
  screens. Availability and access depend on the phone and OS.
- **Optional monitor:** a user-started foreground notification can collect
  telemetry and charge-counter samples while running.
- **Usage history:** optionally display foreground app time returned by
  Android Usage Access. This is screen-time history, not measured app battery
  drain.
- **Evidence-first results:** show the reported source when available and
  leave health unavailable when there is not enough evidence.

## What the numbers mean

BatteryFox keeps distinct evidence paths separate:

- **Device-reported health** is shown when a supported bug-report field
  explicitly contains a health value.
- **Capacity-ratio health** is an estimate derived only when the imported
  report contains both estimated and design capacity values. Vendor field
  meanings may differ.
- **Learned capacity** is an experimental BatteryFox estimate inferred from
  repeated charge-counter changes. It is not an OEM-reported health percentage
  and does not by itself determine battery health.
- **Unavailable** means the phone did not provide enough usable evidence.

The Android charge counter is not exposed consistently across devices. The
learned-capacity estimate requires at least three consistent observations and
meaningful charge-level changes; its range is a measure of observed sample
spread, not a certified confidence interval. Android percentage rounding,
charge-counter noise, charging state, and firmware behavior can all affect
the result.

Cycle count and device age are not battery-health measurements. The
experimental current-pulse feature reports a voltage/current response only;
it is not calibrated against reference equipment. Ordinary apps cannot rewrite
the phone's fuel-gauge or PMIC calibration registers, and BatteryFox does not
claim to do so.

## Compatibility and validation

- **Minimum Android version:** Android 8.0 (API 26).
- **Build target:** Android SDK 35.
- **Device coverage:** no physical-device compatibility or accuracy matrix has
  been established yet. OEM, firmware, and Android-version differences affect
  available telemetry and bug-report formats.
- **Evidence:** automated JVM tests cover supported parser examples, invalid
  and missing values, telemetry normalization, and estimator behavior. Passing
  tests protects code behavior; it is not proof of accuracy on physical
  phones.

Before making accuracy claims, BatteryFox needs validation on named phone
models and OS versions against suitable independent references. Unsupported
readings should remain unavailable instead of being filled with generic
capacity or health defaults.

## Privacy

- Bug-report ZIP files are parsed on-device and are not uploaded by BatteryFox.
  Bug reports may contain sensitive information unrelated to batteries; only
  import a report you are comfortable processing locally.
- Health history and charge-counter samples are stored in the app's private
  preferences. Charge samples are retained locally for up to 60 days.
- The optional app-usage view requires the user to grant Android Usage Access.
  It displays foreground time, not measured battery consumption.
- The monitor is user-started and can be stopped from the dashboard.

## Build, test, and run

Requirements: JDK 17, Android SDK 35, and the Android SDK path configured for
Gradle (for example, in a local `local.properties` file).

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Android Studio can also open the project and run it on an emulator or connected
device. Emulator runs cannot establish device-specific battery accuracy.

## Contributing device evidence

Compatibility reports are most useful when they include:

- phone manufacturer and model;
- Android version and OEM software version;
- which reading or import path was tested;
- whether the field was present and plausible;
- comparison method and observed error, if a suitable reference was used.

Do not post raw bug reports publicly: they may include personal or identifying
device information. Share only the minimum redacted battery lines needed to
reproduce a parser case.
