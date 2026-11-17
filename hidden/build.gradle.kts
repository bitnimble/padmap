// Compile-only stubs of hidden framework classes; the device's own classes are used at runtime.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.padmap.hidden"
    compileSdk = 37
    defaultConfig {
        minSdk = 30
    }
}
