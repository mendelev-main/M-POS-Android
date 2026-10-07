# 103 — нативный основной экран оплаты

Scope: native main payment/receipt preview, split payment workspace, card confirmation, offline loyalty decision and completion receipt presentation. Existing native cash/split/count/amount input dialogs and 074/075/077 native business decisions remain. Financial formulas, stock/shift/delivery/rewards guards, terminal-confirmation semantics, Room commits, v13 and printing/availability triggers are unchanged.

Use shared native forms/palettes/Manrope, structured receipt metrics and accessible mounted role-button actions. No executable snippets. Await known payment operations and lock presentation across modal/page replacement; paid split parts cannot be edited or exited by bypassing reviewed handlers. Native cancel waits for the reviewed result so an exit refusal is visible. Rollback restores reviewed presentation, not native storage authority. DOM/auth/runtime removal remains 109. No local APK; physical acceptance 110 pending.

Implementation complete; final automated verification/publication recorded below.

## Подтверждённое бизнес-поведение

Пользователь явно подтвердил сохранение действующей отмены карты: обычная оплата картой → «Отмена» вызывает renderSplitPayment, включая пустую раздельную оплату, когда частей ещё нет. Повторное «Назад» возвращает основную оплату. Для оплаты отдельной части отмена также возвращается к раздельной оплате. Не меняем это правило в 103.

## Реализация

Общий нативный renderer из 100/102 распознаёт payment-page-root и контекстные
модальные формы оплаты. Kotlin показывает чек/скидки/доставку/лояльность/итог,
внесённую сумму/номиналы/сдачу, выбор наличных/карты, части оплаты, подтверждение
терминала, offline-решение по подарку и оплаченный чек. Суммы — форматированные
узлы существующей презентации; Kotlin UI не повторяет финансовую арифметику.
Role-button ввод суммы имеет реальную mounted action identity и учитывает
aria-disabled; paid части не становятся кликабельными нативными контролами.
Счётчик частей имеет отдельную подпись и понятные accessibility action names.
Нативные cash/count/amount формы предыдущих этапов продолжают работать поверх
этого workspace, включая обновление underlying tender/change.

Известные async операции (stock/reward/preflight/split progress/final settlement)
учитываются до acknowledgement. Presentation счётчик не меняет state, суммы,
финансовые snapshots или native authority. Pending/unknown-result блокировка
переносится через замену модальной формы или payment workspace; callbacks с
устаревшими token/node identities не действуют. Отдельный observer отслеживает
динамические узлы payment page и освобождается при hide/replacement. Error
feedback принадлежит текущему cart context и не переносится в другой заказ.

Native payment back отправляет original mounted back/cancel и ждёт результата.
Аппаратный Android Back перехватывается внутри dialog: отказ закрыть paid split
не закрывает окно сам по себе. Busy back заблокирован; после отказа сотрудник
видит исходную причину и продолжает оплату. Unknown-storage state показывает
указание перезапуска. Успешное закрытие/завершение идёт только через исходный
flow; при paid split оригинальный запрет выхода сохраняется.

Cash/card receipts, stock movements, split draft progress и доставка проходят
существующие native commands. Только после успешного commit выполняются source
availability/loyalty/print triggers. UI не добавляет auto sync, отправку остатков,
повторную печать, повтор финансовой транзакции или terminal polling. Кнопка
«Оплата прошла» остаётся ручным подтверждением кассира после терминала.

## Совместимость

MPosNativePaymentUiEnabled=false возвращает reviewed payment presentation.
Для полного presentation fallback используются также существующие flags
cash/split/count/amount input. Эти flags не возвращают legacy storage authority,
не меняют v13 или settled receipts. Source DOM/formatting/receipt/runtime остаётся
до 109; screen rendering не означает окончательного ухода от WebView. История
чеков/детали/возврат относятся к 104; presentation оплаченного чека здесь
сохраняет source buttons/данные, не добавляет операции возврата.

## Проверки

Actual reviewed payment renderers, inline mounted handlers and finalizePayment
execute with synthetic state/controlled local commit. Covered: formatted tender,
change/denominations, main amount role-button, cash commit ordering/duplicate
rejection, terminal confirmation/cancel, paid split disabling/back refusal,
failed/unknown commit preserving cart/products/receipts and no post-commit sends,
stale token/replacement/rollback. Existing 074/075/077 and settlement/availability/
print command tests remain. Native views verify deferred hardware back, refusal
feedback, reopened recovery block and palette/metrics/actions. Synthetic light/
dark previews are inspected separately from physical acceptance. No product APK.

Paid completion has a single displayed Done action. Native Back invokes that mounted action, clearing the reviewed payment page/flow rather than revealing its stale pre-payment contents. Offline reward Back invokes the mounted «Вернуться» action so __offlinePaymentAction is cleared without consuming the reward or creating a sale. These paths are covered by actual-renderer tests; terminal cancellation retains the explicitly confirmed source behavior.

For deferred payment exit, default Dialog cancellation is disabled. Hardware KeyEvent Back and API 33+ OnBackInvokedDispatcher route through the same reviewed cancellation decision; predictive/gesture Back must not dismiss paid split without that decision. API 33+ gesture acceptance is pending on the physical tablet.

## Итоговая проверка

482 JS / 397 full JVM tests passed, 0 failures/errors/skipped. После доработки API 33+ Back: final native UI 15/15 и lint passed; lint 0 errors / 15 existing warnings. Native previews inspected (synthetic only). Full-suite report precedes the final targeted UI run; the targeted run is not reported as another full suite. Product APK не собирался, физическая/gesture приёмка pending.

Engineering progress after main publication: 102/110 (92.73%), 8 remaining. This is an engineering task ratio, not native feature coverage. Next 104 — receipt history/details/refunds.
