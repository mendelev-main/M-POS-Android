# 05a — внесение и изъятие наличных

Статус ci-verified, CM01–CM05; [CI40853537689](https://github.com/mendelev-main/M-POS-Android/actions/runs/40853537689)/7293d9c. Зависимость: checkpoint04 в PHASE-04-REVIEW.md, действующие Shift/Sale/Refund contracts. Пока single-owner; сотрудники/auth/roles/полные отчёты не объявлены реализованными.

## Источник и правила

Pinned Web1682c883 `Web/js/features/shifts.js` openCashMovementModal/submitCashMovement/shiftTotals/cashDrawerBalance. Сумма положительная, комментарий необязателен, изъятие не превышает наличные. Native: точный ввод BYN до копейки (запятая допустима), без Double/молчаливого rounding; комментарий trim, до300 символов без управляющих знаков. Источник Android не переопределяет submitCashMovement. Source regression cases520/526/561/2306 проверяют запись до эффекта, отказ хранения и exact25,50.

Пример: касса100, внесли20 →120, изъяли5 →115 BYN. Наличная продажа5 добавляет5, возврат5 вычитает5 один раз. Сдача и доставка сохраняют уже утверждённый net cash контракт. Ручные движения не заменяют openingCash, не правят receipts/refunds и не дублируют их кассовые эффекты. Closed expected/count/difference сохраняются исторически; новая смена переносит counted предыдущей, а не сумму старого журнала заново.

## Команда и хранение

ShiftCashCommand связывает UUID команды, shiftId, expected register revision, owner actor, deposit/withdrawal, amountMinor и нормализованный note. Guard внутри Room transaction: owner, актуальная открытая смена и revision, отсутствие незавершённой раздельной оплаты, timestamp>=openedAt, достаточность cash и отсутствие переполнения. До завершения разделённого чека ручная операция не допускается: текущий native cash snapshot не представляет незавершённые поступления как финальный чек. Native guard сохранён отдельно от Web UI.

Одной InstallationGate+Room transaction сохраняются4шага: workspace document `shift_cash` → register revision+1 → result → audit. Payload1 содержит неизменные id/command/shift/actor/type/amount/note/time/before/after; отсутствие документа означает пустой legacy journal. Повреждение/неизвестная версия не сбрасываются. SQLschema8 прежняя, новая таблица/миграция не требуется; будущий backup обязан включить документ. Duplicate IDs/commands, отрицательные суммы и несогласованные before/after отказываются.

ShiftRepository — один владелец currentCash: opening + cash sales − delivery cash-out − cash refunds + courier refund credit + manual deposits − manual withdrawals. Суммирование BigInteger до проверки итогового Long, без ложного промежуточного overflow. Закрытая смена читает сохранённый expectedCash; её ручные записи не попадают в новую смену. Close preview/sale/refund/другая кассовая операция после открытия формы меняют register revision и требуют нового подтверждения. Replay исходной команды возвращает прежний movementId, в том числе после закрытия/обновления APK, без повторного денежного эффекта.

## UI и восстановление

В «Смена»: Внести/Изъять → общая форма сумма/необязательный комментарий → preview по captured available cash → save. Ошибка сохраняет ввод. При stale revision нужно закрыть форму и открыть актуальную заново. История ручных движений последней смены показывает вид/сумму/дату/комментарий. Автоматические эффекты продаж и возвратов остаются в своих журналах, не копируются ручными строками.

Pending/неизвестный результат блокируют правку/закрытие/глобальные переходы и новую команду. Retry проверяет тот же commandId. Известный commit с ошибкой последующего чтения не считается неизвестной записью и не повторяется: предлагается перечитать. Activity recreation сохраняет форму/команду через ViewModel. Новый процесс читает committed journal и кассу; неподтверждённая форма не выполняется сама. Ошибка чтения не заменяет cash нулём. Ящик/принтер/Telegram не вызываются; оборудование08 отдельно.

## Приёмка

- CM01: exact positive cents/comma, optional note/limits, insufficient/overflow refusal; unchanged opening and receipts.
- CM02: atomic4writes/SQL abort/lost reply/replay; owner/gate/CAS/concurrent withdrawals; cash invalidates close preview.
- CM03: real sale/refund + manual journal reconciles once; close/count/difference/carry/reopen correct; corrupt payload refusal.
- CM04: shared form/preview/history, invalid amount, recreation, unknown-result input locks and same-command retry; known commit/read-failure recovery.
- CM05: actual APK1→2 preserves journal, closed summary, carry/current cash and original replays without duplicate.

После05a:05b сотрудники и auth/capabilities (D04 надо согласовать),05c расширенная история смен/кассовая сверка,05d аналитика/складские стоимостные отчёты. Уточнение состава/порядка по следующему checkpoint; M17 исключён. Physical tablet/dark/150%/cold-process UI остаются отдельной приёмкой.

## Наблюдаемый результат

05a CM01–CM05 ci-verified/API36: [FullCI40853537689](https://github.com/mendelev-main/M-POS-Android/actions/runs/40853537689)/7293d9c SUCCESS; production runtime93c1f2c, screenshot helper7293d9c.136JVM/211instrumentation/0failures/0skipped,13Python/harness/lint/debugAPK/testAPK; actual debugAPK1→2 passed. Manual deposit/withdrawal, exact cash/close/count/carry, four-write rollback/SQL abort/lost reply/replay, owner/gate/CAS/concurrent withdrawal, unfinished split refusal, corrupt journal refusal, recreation/input retention and unknown-result locks passed. SHIFT CASH UPDATE PASSED: journal, closed summary, carry/current cash and original command replays preserved without duplication. Full-window modal/history screenshots inspected: readable, no overlap. Initial40852329275 failed only the modal screenshot helper (two Compose roots); helper corrected without weakening monetary assertions. Single-owner scope only; roles/auth/analytics and physical tablet/dark/150%/cold-process UI remain pending. Main/signing/SQLschema8 unchanged.
