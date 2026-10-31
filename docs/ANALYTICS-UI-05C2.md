# 05c.2 — экран аналитики

Статус: UI01–UI05 implemented/validation pending. Основание — [ядро05c.1](REPORTS-05C.md), pinned Web1682c883 analytics.js/pos.html; regression source-product-stock.test.cjs «analytics keeps local payment, cost and grouping rules within the selected period». Android admin presentation/session adapter не заменяет analyticsData.06/09 в TODO, M17 исключён.

## Визуальный контракт

Используется существующая MposTheme: Manrope, background#F5F4F0/surface#FFF/ink#1B1F2A/muted#767C8C/accent#0E8F6F/border#E7E4DD и тёмные semantic counterparts. Шрифт label11sp/value24sp, panel title18sp. Новый shared MposAnalyticsCard переносит source analytics-kpi (radius18/padding16/border1) и analytics-card (radius20/padding18/border1); gap12/14. Close/Dialog/Fields/buttons прежние общие, без локального крестика.

Композиция: общий navbar → панель периода с датами/быстрыми периодами →4 колонки KPI на широком экране,2/1 по эффективной ширине с учётом font scale →2/1 панели оплат → пояснение формул/пустого периода и отдельное сообщение будущих показателей. Всё в LazyColumn; длинные суммы не обрезаются до Long и не превращаются в scientific notation. Card shadows не добавлены: сохранён native flat/border standard.

В этой итерации: продажи/возвраты/net/новые чеки/averageSale/refundCount, cash/card/delivery обеих групп. Бары — только визуальное отношение allocation/total (decimal→Float для progress, не денежный расчёт); текст денег остаётся точным BigDecimal(BigInteger,2), всегда2 знака/запятая/BYN. Прибыль/COGS, сотрудники/категории/товары —05d, статическая явная пометка будущей реализации. Нет фиктивных0 для неготовых показателей, нет loyalty/server KPI.

## Период и состояние

Первый период — сегодня, как Web. Default zone берётся из Clock.systemDefaultZone устройства, явно показана и закрепляется в SavedStateHandle для этого экрана; recreation/process restore не меняют выбранные from/through/zone скрыто. Кнопки Сегодня/7/30 считаются по календарным датам в этой зоне; ручной shared dialog принимает ISO ГГГГ-ММ-ДД и подтверждает обе даты атомарно. Неверный ввод не меняет applied period/report; ошибка видна в диалоге. Даты будущего не запрещены: такой валидный диапазон может быть пустым, как в источнике.

При запросе старый результат убирается: нет старых сумм под новыми датами. Latest request serial + cancellation fence не допускают старый ответ даже при игнорировании отмены adapter. Loading/error/retry/empty различаются; ошибка не выдаёт0. Refresh перечитывает тот же показанный диапазон, не двигает его незаметно на следующий день.

## Доступ и lifecycle

Полный денежный snapshot только для active admin, как контракт05c.1; без входа показана кнопка Настройки, access.snapshot вообще не вызывается. Данные кассирских quantity/percentage группировок относятся к будущему05d, сейчас нет выдачи полного отчёта кассиру ради client-side masking.

AdminForms фиксирует permit до IO и request serial. Logout/изменение generation очищает report/error/pending и закрывает date dialog; UI дополнительно проверяет epoch прямо при render, поэтому запоздавший callback/быстрый logout→login не восстанавливает прежние числа. Новый вход/возврат в route запускает новое чтение. В SavedStateHandle только reportFrom/reportThrough/reportZone; report/credential/session token не сохраняются. Activity recreation сохраняет выбранный период, новый locked runtime не восстанавливает деньги. Административное чтение не блокирует кассовую навигацию, его результат можно безопасно отбросить при смене периода.

## Приёмка UI01–UI05

- UI01: реальная topNav-analytics открывает read model вместо PlannedModuleScreen; route/период переживают recreation.
- UI02: правильные отрицательный net/продажи/возвраты/average/count, manual interval/presets, invalid dates keep applied range.
- UI03: error/retry, stale read after newer query не публикуется; сохранённые даты читаются без восстановления денежных данных в locked runtime.
- UI04: logout/relogin закрывает dialog и очищает money, старое чтение не получает права новой session.
- UI05: shared Web-derived cards/dialog/buttons, narrow520dp/font150%, scroll до способов оплаты, отсутствие deferred customer blocks; screenshots wide/locked/narrow.

ReportViewModelTest3, ReportUiTest2 и расширенный TopNavigationUiTest. Component screenshots используют isolated host и synthetic25BYN delivery fixture; полная main navigation проверяется отдельно. Exact pixel parity с Web, dark и физический планшет остаются отдельной приёмкой. SQLschema8/signing/main прежние. Следующий блок05d: расширенные отчёты/исторические группировки, не начинается автоматически.
