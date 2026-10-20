package com.example.data.analyzer

import com.example.data.model.BuildTarget

object WorkflowGenerator {

    /**
     * Generates a battle-tested, robust GitHub Actions workflow YAML for building the Android project.
     */
    fun generateWorkflowYaml(
        target: BuildTarget = BuildTarget.DEBUG_APK,
        javaVersion: String = "17"
    ): String {
        val artifactPath = when (target) {
            BuildTarget.DEBUG_APK -> "app/build/outputs/apk/debug/*.apk"
            BuildTarget.RELEASE_APK -> "app/build/outputs/apk/release/*.apk"
            BuildTarget.BUNDLE_AAB -> "app/build/outputs/bundle/release/*.aab"
        }

        return """
            name: CNDN Android APK Derleme

            on:
              push:
                branches: [ main, master, 'cndn/**', 'release/**' ]
              workflow_dispatch:
                inputs:
                  build_target:
                    description: 'Derleme Hedefi'
                    required: true
                    default: '${target.name}'
                    type: choice
                    options:
                      - DEBUG_APK
                      - RELEASE_APK
                      - BUNDLE_AAB

            concurrency:
              group: cndn-build-${'$'}{{ github.ref }}
              cancel-in-progress: true

            jobs:
              build:
                name: Android Derleme (${target.gradleCommand})
                runs-on: ubuntu-latest
                timeout-minutes: 30

                steps:
                  - name: Depo Kodunu Çek (Checkout)
                    uses: actions/checkout@v4
                    with:
                      fetch-depth: 1

                  - name: Java JDK $javaVersion Kurulumu
                    uses: actions/setup-java@v4
                    with:
                      distribution: 'temurin'
                      java-version: '$javaVersion'
                      cache: 'gradle'

                  - name: Gradle Wrapper Yetkilendirmesi ve Sürüm Uyumu
                    run: |
                      if [ -f "gradlew" ]; then
                        chmod +x gradlew
                      else
                        echo "gradlew bulunamadı, fallback gradle wrapper oluşturuluyor"
                        gradle wrapper --gradle-version 9.3.1 || true
                        chmod +x gradlew || true
                      fi
                      WRAPPER_PROP="gradle/wrapper/gradle-wrapper.properties"
                      if [ -f "${'$'}WRAPPER_PROP" ]; then
                        if grep -rq "9\.1\." gradle/libs.versions.toml app/build.gradle* build.gradle* 2>/dev/null; then
                          echo "AGP 9.x tespit edildi. Gradle sürümü 9.3.1 olarak ayarlanıyor..."
                          sed -i 's/gradle-[0-8]\.[0-9.]*-bin\.zip/gradle-9.3.1-bin.zip/g' "${'$'}WRAPPER_PROP" || true
                          sed -i 's/gradle-9\.[0-2]\.[0-9.]*-bin\.zip/gradle-9.3.1-bin.zip/g' "${'$'}WRAPPER_PROP" || true
                        fi
                      fi

                  - name: Android Projesini Derle (${target.gradleCommand})
                    id: gradle_build
                    run: |
                      ./gradlew ${target.gradleCommand} --stacktrace --no-daemon

                  - name: Derleme Çıktısını Kontrol Et
                    if: success()
                    run: |
                      echo "=== Oluşan Çıktılar ==="
                      ls -la app/build/outputs/apk/* 2>/dev/null || ls -la app/build/outputs/bundle/* 2>/dev/null || true

                  - name: APK / AAB Artefaktını Yükle
                    if: success()
                    uses: actions/upload-artifact@v4
                    with:
                      name: ${target.artifactName}
                      path: $artifactPath
                      retention-days: 14
                      if-no-files-found: error
        """.trimIndent()
    }
}
