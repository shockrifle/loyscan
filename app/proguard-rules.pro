# R8 rules for the app (release, and the `smoke` build type that tests it, #73). Libraries ship their own rules (Room,
# Hilt, kotlinx.serialization, Coil, CameraX, Glance, zxing-cpp); only what they can't know goes here.
# Why each rule, and how they're checked: wiki/decisions/2026-11-05-r8-smoke-test-on-a-device.md

# Navigation finds an enum route argument's class by its serial name (Class.forName), so its name must survive.
# Without this, opening StorePicker/CustomStore/CardForm crashes: "Cannot find class with name …BarcodeFormat?".
# RouteKeepRulesTest fails if a route gains an enum argument that isn't listed here.
-keepnames enum com.bdaniel.loyscan.domain.BarcodeFormat
