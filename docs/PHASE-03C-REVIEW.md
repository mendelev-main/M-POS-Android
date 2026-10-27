# Сверка03c перед выпуском

Рабочий блок — полный возврат native receipt. Stable перед блоком0.1.61.1/code7101, Android16+/API36, BYN, applicationId/certificate неизменны. Частичный возврат, loyalty/acquiring, печать/Telegram и этап04 не входят в этот блок.

## Выполненный объём

| Граница | Реализация / evidence |
|---|---|
| Исторический склад | HistoricalRestock, exact0.0004kg/negative/untracked/changed recipe; checks40377899857 |
| Деньги | RefundMoney, original cash/card amounts без повторной сдачи, delivery cash credit; checks40379182536 |
| Атомарность и миграции | Room8, unique receipt/command, FK RESTRICT, receipt unchanged, refund+ledger+balance revisions+register+result+audit; full40380296921,81instrumentation |
| Повтор/потерянный ответ | command replay до проверки текущего состояния, два commands один receipt —один победитель, сбой каждой записи/SQLite abort, no partial split, close/sale/installer guards |
| История и текущая касса | Original sale/closed shift не переписываются. Refund effect=delivery credit−customer cash в refund shift один раз. Mixed100: cash40/card60 вручную; доставка25/fee5 возвращает полный25 и отменяет cash expense5 |
| Замена APK | RefundUpgradeTest: exact stock/ledger/hash/receipt/refund/closed cash/replay; full40382070117/API36,82instrumentation +3financialseed/verify после actual debugAPK1→2 |
| UI | История/статус, shared Close/Dialog/Danger, confirmation наличные/ручная карта/курьер, известный результат при ошибке перечитывания. Full40386335852:77JVM/86instrumentation и actual updater passed. Visual повтор40387854147/d7ffd83 прошёл: чистые refund-confirm/result/returned-receipt1600×1125 просмотрены, без overlay |

Оригинальное Web поведение доставки намеренно изменено по решению владельца: courier expense тоже отменяется. Исправлен обнаруженный Web cross-shift refund compensation bug. Native не пересчитывает текущий рецепт при возврате, не удаляет исходный receipt и не создаёт duplicate generic cash withdrawal.

## Перед merge/release

- Full CI40387854147/d7ffd83 SUCCESS:77JVM/86instrumentation/8Python/lint/build,2scale/8UIseed/3financialseed+verify/debugAPK1→2. Чистые refund screenshots просмотрены.40386335852 сохраняется как business evidence с непригодными visual artifacts (Quickstep overlay). Новый active-window guard обязателен; generic/M-POS ANR не подавляются.
- Сверить PLANS/PARITY/build-log/current context, generated Room8 schema из CI уже сохранена.
- Готовый блок через PR develop→main, PR checks→merge→main checks/emulator/signing/publish→public signed upgrade. Не публиковать каждый инкремент. Версию назначает существующий release pipeline; новые signing key/package не нужны.

## Пределы проверки

Physical tablet/OEM, paired Web screenshots, font scale/dark/portrait для нового refund UI, принудительный cold kill на каждой границе, пользовательская работа с внешним терминалом/возвратом курьерских наличных —отдельная приёмка. Физическое устройство отложено владельцем; CI не обозначать device-verified.

SDK/ARM64 AVD для Mac установлен и local build77JVM/lint/APKs прошёл. GUI first launch пока требует подтверждения владельцем: ограниченный процесс агента не получает CPU sysctl и не загрузил Android. Подробности/launchers: [LOCAL-ANDROID.md](LOCAL-ANDROID.md). Локальная сборка и настроенный AVD не равны работающему окну Android.

После release checkpoint следующий этап04 —склад, приёмка/поставщики/закупки/инвентаризация, с отдельными acceptance и transaction tests; он не начинается автоматически этим review.

## Актуальная сверка перед PR5

В блок также входят общий navbar и рабочая зона по pinned Web. FullCI40461096340/3e11c33 SUCCESS80JVM/90instrumentation/12Python/lint/build/actual debugAPK1→2; screenshots обычного планшета и150% просмотрены. Удалена устаревшая подсказка экрана смены о будущей доступности оплат: оплаты и возвраты уже подключены. Это текстовое исправление; финансовые команды/Room8/signing прежние. PR5 develop→main; выпуск и public-channel upgrade должны быть подтверждены отдельно main pipeline. Физическая/тёмная/paired pixel приёмка по-прежнему отложена.
