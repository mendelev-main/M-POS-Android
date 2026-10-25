# План этапов

Порядок задан пользователем: товары → рабочая зона → остальные возможности → настройки сайта/конфигурация. Работа начинается по запросу пользователя на соответствующий этап; закрытие этапа не означает автоматический старт следующего.

| Этап | Содержание | Зависимость | Статус |
|---|---|---|---|
| 00 | Harness, документация, Kotlin bootstrap, Actions, identity/signing | — | ci-verified: checks/emulator/signed APK; device pending |
| 00.1 | Проверка/скачивание APK, системная установка, GitHub Release канал | 00 | ci-verified API 28/35, production 1801→1901; device pending |
| 01 | Товары, категории, фото, единицы, рецептуры, модификаторы | 00 | 01a ci-verified API28/35, signed0.1.16.1; 01b ci-verified API28/35 (run40094842882); 01c ci-verified API28/35 (история); актуальный core/P03 order+color/symbol/schema4 ci-verified Android16/API36, run40101394875; device pending |
| 02 | Рабочая зона, раскладка, папки, корзина, текущие/отложенные чеки | 01 | 02a ci-verified API36/run40106275939; 02b ci-verified API36/run40140796798, 02c ci-verified API36/run40172190371; 02d UI ci-verified API36/run40172821096; 02e slots/drag ci-verified API36/run40177332732; 02f perf/adaptive ci-verified API36/run40184227465; device позже по решению владельца |
| 03 | Минимальная смена03a → оплата, скидки, возврат, история и сохранность | 02 | 03a ci-verified API36/run40206673038; D02 дополнен,03b.1 money ci-verified/run40223239353; 03b.2a checkout inputs ci-verified/run40224718420; 03b.2b atomic sale/history repository ci-verified/run40226577890; checkout/history UI и refund planned; docs/SALE-03B.md |
| 04 | Склад, приёмка, поставщики, закупки, инвентаризация | 03 | planned |
| 05 | Полное управление сменой/кассой, сотрудники/роли, отчёты/аналитика | 03, 04 | planned |
| 06 | Клиенты, согласия, лояльность, зал/бронирования | 05 | planned |
| 07 | Backup/import, фото, проверяемые миграции и восстановление | 01–06 | planned |
| 08 | Принтер/ящик, PDF/XLSX, Telegram, настройки устройства | 05, 07 | planned |
| 09 | Backend/сайт, HTTPS/SSE, online orders/menu/availability, конфигурация | 03–08 | planned |

План21 логического модуля: [MODULES.md](docs/MODULES.md). Порядок/критический путь: [DELIVERY-ROADMAP.md](docs/DELIVERY-ROADMAP.md). Остаток документации: [DOCUMENTATION-PLAN.md](docs/DOCUMENTATION-PLAN.md). Старт —01a; currency каталога BYN подтверждена пользователем.

Документация сценариев: [спецификация](docs/FUNCTIONAL-SPEC.md). Инкременты 01a–01c/02a–02f и контракты описаны отдельно; статус этапа остаётся planned до реализации.

Каждый этап: `harness/build/NN-*.md` содержит границы и критерии; при начале создаётся его context; после проверок обновляются parity и журнал. Внутри этапов допускаются небольшие PR/итерации. Этап 07 — пользовательский перенос Web backup, а не отсрочка обычных Room migrations: migration tests нужны с первого изменения схемы в 01.
