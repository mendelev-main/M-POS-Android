# Каталог 01c — модификаторы и фото

Статус: ci-verified API28/35 на eb40b22/run40098187895; физическая приёмка pending. Рабочая ветка develop. Это продолжение01; корзина02 и финансовые операции03 не входят.

## Источник и уточнения

Pinned Web1682c88334e6fb13586ce4b530157c59ca062e43: product-configuration.js normalizeModifierGroup/validateModifierGroups/renderModifierGroups; product-photo.js; Android media/ProductPhotoManager.kt/ProductImageStore.kt и MainActivity picker. Regression reference — tests/source-product-stock.test.cjs: modifier stock/composite около719–737, photo/storage около933–949, modifier deletion около2293.

В Web normalizeModifierGroup исправляет min/max до validate, поэтому min2/max1 может стать1/1. Native следует ранее утверждённому MOD-01: отклоняет исходные невалидные ограничения. Границы native: до20 групп, до100 вариантов/группу, max1..100; min0..max и не больше числа вариантов. Невыполнимый min не исправляется молча.

## Модификаторы — P06

Группа имеет стабильный ID/имя/min/max/order; вариант — стабильный ID/productId/posName/quantity/priceDelta/order. Имя группы1..100 символов, POS подпись до200 (пустая означает имя товара). Группы могут относиться к simple или composite. Вариант указывает существующий товар, отличный от владельца; один productId не повторяется внутри группы. ID групп уникальны внутри владельца, ID вариантов — внутри всего товара. Пустая группа, qty≤0, нецелые min/max, невалидные деньги или ссылка отклоняются.

Quantity задаётся в закреплённой базовой единице выбранного товара, до12 дробных знаков; composite option нормируется через его yield. priceDelta — signed decimal BYN до2 знаков, без Double. Доплата относится к одной основной позиции; quantity варианта определяет расход, не множит priceDelta. Изменение group/option IDs при редактировании/перезапуске не происходит.

ModifierRules.selectionPrice/configureModifiers — чистый контракт для02: выбранный optionId допускается один раз; группы проверяют min/max; неизвестный optionId и отрицательный итог отклоняются. Например, база2 + delta−1.50 →0.50 допустимо; база1 + delta−1.50 отклоняется. Сохранение отрицательной delta не означает разрешение любого выбора. Для цены0 ручная фактическая цена будет задана02 и проверена тем же контрактом. Финансовые скидки/округление03 здесь не вводятся.

configureModifiers создаёт запросы основного товара и выбранных вариантов. RecipeCalculator агрегирует их вместе и применяет noStockTracking/allowNegativeStock каждого simple ingredient. Группы выбранного варианта не выбираются автоматически: оператор выбирает варианты основной позиции, как в Web. Здесь нет записи продажи/списания/резервов.

## Фото — P08/RES-02

