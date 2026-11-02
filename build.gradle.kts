plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    // AGP 9 compiles Kotlin itself; declaring KGP here pins the Kotlin version it uses.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.spotless)
}

// Formatting: ./gradlew spotlessApply fixes it, CI runs spotlessCheck. Rules are in .editorconfig.
spotless {
    val ktlintRules = mapOf(
        "ktlint_code_style" to "android_studio",
        // Long lines read better here than wrapped ones; the code keeps them short where it can.
        "max_line_length" to "off",
        // Composables are named like classes.
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        // Keep expression bodies wrapped as written; with no line limit this rule would join them.
        "ktlint_standard_function-signature" to "disabled",
        "ktlint_standard_no-unused-imports" to "enabled",
    )
    kotlin {
        target("**/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
}
