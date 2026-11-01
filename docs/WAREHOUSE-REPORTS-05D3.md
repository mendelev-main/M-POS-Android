# 05d.3 — исторические остатки и оценка склада

Статус: in progress, validation pending. Следующий блок M15 после05d.2;07/08 не начинаются этим изменением.06/09 TODO, M17 excluded.

Источник: pinned1682c88334e6fb13586ce4b530157c59ca062e43, warehouse-reporting.js warehouseReport/warehouseNotes; Android adapters reporting не переопределяют. source-product-stock.test.cjs1294+: inclusive local range, return by return date, no writes, legacy unknown history. Web берёт текущий balance и вычитает движения после границы; manual/inventory корректировки не полностью покрыты. Native использует полный ledger, включая opening/product_card/inventory, проверяет полноту и единицы, не пересчитывает старые рецептуры.

## Критерии

- WH01: точные start/end по полуоткрытому календарному периоду. Current − сумма delta с boundary включительно; current snapshot и все движения читаются в одной admin transaction. Сумма ledger должна сходиться с current. При расхождении start/end недоступны, current не исправляется. Товары сегодняшнего каталога, названия явно текущие.
- WH02: складские приход/списание/возврат и signed прочие корректировки раздельно. Количества не округляются до0.001 и не складываются между товарами/единицами. Возвраты по дате возврата. NoStockTracking: current/start/end недоступны, но закупленные количества и фактические суммы накладных остаются видимы отдельно от складского прихода.
- WH03: единица движения сохраняется при записи (schema9); migration8→9 добавляет nullable unit без backfill из products. Legacy sale/refund/receiving/inventory unit восстанавливается только из immutable source после проверки product/delta/command/time и receipt hash. Opening/product_card без unit остаются неизвестными. Неизвестная или конфликтующая единица после boundary делает эту границу недоступной, не нулевой. Будущие изменения создают unit snapshot атомарно с обычным movement. Никаких перезаписей/replay старых команд.
- WH04: владелец подтвердил2026-11-01 поведение Web: исторический остаток10шт. при текущей стоимости3BYN оценивается в30BYN, даже если раньше cost был2. Явная подпись «Оценка по текущей себестоимости», это не историческая себестоимость/прибыль05d.1. Владелец подтвердил HALF_UP каждой строки до копейки, затем сумму строк:1,5×2,35→3,53; две строки→7,06. Строки без учёта исключаются из общей стоимости остатков с явным счётчиком. Если хотя бы одна учитываемая строка недоступна, общий итог — недоступно, не частичная сумма. Суммы накладных — сохранённые фактические totalMinor.
- WH05: shared analytics UI, период/обновить/error/retry/empty, единый report snapshot и существующие admin epoch fences. Unknown показывается прочерком с причиной, неполная денежная сумма не выдаётся за полный итог. Logout скрывает данные, SavedState без денег.
- WH06: exact math/DST/boundaries/manual adjustments/unknown-unit/incomplete ledger/noTracking tests, real Room read-only/reopen/migration8→9/old replay и actual APK upgrade. Physical tablet/dark/performance отдельно.

Базовая единица native товара неизменна после создания (CatalogRepository.saveProduct), но это не используется как скрытый backfill старых movement units. Nullable migration сохраняет происхождение исторических фактов. Новый07 backup должен включать schema9 movement.unit, старые JSON/fingerprints не меняются.


2026-11-01: оба правила WH04 подтверждены. Full05d.3 valuation подключена/validation pending. Промежуточный CI41303717173/0e31565 отменён в начале после ответа владельца, не evidence и не failure; готовится один полный прогон обновлённого блока.
