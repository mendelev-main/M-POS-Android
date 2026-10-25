# 03a — минимальная смена: контракт перед реализацией

Статус: specification-only, runtime ещё отсутствует. Задача — явный допуск будущей продажи в открытую смену, а не полное управление кассой05. Предпосылки — [сверка01/02](PHASE-02-REVIEW.md), текущие Room schema5/постоянный native package, [данные](DATA.md), [состояния](STATE-MACHINES.md). mpos-phase/mpos-domain; UI из общего DESIGN-SYSTEM.

## Источник и принятые границы

Pinned Web1682c883, js/features/shifts.js: currentShift/shiftTotals/cashDrawerBalance, renderShiftScreen, submitOpenShift, submitCloseShift. Web переносит countedCash последней закрытой смены в openingCash новой; первая открывается с0. Close принимает фактически пересчитанную неотрицательную сумму и сохраняет разницу. Native сохраняет этот UX: перед open показывает перенос и ссылку на предшествующую смену, без скрытого редактирования остатка. Внесение/изъятие отдельными движениями —05, не прямая замена openingCash.

Single-owner03a явно показывает «Локальный владелец». Полноценный выбор сотрудника/проверка ролей из Web появятся в05; это документированная этапная граница. Пароли/административная runtime session Web не переносятся. Telegram/принтер/ящик —08, реальные внешние действия тестами не вызываются.

## Хранение и команды

На устройстве один registerId, не более одной active shift. Shift содержит id/registerId/status, openedAt/closedAt/zoneId, actor snapshot, openingCashMinor/counting/expected/difference BYN и openingSourceShiftId. Closed snapshot неизменяем. Время задаётся инъекцией Clock и сохраняется один раз; повтор команды не создаёт новое время/ID.

OpenShift(commandId, expectedRegisterRevision, expectedPreviousShiftId, actor) читает последнюю закрытую и active в transaction, сверяет revision/перенос, создаёт shift/active link/результат/audit атомарно. Повтор идентичного commandId возвращает исходный shiftId даже после закрытия. Тот же ключ с другим fingerprint отклоняется. Параллельные open: один победитель, второй Conflict. Отказ записи не меняет статус UI и не оставляет частичную смену.

CloseShift(commandId, shiftId, expectedRevision, previewToken, countedCashMinor, actor) требует active shift и свежий preview. Записывает closed snapshot/result/audit и очищает active link одной transaction. Повтор возвращает прежний итог, новая команда к закрытой смене отклоняется. Preview учитывает revision денежных событий: sale после preview требует обновления итогов/повторного подтверждения. Counted≥0, максимум и overflow minor units проверяются явно, comma input нормализуется без Double.

До появления продаж expectedCash=openingCash; пример opening0/counted0 →difference0, повтор open переносит0. Общий контракт ожидаемой кассы: opening + cash net sales − cash refunds + deposits − withdrawals; card не меняет наличные. Уплаченная купюра50 при наличной продаже40/сдаче10 увеличивает кассу на40. Наличная выдача refund не дублируется withdrawal. Полная реализация sale/allocations и cash movements вводится позже с отдельными gates.

Будущий CommitSale проверяет active shiftId и revision внутри той же БД transaction. При гонке close/sale либо sale включена до close, либо отклонена после close. Черновик current/parked не является оплатой: закрытие смены его сохраняет, для оплаты требуется новая смена. Runtime03a ещё не разрешает продажу и не создаёт фиктивных receipt.

## Экран и отказ

Общий header/navigation «Смена». Нет активной: последние итог/перенос, локальный владелец, открыть → общий confirm dialog. Открыта: ID/время/оператор/opening cash, закрыть → preview/count input/разница/подтверждение → committed summary. Например ожидается145, пересчитано140 → показать−5 BYN и сохранить именно140, будущий перенос140. Не обнулять разницу и не создавать автоматически расход5. Ошибка сохраняет введённую сумму и прежнюю открытую смену. Один общий Close/Primary/Secondary, без нового визуального языка.

Loading/empty/error, pending disabling и ViewModel main publication обязательны. Незавершённая запись не выглядит успешным close. Установка APK ожидает завершения shift write; после commit сохраняется рабочее состояние. После появления sale/recovery тот же guard должен охватывать финансовую операцию, а не только экран смены.

## Порядок реализации и проверки

1. Pure shift model/minor money parsing и команды: initial0/carry140, open/close/replay/fingerprint/revision/overflow/clock.
2. Room migration5→следующая схема с экспортом; одна активная смена обеспечена БД/transaction, не только кнопкой. Сохранить catalog/current/parked/home и updater settings. Проверить пути всех поддерживаемых схем, rollback на реальном write, конкурентные open/close, reopen.
3. Shared Compose экран + подтверждения/ввод/ошибки/recreate. Current/parked сохраняются при open/close, отсутствует кнопка успешной продажи.
4. Расширить actual APK replacement fixture данными смены и проверить постоянный package/cert; Main merge/release только после готового блока и отдельной задачи выпуска.

H01/H02 в03a покрывают только минимальные open/close/admission; S01–S05/полные H01–H04 остаются planned. Документация не является evidence прохождения этих тестов.

## Подтверждённая часть D02 —2026-10-25

Владелец подтвердил BYN/net line HALF_UP2:0.05×3×(1−10%)=0.135→0.14; итог складывается из округлённых строк. Возвраты первого этапа — только полный чек. Наличный refund сегодняшней смены уменьшает её cash ровно один раз; исходная продажа остаётся исторически во вчерашней смене. Подробности [денежного контракта](MONEY-03.md).

Открыты до соответствующего sale-кода: виды/порядок скидок и их allocation, доставка/loyalty/gift и split rounding fixtures, card outcome/reversal contract (без SDK — только отметка способа), полный refund mixed payments/delivery. Это не требует молча принимать новые финансовые правила при реализации минимального open/close.
