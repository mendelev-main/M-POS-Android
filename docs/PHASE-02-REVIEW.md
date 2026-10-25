# Сверка каталога и рабочей зоны перед03a

2026-10-25. Базовый runtime f1639b8, документация9c050c4, merge87f57ae. Это аудит доказательств, не новая device приёмка. [Финальный develop CI](https://github.com/mendelev-main/M-POS-Android/actions/runs/40184227465):47JVM/47instrumentation,2scale+4update seed,lint/build/6Python; actual APK1→2 сохраняет данные. Полный main release имеет собственный gate и фиксируется отдельно в build-log.

| IDs | Что подтверждено автоматикой в текущем коде | Остаток / граница |
|---|---|---|
| P01/P02/P03 | simple CRUD/валидация/draft/error; поиск/категории; appearance/order; Room rollback/replay/reopen | полный walkthrough экрана на планшете, visual parity всех состояний |
| P04/P05/P07 | units/base/display, yield/вложенность, flags/precision/cycles/dependency guards; CatalogConfigurationTest/RecipeCalculatorTest | supplier receiving/массовая инвентаризация относятся к04 |
| P06 | min/max/modifier dependencies/доплата/strict Add, ModifierRulesTest/CatalogExtrasUiTest/WorkspaceUiTest | все варианты клавиатуры и сложных длинных названий на физическом устройстве |
| P08 | private file staging/commit/fault/restart, bounded Coil memory cache; CatalogPhotoSessionTest/PhotoCacheTest | реальный provider/HEIC/memory pressure/OEM; GetContent остаётся picker, PickVisualMedia не подключён |
| P09/P10 | FK/usage/delete guards текущих рецептов/modifiers/current/parked/home; ledger/revision/conflict/fault/migrations1→5 | защита будущих receipt snapshots вводится в03; полного архива товаров сейчас нет |
| W01 | категории/папки/search/persistent fixed home slots/drag и доступное перемещение; HomeLayoutRepositoryTest/HomeLayoutUiTest | paired Web walkthrough/реальный tablet layout |
| W02 | strict modifiers/manual price/merge/quantity/remove; folder/live preview; CartEngine и WorkspaceUiTest | проверка длинных данных, клавиатуры и выбора после process kill на планшете |
| W03 | cart+parked aggregate reserves, exact0.0004kg/flags/shortage; OrderRulesTest/WorkspaceAvailabilityTest | подпись плитки advisory для базового товара; после выбора modifiers Add проверяет полную конфигурацию. Это не расчёт любой будущей комбинации на плитке |
| W04/W05 | durable park/resume/swap/delete/clear/details; metadata ownership/CAS/fault/replay/ABA и один резерв; ParkedOrdersRepositoryTest/UI | real-device walkthrough; sale/refund ещё отсутствуют |
| W06 | Room reopen/recreate/concurrent commands/stale revalidation/actual APK replacement; RevalidationRepositoryTest/UI | полная physical process-death matrix открытых диалогов и OEM background kill остаётся pending |
| W07/DS04 |2000товаров, bounded lazy расчёт, portrait/landscape150%, доступные основные действия; WorkspaceScaleUiTest | real tablet/200% font/screen reader/все IME/error combinations и длительная нагрузка |

Нельзя объявлять весь P/W диапазон device-verified. Пользователь отложил тесты на планшете; это не блокирует подготовку03a. Исторические строки planned в ранних заметках01a–02e описывают тогдашний срез, текущие evidence/ограничения задаёт PARITY и эта сверка.

Перед первой оплатой дополнительно обязательны: утверждённая денежная policy/allocations, актуальная смена, исторический snapshot расхода, транзакция sale/receipt/stock/draft/result, installation guard на commit/recovery, fault/replay/concurrency и миграция. Выпуск каталога/черновиков не означает готовность реальных оплат.

Следующая часть — [03a: контракт минимальной смены](SHIFT-03A.md). Эквайринг, фискализация, сотрудники/роли и отчётность05 не входят.
