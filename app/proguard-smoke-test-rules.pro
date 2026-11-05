# R8 rules for the smoke test's APK (androidTest against the minified `smoke` build type, #73). Not used by the app.
# AndroidX Test refers to these optional classes, which neither the test nor the app has: compile-time annotations,
# and a coroutines adapter for the screenshot API, which the smoke test doesn't use.
-dontwarn androidx.concurrent.futures.SuspendToFutureAdapter
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
