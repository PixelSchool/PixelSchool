# PixelSchool 🎓

<div align="center">

![Android CI](https://github.com/Refyrd/PixelSchool/actions/workflows/build.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.00-4285F4.svg?logo=jetpackcompose)
![Design System](https://img.shields.io/badge/Material%203-Expressive-FF6F00.svg?logo=materialdesign)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?logo=android)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

**Современный, чистый и выразительный Android-клиент для школьного электронного дневника (edu.rk.gov.ru / ElJur).**

*Без рекламы, без лишнего визуального шума, с плавной анимацией и продуманным Material 3 Expressive дизайном.*

</div>

---

## 📱 О проекте

**PixelSchool** переосмысляет опыт взаимодействия со школьным электронным журналом Крыма (ГИС РК «Образование» / ElJur). Приложение фокусируется на самом важном: читаемом расписании уроков, удобном трекере домашних заданий, сводной четвертной ведомости оценок и гармоничном интерфейсе с динамической палитрой Material You.

### ✨ Ключевые возможности

- 🔐 **Безопасная авторизация через Госуслуги (ЕСИА):**
  - Поддержка бесшовного веб-входа и автоматического перехвата защищенной сессии.
  - Полноценная поддержка сертификатов Национального удостоверяющего центра Минцифры РФ (Russian Root CA) без сбоев SSL.
  - Режим оффлайн/демо для быстрого ознакомления с интерфейсом.

- 📅 **Умное расписание уроков:**
  - Наглядное понедельное и посуточное переключение с автоматическим выбором понедельника при переходе на следующую неделю.
  - Карточки уроков с предметом, временем, кабинетом, преподавателем и темой занятия.
  - Поддержка прикрепленных файлов и ссылок с открытием во внешних приложениях.

- 📝 **Трекер домашних заданий:**
  - Удобная фильтрация по дням недели и статусу выполнения (*Все / В процессе / Выполнено*).
  - Индикатор общего прогресса выполнения заданий на выбранный день.
  - Быстрое переключение статуса выполнения в один тап.

- 📊 **Сводная ведомость оценок:**
  - Динамический парсинг оценок со страницы сводной ведомости за всю четверть и за год.
  - Выразительные M3-бейджи оценок с акцентной цветовой гаммой для пятерок, четверок и троек.
  - Карточка среднего балла (GPA) по четвертям и предметам с детальным расчетом веса отметок.
  - Адаптивный скроллируемый таб-бар с Material 3 Expressive пилюлей-индикатором.

- 🎨 **Material 3 Expressive & Typography:**
  - Фирменный шрифт **Google Sans Flex** с полной поддержкой кириллицы.
  - Динамические цвета Material You (начиная с Android 12) и адаптированная темная тема.
  - Строгий 12.dp вертикальный ритм и скругления `CornerShape(20.dp - 28.dp)`.

---

## 🛠 Технологический стек

| Категория | Технологии |
|---|---|
| **Язык** | Kotlin 2.0 (K2 compiler enabled) |
| **UI Framework** | Jetpack Compose (Compose BOM 2024.10.00), Material 3 Expressive |
| **Архитектура** | Modern Android Architecture (MVVM, Single Source of Truth, Kotlin Coroutines, StateFlow) |
| **Сеть и парсинг** | OkHttp 4.12, Jsoup (HTML/DOM parser), Android Keystore TrustManager |
| **CI / CD** | GitHub Actions (автоматическая сборка APK при каждом пуше в `main`) |
| **Подпись релизов** | APK Signature Schemes v1, v2, v3, v4 |

---

## 🚀 Сборка и запуск

### Требования
- JDK 17 (Temurin или OpenJDK)
- Android SDK 35 (minSdk: 26, targetSdk: 35)
- Gradle 8.10.2 (входит в проект через Gradle Wrapper)

### Локальная сборка

```bash
# Клонирование репозитория
git clone git@github.com:Refyrd/PixelSchool.git
cd PixelSchool

# Сборка подписанного Release APK
./gradlew assembleRelease

# Установка на подключенное устройство через ADB
adb install -r app/build/outputs/apk/release/app-release.apk
```

Готовый APK сохраняется по пути:
```
app/build/outputs/apk/release/app-release.apk
```

---

## ⚙️ Непрерывная интеграция (GitHub Actions)

В репозитории настроен автоматический пайплайн [GitHub Actions](https://github.com/Refyrd/PixelSchool/actions):
- При каждом `git push` в ветку `main` запускается сборка подписанного Release APK в изолированном окружении Ubuntu.
- Проверяются компиляция Kotlin-кода, подпись и валидность всех ресурсов.
- Сгенерированный артефакт `PixelSchool-release-apk` доступен для скачивания на странице запуска Actions.
- Workflow также можно запускать вручную через кнопку **Run workflow** (`workflow_dispatch`).

---

## 🛡️ Безопасность и конфиденциальность

- **Zero PII (Личные данные не хранятся в репозитории):** Все сессии, токены и ФИО обрабатываются исключительно локально на клиентском устройстве через защищенный `Encrypted/Private SharedPreferences`.
- **Подписанные коммиты:** Все коммиты в репозитории криптографически подписаны GPG-ключом автора.

---

## 📄 Лицензия

Проект распространяется под лицензией [MIT License](LICENSE).
