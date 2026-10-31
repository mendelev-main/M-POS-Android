# План документации и условие перехода к коду

Срез 2026-10-24. Цель — конечный набор решений для очередного инкремента, а не бесконечное описание всей системы до первой строки Kotlin. Наличие документа не равно утверждённому контракту/прошедшему тесту.

## Уже есть общий слой

Описаны цель/стек/архитектура, P/W/S/I/H/C/B/D/N, логическая модель/состояния/восстановление/доступ, acceptance и harness. [MODULES.md](MODULES.md) содержит 20 активных областей (M17 исключён) с владельцем/предпосылками. В этой итерации добавлены паспорта недостающих областей и расширения склада, управления/отчётов, клиентов.

## Восемь пакетов углубления

Пакет — связанный набор решений/fixtures, не обязательный отдельный Markdown файл. Срок готовности — перед своим инкрементом, а не перед всей разработкой.

| ID / срок | Уже описано | Что ещё уточнить до его кода | Источник результата |
|---|---|---|---|
| D01 Перед 01a | первый scope, BYN, design system из Web, stock editor без причины, validation defaults, первая schema candidate, UI/failure cases | в PR проверить совместимые Room/KSP versions, оформить реальную schema/fixtures; небольшие лимиты candidate принимаются как native defaults | [FOUNDATION-CONTRACTS.md](FOUNDATION-CONTRACTS.md) |
| D02 Перед 03a–03c | state machines/atomic sale/refund/минимальная смена | точные money/discount/split/refund allocation examples, cash/shift reconciliation, поддержка partial refund | [OPERATIONS-SPEC.md](OPERATIONS-SPEC.md), [MANAGEMENT-REPORTS-SPEC.md](MANAGEMENT-REPORTS-SPEC.md) |
| D03 Перед 04 | suppliers, procurement/receiving, baseline/cancel protocols | weighted cost/закрытие с shortage/explicit over-receiving подтверждены2026-10-27; дополнительные расходы/сторно и receiving fixtures уточняются | [WAREHOUSE-SPEC.md](WAREHOUSE-SPEC.md) |
| D04 Перед 05 | staff/admin границы, capability proposal, report snapshots/formulas | окончательная role/auth matrix, период/timezone/возвратный cut-off, exports data schema | [MANAGEMENT-REPORTS-SPEC.md](MANAGEMENT-REPORTS-SPEC.md), [ACCESS-AND-AUDIT.md](ACCESS-AND-AUDIT.md) |
| D05 Перед 06 | customer lookup/gift/consent boundary | loyalty payload/idempotency/offline/staging, consent text | [CUSTOMERS-HALL-SPEC.md](CUSTOMERS-HALL-SPEC.md) |
| D06 Перед 07 | parse/preview/staging/activation/recovery | backup schemas/fixtures/Web mappings, limits, restore checkpoints и судьба external effects, replace/merge решение | [RESILIENCE.md](RESILIENCE.md), [OPERATIONS-SPEC.md](OPERATIONS-SPEC.md) |
| D07 Перед 08 | transport boundaries/print ambiguity/report snapshot/Telegram fake | модель принтера/бумага/кириллица, sample PDF/XLSX, delivery/retry cases и тестовый Telegram | [OPERATIONS-SPEC.md](OPERATIONS-SPEC.md) |
| D08 Перед 09 | binding/SSE/menu/availability/tariffs boundaries | реальные schemas/error fixtures, server auth/replay/idempotency/ownership и staging | [SETTINGS-SITE-SPEC.md](SETTINGS-SITE-SPEC.md) |

До 01b отдельно внутри D01 добавляется fixture precision recipeYield1/3 и graph/image limits до 01c. Это не условие для simple/category01a. Remote loyalty часть D05 может использовать часть D08 transport раньше 09, не делает целиком готовым сайт.

## Сколько осталось перед первым кодом

**Для 01a отдельный новый большой документ больше не нужен.** D01 здесь описан как стартовый контракт; BYN выбран пользователем. В начале кодовой задачи остаются три коротких технических результата: закрепить проверенные версии Room/KSP, превратить schema candidate в реальную exported schema и превратить CAT/UNIT/command examples в исполняемые tests. Это часть реализации и review первой PR, а не ещё один круг общей документации.

Начать можно с 01a: сохранять категории и простые товары. Не нужно ждать D02–D08. После появления рабочего 01a детализировать следующий 01b, затем 01c/02. До финальных интеграций остаются **7 последующих пакетов D02–D08** плюс уточнения рецептур/медиа D01; каждый закрывается перед своим кодом. Ранний общий паспорт не заменяет фактическую модель оборудования или ответ backend.

## Правило достаточности

У каждого выбранного инкремента есть вход/выход, owned data, command/revision/transaction, failure/retry/restart, UI states, fixtures и источники. Если неизвестность меняет результат именно этого инкремента, её закрывают до кода. Если относится к позднему модулю, записывают gate и не заставляют первый каталог ждать сайт/принтер/лояльность.

«Описано» означает документ существует; «готово к реализации» означает нет блокирующей неизвестности для выбранного scope; implemented/verified — отдельные статусы parity. Новые паспорта остаются planned в смысле продукта. Схема/версии и действительные тесты проверяются по коду, а не по числу страниц.

D02 уточнение25.10.2026: [MONEY-03](MONEY-03.md) — подтверждены line HALF_UP2/пример0.135→0.14 и полный cross-shift cash refund. [SHIFT-03A](SHIFT-03A.md) — минимальный open/close/carry из Web, candidate money/split/discount gates остаются. [Сверка01/02](PHASE-02-REVIEW.md) сохраняет физические acceptance pending.

2026-10-31: D05/этап06 и D08/этап09 отложены владельцем в [TODO](PRODUCT-TODO.md), не блокируют07/08. D06 проектируется для реально реализованных моделей01–05; unsupported Web fields требуют явного preview/политики, не фиктивного модуля клиентов. Правила текущих отчётов закреплены в [REPORTS-05C](REPORTS-05C.md).
