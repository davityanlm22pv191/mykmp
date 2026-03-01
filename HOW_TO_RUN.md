# Запуск проекта Claude Chat (KMP) на разных платформах

Проект — Kotlin Multiplatform Compose клиент для Claude API.
Поддерживаемые платформы: Desktop (JVM), Android, iOS, Web (WASM), Web (JS).

---

## Общие требования

- **JDK 17+** (рекомендуется JDK 21)
- **Gradle 8.14+** — используется через wrapper (`./gradlew`), устанавливать отдельно не нужно
- **API-ключ Anthropic** (`sk-ant-...`) — нужен для работы с Claude API
- **VPN** — Anthropic API блокирует запросы с российских IP (возвращает 403). VPN обязателен для работы

### Структура проекта

```
kmp/
├── composeApp/           # Мультиплатформенный модуль
│   └── src/
│       ├── commonMain/   # Общий код для всех платформ
│       ├── androidMain/  # Android
│       ├── jvmMain/      # Desktop (JVM)
│       ├── iosMain/      # iOS
│       ├── jsMain/       # Web (JavaScript)
│       ├── wasmJsMain/   # Web (WebAssembly)
│       └── webMain/      # HTML/CSS ресурсы для веб-версий
├── iosApp/               # Xcode-проект для iOS
├── local.properties      # Локальные настройки (не в git)
└── gradle.properties     # Настройки Gradle
```

---

## 1. Desktop (JVM)

Самый простой способ запуска. Работает на macOS, Windows, Linux.

### Настройка API-ключа

Через переменную окружения:
```bash
export ANTHROPIC_API_KEY=sk-ant-api03-ваш-ключ
```

Или в IntelliJ IDEA:
1. Run → Edit Configurations
2. Найти конфигурацию `composeApp [run]` (или создать Gradle-конфигурацию)
3. Environment Variables → добавить `ANTHROPIC_API_KEY=sk-ant-api03-ваш-ключ`

### Запуск

```bash
./gradlew :composeApp:run
```

Откроется окно 700x900 с заголовком "Claude Chat".

### Где хранятся данные

Файлы чата и настройки сохраняются в `~/.claude-chat/`.

### Сборка дистрибутива

```bash
# macOS — DMG
./gradlew :composeApp:packageDmg

# Windows — MSI
./gradlew :composeApp:packageMsi

# Linux — Deb
./gradlew :composeApp:packageDeb
```

---

## 2. Android

### Предварительные требования

- **Android SDK** — установить через Android Studio или SDK Manager
- **Эмулятор или физическое устройство** с API Level 24+ (Android 7.0+)
- **VPN на устройстве** — без VPN API вернёт 403

### Настройка

Файл `local.properties` в корне проекта (создать, если нет):
```properties
sdk.dir=/путь/к/Android/sdk
ANTHROPIC_API_KEY=sk-ant-api03-ваш-ключ
```

На macOS путь к SDK обычно: `/Users/<username>/Library/Android/sdk`
На Windows: `C:\\Users\\<username>\\AppData\\Local\\Android\\Sdk`
На Linux: `/home/<username>/Android/Sdk`

API-ключ попадает в `BuildConfig.ANTHROPIC_API_KEY` при сборке.

### Запуск

Через терминал:
```bash
# Сборка debug APK
./gradlew :composeApp:assembleDebug

# Установка на подключённое устройство/эмулятор
./gradlew :composeApp:installDebug
```

Через IntelliJ IDEA / Android Studio:
1. Выбрать конфигурацию `composeApp` с иконкой Android
2. Выбрать устройство
3. Нажать Run (Shift+F10)

### Отладка HTTP-запросов (Chucker)

В debug-сборке встроен **Chucker 4.3.0** — HTTP-инспектор. После любого сетевого запроса в уведомлениях Android появится запись с деталями запроса/ответа. Нажмите на уведомление, чтобы увидеть:
- URL, заголовки, тело запроса
- Статус код, тело ответа
- Время выполнения

В release-сборке Chucker автоматически отключён (подставляется no-op).

### APK

