# Технологический стек M-POS

Решение ADR-002 от 2026-10-24. Цель — самостоятельное нативное Android-приложение на Kotlin с функциональностью [M-POS-Android-Web](https://github.com/mendelev-main/M-POS-Android-Web/tree/1682c88334e6fb13586ce4b530157c59ca062e43): товары, кассовая зона, продажи, склад, смены, клиенты, лояльность, отчёты, оборудование и сайт.

Это выбранный целевой стек. **Установлен bootstrap 00; в 00.1 подключены обновления:** Lifecycle ViewModel/Compose 2.9.1, core-ktx 1.16.0 и OkHttp 4.12.0, а также org.json/MockWebServer в tests. Updater использует платформенный org.json для небольшого release manifest; kotlinx.serialization для предметных DTO остаётся будущим компонентом. В01a подключены Room2.8.4/KSP2.2.21-2.0.4, bundled Manrope и shared Compose theme/components; Room schema1 экспортирована. Остальные будущие компоненты (Hilt/DataStore/WorkManager/Coil/serialization) пока не подключены. Строка «подключить» означает архитектурное решение для будущего этапа, а не наличие зависимости или готовой функции. Функциональное соответствие проверяется по [PARITY.md](PARITY.md), происхождение требований — по [SOURCE.md](SOURCE.md).

## 1. Платформа и установленная база

| Компонент | Зафиксировано в репозитории | Назначение |
|---|---|---|
| Платформа | Android 9+, minSdk 28 | Автономная работа на Android-планшетах |
| Язык | Kotlin 2.2.21 | UI, бизнес-логика, данные и платформенные адаптеры |
| UI | Jetpack Compose, Compose BOM 2025.10.00, Material 3 | Нативные экраны и общие компоненты |
| Activity | activity-compose 1.11.0, single Activity | Хост Compose и Android lifecycle |
| Сборка | Gradle Wrapper 8.13, AGP 8.13.2, Kotlin DSL, JDK 17 | Воспроизводимая сборка |
| SDK | compileSdk/targetSdk 36, Build Tools 35.0.0 | Закреплённая проверенная сборочная база |
| Release | R8 и resource shrinking | Оптимизация APK |
| Тестовая база | JUnit 4.13.2, Compose UI Test, AndroidX Test | JVM и instrumentation tests; доменные JVM tests появятся в 01 |
| Автоматизация | Python 3, GitHub Actions, repository harness | Проверки, журнал evidence и выпуск APK |
| Identity | release `com.mendelev.mpos`, debug `.debug` | Независимая установка рядом с Web `.web` |

Точные установленные зависимости — в `app/build.gradle.kts` и корневом `build.gradle.kts`. Эта таблица не означает «последние доступные версии»: база уже прошла CI. Новые зависимости01a указаны выше; дальнейшие обновления закрепляются отдельной проверкой.

## 2. Целевой стек приложения

| Область | Выбранная технология | Какую задачу M-POS решает | Когда подключать |
|---|---|---|---|
| Экранные состояния | AndroidX Lifecycle ViewModel, StateFlow, lifecycle-aware Compose collection | Состояния каталога, корзины, форм и ошибок; восстановление после смены конфигурации | 00.1 для updater; 01 для features |
| Асинхронность | Kotlin Coroutines, Flow, structured concurrency | IO, наблюдение за БД, отмена задач, ограничение времени операций | 00.1 для updater; 01 для features |
| Навигация | AndroidX Navigation Compose с типизированными routes | Товары, редакторы, рабочая зона, настройки; передача ID записи между экранами | 01, развитие в 02 |
| Зависимости | Hilt, constructor injection | ViewModel, repositories, DB, workers и замена адаптеров в тестах | 01 |
| Локальная БД | AndroidX Room поверх SQLite, DAO, transactions, schema export и migrations | Товары, категории, рецептуры; далее чеки, склад, смены, клиенты и outbox | 01 |
| Генерация кода | KSP для Room/Hilt с совместимыми версиями processors | Проверяемые DAO и DI без ручного service locator | 01 |
| Предпочтения | Jetpack DataStore Preferences | Тема, раскладка интерфейса и неоперационные настройки устройства | По первой потребности в 01–02; расширение в 08–09 |
| JSON | kotlinx.serialization-json и Kotlin serialization plugin | Версионированные DTO, навигация, backup и сетевые контракты | 01; backup в 07, сеть в 08–09 |
| Изображения | Coil Compose, локальные app-private файлы, Photo Picker/Activity Result APIs | Фото товаров, thumbnails, освобождение ресурсов и отмена загрузки | 01 |
| HTTP | OkHttp + Okio, типизированные Kotlin API adapters, kotlinx.serialization | Backend HTTPS, Telegram multipart и скачивание файлов | 00.1 updater; 08–09 integrations |
| Входящие события | Потоковое чтение OkHttp/Okio + отдельный SSE decoder | Заказы сайта, event/id/retry, Last-Event-ID, reconnect и дедупликация | 09 |
| Фоновая доставка | WorkManager CoroutineWorker + durable outbox в Room | Повторяемые отправки при восстановлении сети, переживающие restart | 08–09; структура outbox с 03 |
| Деньги и количества | java.math.BigDecimal, Long minor units, явные value types и converters | Десятичные расчёты без Double, дробный расход ингредиентов | С 01, финансовые правила в 03 |
| Даты и время | java.time.Instant, ZoneId, Clock через DI | История операций, смены, business day и воспроизводимые тесты | По первой датированной сущности |
| Защита секретов | Android Keystore + AES-GCM, app-private encrypted payload | Device credentials/Telegram token, когда появятся интеграции | При первом сохраняемом секрете, до 08–09 |

Для всех строк «подключать» конкретные версии выбираются и проверяются в соответствующей реализации. Совместимость проверять с Kotlin 2.2.21, AGP, minSdk 28, KSP, Hilt и Room; версии новых dependencies закреплять через `gradle/libs.versions.toml` при начале 01. Никаких `+`, floating snapshots или незафиксированных обновлений. Если нужна смена сборочной базы — отдельное решение и полный CI.

[Рекомендации Android](https://developer.android.com/topic/architecture/recommendations) служат основанием для слоёв, управления состоянием и DI. Для Kotlin используются [coroutines](https://kotlinlang.org/docs/coroutines-guide.html) и [serialization](https://kotlinlang.org/docs/serialization.html); Hilt описан в [официальной документации](https://developer.android.com/training/dependency-injection/hilt-android). Выбор этих компонентов — решение проекта, а не заявление, что Google требует именно весь этот набор.

## 3. Границы слоёв и структура

```text
Compose Screen → ViewModel → Use case → Repository → Room / File store
                                              └──→ API / Printer adapter
                         transaction → persisted outbox → Worker / dispatcher
```

Поток данных направлен к экрану через immutable UiState/Flow; действия пользователя идут в ViewModel и use cases. Финансовые расчёты находятся в чистом Kotlin domain, без Context/Compose/Room entities. DTO транспорта, DB entities и domain models разделены там, где отличаются их контракты. [Типизированная навигация](https://developer.android.com/guide/navigation/use-graph/navigate) передаёт ID, а не целые изменяемые товары или корзины.

На старте один Gradle module `:app` с пакетами:

```text
com.mendelev.mpos/
  app/                 Activity, navigation, DI composition
  core/                money, quantities, time, common UI
  domain/              models, repository contracts, use cases
  data/local/          Room, DAO, migrations
  data/preferences/    DataStore
  data/files/          photos, backup staging
  data/network/        HTTP/SSE DTO and adapters (08–09)
  feature/products/    товары и редакторы (01)
  feature/workspace/   плитки, папки, текущие/отложенные чеки (02)
  integration/         print, reports, Telegram (08)
```

Это карта будущего кода, не команда создать пустые папки заранее. Остальные feature-пакеты добавлять с этапами. Gradle modules выделять по доказанной границе/потребности сборки, а не по числу экранов. Не вводить одновременно второй DI-контейнер, второй HTTP-стек или отдельную MVI-библиотеку: ViewModel + UDF достаточно. В этой архитектуре HTTP-адаптеры пишутся поверх OkHttp; Retrofit/Ktor не требуются для базового плана.

## 4. Хранение и расчёты кассы

Room хранит рабочие сущности и долговечные сессии; DataStore не используется для продаж, складских движений и резервов. Закрытая продажа хранит исторические цены, скидки, состав и stockConsumption. Snapshot/движения/обновление сессии/запись outbox выполняются одной локальной transaction. UI сообщает успех только после commit; печать и HTTP выполняются после него. Сетевой сбой не запускает продажу повторно.

Outbox хранит намерение и idempotency key; WorkManager планирует исполнение и не становится вторым журналом финансов. У каждого эффекта свои правила повтора: сетевой запрос с серверной идемпотентностью и физическая печать имеют разные гарантии. Если принтер оборвал соединение, автоматический retry не доказывает отсутствие первого отпечатка: UI должен показывать неопределённый результат и явную повторную печать.

BigDecimal преобразуется в БД без потери точности — например, канонической decimal string через Room converter. Денежные итоги в minor units получают scale/rounding только по подтверждённой политике 03. SQL-агрегацию decimal string не использовать как арифметику денег. Количества и единицы — отдельные типы; UI-форматирование не меняет сохранённое значение. UUID/строковые IDs стабильны. Clock внедряется, даты событий сохраняются как Instant; часовой пояс бизнес-дня задаётся явно.

[Локальный источник данных и очереди синхронизации](https://developer.android.com/topic/architecture/data-layer/offline-first) соответствуют offline-first подходу. [Экспорт схем и migration tests Room](https://developer.android.com/training/data-storage/room/migrating-db-versions) нужны с первого изменения схемы; destructive fallback для рабочих данных запрещён. Предметные правила — в [DATA.md](DATA.md).

## 5. Фото, файлы и перенос данных

Фото выбираются через системный Photo Picker там, где он доступен; на других поддерживаемых устройствах используется SAF fallback через Activity Result API. App-private file store хранит собственную копию и metadata; Coil отображает её в Compose. Не полагаться на вечную доступность внешнего URI и не запрашивать доступ ко всей медиатеке ради одного фото. Импорт и работа с большими изображениями ограничивают размеры/память. [Coil Compose](https://coil-kt.github.io/coil/compose/) — выбранный UI image loader.

Backup: версионированный JSON через kotlinx.serialization, фото в согласованном формате контейнера, SAF для выбора/создания файла. Точный совместимый контейнер Web v13 исследуется в 07; ZIP не объявляется входным форматом Web без проверки. Фото проходят staging, связи и версии проверяются до замены рабочих данных. Для больших наборов использовать ограниченное по памяти чтение/запись и проверенные fixtures.

Обмен готовыми файлами — FileProvider с `${applicationId}` и Android Sharesheet. PDF/XLSX/backup не требуют WebView. Формат и политика переноса рабочего Web backup — часть этапа 07, а не автоматическая общая БД двух приложений.

## 6. Оборудование, отчёты и Telegram

| Функция Web | Нативная реализация | Существенная проверка |
|---|---|---|
| Чек и кухня | ESC/POS encoder, Canvas/Bitmap для растрового текста, TCP Socket с IO dispatcher и timeout | Кириллица, ширина бумаги, порядок команд и физический образец |
| Кассовый ящик | Выделенная ESC/POS-команда через printer adapter | Права, cash-событие, отсутствие повторного импульса из-за копий чека |
| PDF | Android PdfDocument, Canvas, TextPaint/StaticLayout | Пагинация, длинные строки, кириллица, соответствие итогов |
| XLSX | Ограниченный OOXML writer на Kotlin + ZipOutputStream/XML | Числовые ячейки, XML escaping, структура workbook и открытие в Excel/LibreOffice |
| Системная печать | Android Print Framework | Выбор печати пользователем и корректный PDF layout |
| Telegram | OkHttp HTTPS/multipart, PNG/PDF из native report renderer | Test destination, timeout, retry policy, данные отправки после commit |

PDF/XLSX/ESC-POS уже имеют Kotlin-реализации в Web-репозитории: `documents/MPosDocumentCanvas.kt`, `MPosWarehouseWorkbook.kt`, `print/EscPosPrinter.kt`. Их можно адаптировать через ревью и тесты при 08, заменив bridge/JSONObject входы на domain/report DTO. Они не считаются уже перенесёнными. Для текущего ограниченного XLSX-экспорта не нужен полный офисный движок; импорт произвольных Excel-файлов этим решением не обещается.

[PdfDocument](https://developer.android.com/reference/android/graphics/pdf/PdfDocument) — платформенный API. Включение Bluetooth/USB, платёжного терминала или фискального SDK требует отдельного контракта устройства: существующий parity охватывает LAN ESC/POS, но не означает сертифицированный эквайринг или фискализацию.

## 7. Backend и сайт

Нативное приложение интегрируется с существующим backend по его контрактам. Стек сервера/сайта здесь не переопределяется. Backend adapters и Telegram используют общую настроенную инфраструктуру OkHttp, с отдельными клиентскими политиками таймаутов, credentials и redirect. JSON DTO валидируются на границе; TLS проверяется штатно, токены не выводятся в логи.

SSE: отдельный streaming decoder и connection coordinator. Проверить multiline data, event/id/retry, cursor persistence, Last-Event-ID, reconnect/backoff, отмену и повтор доставки. Получение события и изменение локального заказа должны быть идемпотентны. Меню/фото публикуются вручную; автоматически выполняются только согласованные availability/order/outbox операции. Локальная касса не зависит от доступности backend.

WorkManager используется для конечных persistent задач с constraints/backoff; постоянное SSE-соединение обслуживается координатором активного приложения. [Планировщик фоновой работы](https://developer.android.com/develop/background-work/background-tasks/persistent) не обещает непрерывное мгновенное получение заказов при остановленном приложении. Если это потребуется, до реализации отдельно согласовать background lifecycle и поддержку backend; push/foreground service не добавлять как молчаливое допущение.

## 8. Безопасность и наблюдаемость

Роли проверяются в use cases. Сессия администратора — runtime-only. Способ локальной аутентификации и параметры PIN/KDF уточняются в 05. Для интеграционных секретов ключ AES-GCM находится в Android Keystore, зашифрованные значения — в app-private storage. [Keystore](https://developer.android.com/privacy-and-security/keystore) хранит криптографические ключи; обычный [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) не объявляется автоматически зашифрованным.

Клиентская SQLite-БД и backup не получают обещание полного шифрования от наличия Keystore: если оно потребуется, отдельно выбрать модель ключей/восстановления и проверить миграции. Signing JKS из GitHub Secrets — ключ выпуска APK; он не является ключом шифрования данных на планшете.

Диагностика: Android Logcat и небольшой Kotlin logging adapter с correlation/operation IDs, безопасные сообщения в UI и состояниях outbox. Пароли, токены, backup payload и контакты клиента не логируются. Подключение внешнего crash/telemetry сервиса не входит в базовый стек; сначала определить необходимость и состав передаваемых данных.

## 9. Тестирование и агентская разработка

| Контур | Инструменты | Когда |
|---|---|---|
| Domain | JUnit + kotlinx-coroutines-test, fake repositories/Clock | С 01: decimal/units, рецептуры, резервы, деньги, повтор команд |
| Persistence | Room testing / MigrationTestHelper, exported schemas, instrumentation | С 01: атомарность, связи, миграции, restart |
| UI | Compose UI Test + AndroidX Test, screenshot fixtures по необходимости | С 01: формы/состояния, затем landscape/portrait и касса |
| Network | MockWebServer выбранной версии OkHttp, fake SSE/Telegram | 08–09: HTTP, timeout, abort, reconnect, повтор событий |
| Периферия/документы | Fake printer transport, byte/структурные fixtures, PNG/PDF previews | 08; физическая приёмка отдельно |
| Repository | AGENTS.md, `.agents/skills/`, Python harness, планы/context/build-log | Уже подключён |
| CI/release | GitHub Actions, Android lint, Gradle, emulator, R8, apksigner | Уже подключён |

Новые зависимости и fake adapters добавляются вместе с наблюдаемыми сценариями, а не ради количества библиотек. Долгий UI/Room/network сценарий не заменяется поиском строк в Kotlin. Детали gates — в [TESTING.md](TESTING.md), identity/ключа/версий — в [RELEASE.md](RELEASE.md).

## 10. Подключение по этапам

- **01 — товары:** ViewModel/Coroutines, Room/KSP, Hilt, Navigation Compose, serialization, Coil, file/picker adapters; DataStore при первой preference. Сначала каталог с сохранением, затем рецептуры и модификаторы.
- **02 — рабочая зона:** тот же стек, persistent cart/parked sessions и layout; новый технологический фреймворк не нужен.
- **03–06 — продажи, склад, смены, клиенты:** расширение Kotlin domain/Room transactions и snapshots, права и тесты. Таблица outbox с 03, без преждевременных реальных отправок.
- **07 — перенос:** JSON schemas, SAF, photo staging и тесты backup/import.
- **08 — устройство:** OkHttp/WorkManager, native ESC/POS, reports, Telegram, защищённые credentials.
- **09 — сайт:** backend DTO, streaming SSE, coordinator, sync policy и настройки endpoint.

Стек считается реализованным по мере появления кода и evidence; целевая архитектура сама по себе не закрывает acceptance. Полное соответствие включает поведение, данные, отказы и физические интеграции, а не только наличие аналогичных экранов.
