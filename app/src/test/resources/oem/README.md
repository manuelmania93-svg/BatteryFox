# Fixture provenance

samsung-a20e.txt is a reduced battery-only excerpt of the public a20e dumpsys output posted on 2024-08-01:
https://xdaforums.com/t/anyway-to-reliably-check-battery-health-for-samsung-and-particularly-exynos-phones.4684265/
Unrelated feature flags and manufacturing-date lines are omitted. Values of the retained fields are unchanged.
It tests unsupported health (-1) and the categorical Android health enum (2), neither of which is a health percentage.

All other inline fixtures in OemFixtureTest are synthetic regressions. They are NOT captures from a device.
AOSP capacity labels follow the public Android dumpsys documentation/source formats:
https://developer.android.com/tools/dumpsys
No Red Magic, Qualcomm, Pixel or current One UI device validation is claimed. Private full bugreports must not be committed; reduce to battery fields, remove identifiers, record device/firmware and expected results before adding a real fixture.