После сборки APK находится в:
```
composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

---

## 3. iOS

### Предварительные требования

- **macOS** с установленным **Xcode 15+**
- **CocoaPods** (если используется)
- Устройство iOS или симулятор

### Настройка API-ключа

1. Открыть проект в Xcode:
   ```bash
   open iosApp/iosApp.xcodeproj
   ```
2. Product → Scheme → Edit Scheme (Cmd+<)
3. В разделе Run → Arguments → Environment Variables
4. Добавить: `ANTHROPIC_API_KEY` = `sk-ant-api03-ваш-ключ`

### Запуск

Через Xcode:
1. Открыть `iosApp/iosApp.xcodeproj`
2. Выбрать таргет `iosApp`
3. Выбрать симулятор (iPhone 15 и т.д.) или физическое устройство
4. Нажать Run (Cmd+R)

Поддерживаемые архитектуры:
- `iosArm64` — физические устройства
- `iosSimulatorArm64` — симулятор на Apple Silicon (M1/M2/M3)

### Архитектура

Swift-код (`iOSApp.swift`) → `ContentView.swift` (SwiftUI обёртка) → `MainViewController.kt` (Kotlin) → `App()` (Compose).

Kotlin-код компилируется в статический фреймворк `ComposeApp.framework`.

---

## 4. Web (WASM) — рекомендуемый для веба

WebAssembly-версия. Быстрее JS-версии, но требует современный браузер.

### Запуск

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

Откроется браузер на `http://localhost:8080/`.

### Ограничения

- **API-ключ**: на клиенте нельзя хранить ключ безопасно. Текущая реализация возвращает пустую строку. Для production нужен backend-proxy
- Поддержка браузеров: Chrome 119+, Firefox 120+, Safari 18+, Edge 119+
- Данные хранятся в `localStorage` браузера

### Продакшен-сборка

```bash
./gradlew :composeApp:wasmJsBrowserProductionWebpack
```

Артефакты в `composeApp/build/dist/wasmJs/productionExecutable/`.

---

## 5. Web (JS) — legacy

JavaScript-версия для старых браузеров, которые не поддерживают WASM.

### Запуск

```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

Откроется браузер на `http://localhost:8080/`.

### Ограничения

Те же, что у WASM: нет API-ключа на клиенте, localStorage для хранения.

### Продакшен-сборка

```bash
./gradlew :composeApp:jsBrowserProductionWebpack
```

Артефакты в `composeApp/build/dist/js/productionExecutable/`.

---

## Известные проблемы

### Гео-блокировка Anthropic API (403)
Anthropic блокирует запросы с российских IP. Ответ: `403 Request not allowed`.
Решение: использовать VPN. Приложение показывает анимированный баннер "Включите VPN" при обнаружении блокировки.

### SLF4J на Android
Ktor по умолчанию логирует через SLF4J, которого нет на Android. В проекте настроен кастомный Logger для вывода в Logcat.

### Web-версии без API-ключа
JS и WASM таргеты не имеют реализации API-ключа (возвращают пустую строку). Для работы нужен backend-proxy, который будет проксировать запросы к Anthropic API.

### iOS — первая сборка
Первая сборка iOS может занять значительное время из-за компиляции Kotlin-фреймворка. Последующие сборки быстрее (инкрементальная компиляция).

---

## Быстрая справка: все команды

| Платформа | Команда запуска |
|-----------|----------------|
| Desktop (JVM) | `./gradlew :composeApp:run` |
| Android (сборка) | `./gradlew :composeApp:assembleDebug` |
| Android (установка) | `./gradlew :composeApp:installDebug` |
| iOS | Открыть `iosApp/iosApp.xcodeproj` в Xcode → Run |
| Web (WASM) | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Web (JS) | `./gradlew :composeApp:jsBrowserDevelopmentRun` |
| Desktop DMG | `./gradlew :composeApp:packageDmg` |
| Desktop MSI | `./gradlew :composeApp:packageMsi` |
| Desktop Deb | `./gradlew :composeApp:packageDeb` |

---

## Конфигурация API-ключа: сводная таблица

| Платформа | Как настроить ключ |
|-----------|-------------------|
| Desktop (JVM) | Env var `ANTHROPIC_API_KEY` (терминал или IntelliJ Run Config) |
| Android | `local.properties` → `ANTHROPIC_API_KEY=...` (попадает в BuildConfig) |
| iOS | Xcode → Scheme → Run → Environment Variables |
| Web (WASM/JS) | Не реализовано (нужен backend-proxy) |
