# Всегда в наличии для онлайн-заказа — 10.11.2026

Статус: implemented локально по прямому запросу владельца; запуск на сайте/планшете pending. main без commit/push. Применены mpos-phase/mpos-domain.

## Карточка товара

Товары → карточка → Онлайн-меню: добавлен общий ProductEditorSwitch «Всегда в наличии» (productAlwaysAvailableOnline). По умолчанию выключен. После сохранения при включённом флаге сайт получает неограниченный доступный остаток товара. Работает для простых и составных товаров, включая нулевой/отрицательный физический остаток. Выключение возвращает обычный расчёт физического остатка/порций рецепта.

Онлайн заказ/Онлайн меню и каналы категории по-прежнему управляют публикацией и видимостью. Ранее согласованное исключение товаров с обязательными модификаторами сохраняется. Флаг не включает скрытый товар и не добавляет его на сайт без обычной ручной публикации каталога.

Не меняет noStockTracking/allowNegativeStock, физические балансы, рецептуру, резервы и локальные правила продажи. Это только настройка доступности на сайте.

## Совместимость протокола

Backend не изменялся и не развёртывался. Проверены исходники M-Pos-Backend server.js blob023cec4132244e6b46e8d66ed7d90982be05635f и availability-service.js bloba80c382963ff49537851ac2e68b9f3fceca20101. Service.store принимает quantity:null, list сохраняет null, validateOrder сравнивает количество только при available!==null. Это уже существующий контракт неограниченного остатка, который Web availability.js использует для Infinity.

Native OnlineAvailability.item отправляет quantity как настоящий JSON null, не строку/null omission и не придуманное большое число. При выключенном флаге остаётся точный BigDecimal остаток с нижней границей0. No-stock товар сохраняет прежний quantity:null независимо от нового переключателя. Для enabled-флага вычисление физического количества не требуется.

BackendPublication собирает выбранные ID одним чтением catalog-product sidecars, сохраняет external/source IDs и отправляет в прежнем POST /api/availability/snapshot. Revision/sampledAt/settledWebOrderIds и подтверждение applied не менялись. BackendRuntime уже следит за product revisions, поэтому Save флага запускает следующий background availability update при включённом приёме онлайн-заказов. Обычная ручная синхронизация также отправляет snapshot. Сам переключатель до Save не вызывает сеть.

## Данные, черновик и backup

ProductDetails.alwaysAvailableOnline nullable: отсутствие в старой записи означает false; явное false позволяет сохранять отключение. ProductDetailsCodec v4 используется при заданном поле, старые записи v1/v2/v3 читаются без миграции. Строго boolean; поле в старой версии codec отклоняется.

ProductInput fingerprint v4 включает flag; прежние fingerprints без flag остаются прежними. Команда с тем же ID и иным значением не считается повтором. CatalogRepository сохраняет setting в той же product transaction. Черновик использует общие fields/value, поэтому значение переживает восстановление редактора. Native backup содержит sidecar, NativeRestoreValidator читает общий codec v4. Исходный Web-архив не редактируется; в старом Web этого переключателя нет, перенос получает default false. Room schema и общий формат backup не менялись.

## Проверки

OnlineAvailabilityTest: unlimited JSON null и отсутствие расчёта stock; точный дробный/отрицательный/default unlimited; explicit true/false/null и legacy codec, draft/backup roundtrip, отказ строкового bool и неверной версии; fingerprints и независимость local stock flags. CatalogPresentationTest — прежняя metadata/fingerprint regression.

ProductDetailsStoreTest расширен проверкой durable flag/запрета смены flag при replay и сохранения при legacy update. BackendPublicationTest (fake HTTP + isolated Room) проверяет null для включённого товара с0 stock, возврат0 после выключения, неизменный BalanceEntity. Android tests compiled-only; production backend/реальные товары/сайт не вызывались. Конкретный результат локальных команд — harness/build-log.md.

Локальный результат:6 JVM/0fail, compileDebugKotlin + compileDebugAndroidTestKotlin successful (1m23s). Preflight и diff-check passed. Реальный сайт/планшет не проверялся.
