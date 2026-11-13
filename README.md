# AI Notes App

An Android note-taking application with productivity features and
AI-assisted text processing. The project is built with Kotlin and
Jetpack Compose, with a separate Spring Boot microservice used for
AI-related functionality.

**Repository:**
[Android_ToDO](https://github.com/Tung126Newbie/Android_ToDO)

## Overview

AI Notes App is a personal Android project focused on making note-taking
and everyday organization easier. It combines core note-management
features with additional tools such as text summarization, weather
suggestions, and alarms.

The Android application communicates with a separately running backend
service for AI-related features. The backend must be available and
correctly configured for those features to work.

## Features

-   **Note management:** Create, view, update, and delete notes (CRUD).
-   **Note search:** Find saved notes.
-   **Text summarization:** Request AI-assisted summaries of text
    through the backend service.
-   **Weather suggestions:** Get weather-related suggestions.
-   **Alarm management:** Set alarms/reminders from the application.

> **Backend note:** AI-assisted features depend on a separate Spring
> Boot microservice. They may not work when that service is stopped,
> unreachable, or configured with an incorrect base URL. Core app
> features that do not depend on the service can be used independently,
> subject to the current implementation.

## Tech Stack

### Android

-   Kotlin
-   Android SDK
-   Jetpack Compose and Material 3
-   AndroidX ViewModel and Lifecycle
-   Kotlin Coroutines and Flow
-   Room
-   Hilt for dependency injection
-   Retrofit and OkHttp for networking
-   DataStore
-   WorkManager
-   Google Play Services Location

### Backend

-   Java
-   Spring Boot
-   REST API

The backend is maintained and run separately from this Android
repository.

## Requirements

The project configuration currently specifies:

-   Android Studio with support for the project's Android Gradle Plugin
-   JDK 17
-   Android SDK 36
-   Minimum Android version: Android 7.0 (API 24)
-   An Android emulator or physical Android device

> Gradle and plugin versions are defined in the project-level Gradle
> files and `gradle/libs.versions.toml`. Use the Gradle wrapper included
> in this repository rather than installing a separate Gradle version.

## Getting Started

### 1. Clone the repository

``` bash
git clone https://github.com/Tung126Newbie/Android_ToDO.git
cd Android_ToDO
```

### 2. Open the project

1.  Launch Android Studio.
2.  Select **Open** and choose the cloned `Android_ToDO` folder.
3.  Allow Android Studio to sync the Gradle project.
4.  Install any Android SDK components requested by Android Studio.

### 3. Configure the backend (for AI-related features)

1.  Start the Spring Boot microservice on a machine reachable by the
    Android device or emulator.
2.  Find the API base URL setting used by the Android app's networking
    configuration.
3.  Set that base URL to the address and port where the backend is
    running.
4.  Make sure the Android device/emulator can reach that address.
5.  Start the backend before testing AI-assisted features.

**Important:** `localhost` inside an Android emulator refers to the
emulator itself, not the host computer. If the backend runs on the
development computer, use the appropriate host address for your emulator
or network setup. For a physical device, use the computer's reachable
LAN IP address and ensure both devices can communicate.

The exact backend URL, port, and configuration key depend on the Spring
Boot service and the current Android source configuration; do not commit
private keys, passwords, or other secrets to GitHub.

### 4. Build and run the Android app

1.  Select an emulator or connect an Android device with USB debugging
    enabled.
2.  Choose the `app` run configuration in Android Studio.
3.  Click **Run**.

You can also build from the project root using the Gradle wrapper:

**Windows PowerShell**

``` powershell
.\gradlew.bat assembleDebug
```

**macOS / Linux**

``` bash
./gradlew assembleDebug
```

The debug APK is normally generated under:

``` text
app/build/outputs/apk/debug/
```

## Repository Structure

``` text
Android_ToDO/
├── app/                    # Android application source and resources
├── gradle/                 # Gradle wrapper and version catalog
├── build.gradle.kts        # Root Gradle build configuration
├── settings.gradle.kts     # Gradle project settings
├── gradle.properties       # Gradle properties
├── gradlew                 # Gradle wrapper (macOS/Linux)
└── gradlew.bat             # Gradle wrapper (Windows)
```

## Current Project Status

The Android application includes note management, search, alarm, and
additional productivity features. AI-related functionality is
implemented as an integration with a separate Spring Boot service and
requires that service to be running and correctly configured.

This repository contains the Android project. The backend service is
maintained separately.

## Author

**Tùng Nguyễn**

-   GitHub: [@Tung126Newbie](https://github.com/Tung126Newbie)
-   Project:
    [Android_ToDO](https://github.com/Tung126Newbie/Android_ToDO)

------------------------------------------------------------------------

This project was developed as a personal learning project to practice
Android application development, Kotlin, Jetpack Compose, local data
management, and REST API integration.
