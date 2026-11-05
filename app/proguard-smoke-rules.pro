# Extra R8 rules for the `smoke` build type only (#73), on top of release's. They keep classes the smoke test's APK
# takes from the app: a library both depend on is packaged once, in the app, so R8 must not remove or rename the parts
# the test runner calls. They only keep more; nothing here changes what the release build does.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
