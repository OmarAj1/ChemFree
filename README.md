# Chemical Translator

Scan or search food and cosmetic chemical ingredients to translate them into plain English, understand their manufacturer use-cases, and identify health risk levels.

## Features

- **Ingredient Scanning (OCR)**: Use your device camera to scan ingredient labels from food and cosmetic products, or paste the text directly.
- **Plain English Translations**: Demystifies complex chemical names (e.g., translates "Titanium Dioxide" to plain English safety terms).
- **Health Risk Index**: Color-coded risk indicators (Low, Moderate, High) and overall safety scoring based on recognized health index data.
- **Dietary Profile & Allergies**: Personalize your profile to receive warnings when scanned ingredients violate your dietary restrictions or trigger allergies.
- **Offline Capability**: Features an offline directory and local SQLite matching so you can scan and search ingredients remotely without an internet connection.
- **AI-Powered Translations**: Optionally fall back to Gemini AI for cutting-edge translations of unrecognized chemicals and additives.

## Architecture

This application is built natively for Android utilizing modern development architectures:
- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose using Material Design 3 guidelines
- **State Management**: Kotlin Coroutines and Flows, unified with standard `ViewModel` patterns.
- **Data Persistence**: Offline-first via local SQLite databases mapped to complex domain entities.

## Getting Started

1. Clone this repository.
2. Open the project in Android Studio (Giraffe or newer recommended).
3. Let Gradle sync and build dependencies.
4. Run the project on an Android Emulator or a physical device running Android (API Level 24+).

### Secrets Management

If you plan to utilize the Gemini AI features locally, you may need an API key. 
1. Copy `.env.example` to `.env` in the root directory.
2. Insert your Google Gemini API Key inside the `.env` file.

## Contributing

We welcome contributions! Please review our [Contributing Guidelines](CONTRIBUTING.md) and our [Code of Conduct](CODE_OF_CONDUCT.md) before submitting pull requests.

## License

This project is licensed under the [MIT License](LICENSE).