Системный GetContent(image/*) через Activity Result API. Runtime storage permission не запрашивается: поток читается по grant выбранного URI и сразу копируется в private storage. URI в БД/черновик не сохраняется. Отмена picker оставляет карточку прежней. Callback привязан к sessionId редактора, переживающему recreate; устаревший результат не открывает stream и не меняет другую карточку. После process recreation callback ставится в очередь за восстановлением черновика.

Границы до обработки: исходник≤16MiB,≤40MP, JPEG/PNG/WebP/HEIF(HEIC при наличии платформенного decoder). Android ImageDecoder уменьшает до1200px по длинной стороне; альфа накладывается на белый фон. Выход — JPEG≤600000bytes: качества82/70/55, при необходимости до8 попыток уменьшения геометрии×0.75, по образцу Web. Файл не апскейлится. Ошибка разрешения/декодирования/лимита/записи не заменяет прежнее фото. Анимация не сохраняется; используется статичный bitmap.

Протокол:

1. Ограниченный stream → product-photo-staging/import-*.tmp, sync/close, проверка формата/пикселей и decode.
2. JPEG → pending file, sync/close → immutable product-photos/<UUID>.jpg, rename и fsync каталога.
3. Черновик с photoId сохраняется в Room; активный Product.photoId меняется только вместе с сохранением карточки в транзакции. Удаление фото до Save меняет лишь черновик; Discard возвращает прежнее активное фото.
4. Сбой БД сохраняет старую Product.photoId и старый файл; подготовленный orphan допустим. Старые файлы не удаляются перед commit.
5. При открытии каталога cleanup читает ссылки товаров и durable draft под Room transaction и удаляет только orphan своего namespace старше24ч. Свежие preparations защищены grace period, read/prepare/cleanup сериализованы store lock. Ошибка cleanup не отменяет успешное открытие каталога. Мгновенного удаления и отдельного worker пока нет.

Photo ID проходит namespace validation; произвольный путь из URI/backup не используется как путь хранения. Image thumbnails читаются вне main thread с downsampling; Coil пока не подключён, граница текущего adapter и целевое расширение02 — [ADR-006](DECISIONS.md). Нет загрузки фото на сайт, зависимостей от внешнего URI после сохранения и доступа к sandbox Web. HEIC/EXIF/разные реальные document providers требуют физической приёмки.

Платформа: [ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder), [Activity Result API и восстановление callback](https://developer.android.com/training/basics/intents/result).

## Room и совместимость — P09/P10

Schema3/MIGRATION_2_3: nullable products.photoId + modifier_groups/options с FK. У владельца cascade группы/варианты; выбранный productId — RESTRICT. Старые товары, рецептуры, баланс, конфигурация, результаты команд и черновики сохраняются. При восстановлении legacy draft с отсутствующим sessionId новый ID записывается до включения редактора/picker. Полный upgrade1→2→3 зарегистрирован; destructive fallback отсутствует.

Save одной транзакцией сохраняет товар/configuration/recipe/modifiers/photo reference/ledger/result/audit и убирает его черновик. Проверки min/max/существования выполняются внутри команды, не только UI. Повтор команды не создаёт второй набор вариантов. Legacy fingerprints01a/01b сохраняются при отсутствии новых полей.

Удаление товара, используемого recipe или modifier option, отклоняется с объяснением. Ссылка из несохранённой чужой карточки также блокирует удаление. Удаление несвязанного товара не очищает чужой черновик. Архивирование/история продаж не вводятся.

## Проверки и остающаяся приёмка

ModifierRulesTest: raw limits/duplicates/references/qty/decimal, cardinality/final price, composite option с per-ingredient stock, fingerprints. ProductExtrasTest: Room replay/guards; malformed/oversize/permission streams; faults copied/decoded/beforePublish/published; реальный DB trigger failure после подготовки фото и reopen; cleanup с product/draft references; actual schema2→3 migration. Существующая schema1 fixture теперь проверяет upgrade1→2→3. CatalogPhotoSessionTest проверяет durable session старого черновика и очередь callback во время восстановления нового ViewModel.

CatalogExtrasUiTest: редактор группы/варианта, отмена picker через instrumentation monitor, импорт синтетического PNG через тот же ViewModel callback/stream path, stale-session guard, recreate, сохранение IDs/photo и отмена удаления. Тест не подменяет проверку выбора файла в реальном provider. Внешний updater прогоняет этот UI scenario перед seed LaunchTest и снимает extras-editor.png для просмотра; затем проверяет обновление пакета и прежний stock8.

CI-verified: [run40098187895](https://github.com/mendelev-main/M-POS-Android/actions/runs/40098187895), commit eb40b22.23 JVM tests,4 Python tests, lint/assembleDebug; по16 instrumentation/API28/35, затем2 explicit UI setup tests и update-e2e с CATALOG UPDATE PASSED/UPDATE E2E PASSED на каждом API. Schema3 совпадает с KSP export. Signing/publish skipped (develop). Полная история исправлений — harness/build-log.md. UI suites начинают с отдельного чистого редактора (сохранённые товары не удаляются), чтобы отказ одного теста не оставлял его незавершённую форму следующему тесту. Реализованные сценарии P06/P08/P09/P10 ci-verified; это не device-verified и не завершение всего этапа01. Физически остаются реальный picker/provider, HEIC/orientation/низкое место, визуальная приёмка и font scale. В этапе01 также ещё нужен ручной порядок категорий/финальная приёмка; к02 автоматически не переходим.
