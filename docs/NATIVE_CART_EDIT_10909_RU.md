# 109.09 — удаление и количество

Production команды cartRemoveCommit/cartQuantityCommit переданы Kotlin/Room.
Проверки pending GitHub Actions. Общий этап 109.09 in progress, 115/129 (89.15%).

Одна transaction читает authoritative session, проверяет native navigation,
recovery и точное совпадение items с проекцией. Положительное количество
проверяет весь заказ через native stock preflight. Session/shadow/projection
сохраняются атомарно; JS принимает только acknowledged persisted items.
Изменение количества сохраняет историческую цену и неизвестные поля строки.
Duplicate keys: preflight всех matching строк, изменение первой; удаление всех,
как source. Numeric strings сохраняют JS addition; нечисловой результат
отклоняется без записи, нехватка остатка также не меняет заказ.

Удаление последней строки полностью очищает контекст, kitchen markers и split
payments. Уменьшение количества до нуля сохраняет контекст даже пустого заказа.
Частичное удаление сохраняет customer/WEB/kitchen/paymentDraft/extensions.
Параметры контекста ещё берутся из explicit session snapshot; полное native
владение order draft остаётся отдельной частью этого этапа.

Общий session-save barrier применяет resetState до отложенного JS snapshot.
Неопределённый ack блокирует повтор и source save до перезапуска; автоматического
повтора нет. Новый runtime не получает старые items или resetState.
Rollback MPosNativeCartEditEnabled=false возвращает исходные обработчики до
начала commit. Runtime ошибка commit не переключает запись на source.

Проверки Room: remove vs decrement reset, duplicate keys, stock rejection,
legacy numeric strings, context/extensions, stale projection, recovery и SQLite
rollback. JS: typed commands, ожидание ack, reset до deferred save, stock refusal,
uncertain repeat gate и late runtime acknowledgement.

Планшетная проверка pending 110:

- Удалить последнюю строку WEB/доставочного заказа с клиентом и split оплатой:
  контекст очищен после сохранения и после перезапуска.
- Уменьшить последнюю строку до нуля: клиент/WEB/комментарий остаются.
- Частично удалить заказ с modifiers/скидкой/кухонной отметкой: остальные
  строки и их исторические цены/состояние сохраняются.
- Нехватка остатка не меняет количество; force-stop и импорт не вызывают
  повторную операцию, печать или сетевую публикацию.

## Следующая граница — параметры заказа

Полный следующий участок включает native форму и Room команды:

- View читает сохранённые session и delivery rates, native draft держит четыре
  поля (подпись/имя/телефон/адрес); ввод не берётся из DOM.
- Save применяет ECMAScript trim через MPosOrderContextEngine, сохраняет
  customer identity/loyalty/comment и одной transaction записывает session.
  Скрытый адрес недоставочного заказа сохраняется пустым, как текущая форма.
- Выбор типа сразу сохраняет type; изменение type снимает tariffSelected,
  недоставочный type обнуляет fee. Cancel не отменяет эту уже записанную смену.
- Выбор тарифа сверяется с актуальным deliveryRates по Number(amount), сразу
  сохраняет fee/selected и пересоздаёт форму. Отображение total берётся из
  native totals, без альтернативного финансового расчёта.
- Пересоздание формы после type/tariff сохраняет прежнюю особенность: несохранённые
  текстовые поля теряются. Пример: введено имя, выбран «Доставка» — имя возвращается
  к ранее сохранённому. Это не меняется незаметно в миграции.
- Stale navigation/session/rates, recovery, duplicate tap, stock/context
  continuity, SQLite rollback, unknown ack и force-stop проверяются отдельно.
- MPosNativeTheme/Manrope, compact type/tariff choices, scroll, 48dp, обе темы,
  keyboard/font scale; primary «Сохранить», secondary «Отмена».

Эта граница пока planned. Полное native владение всеми order draft mutations
и product editor остаются обязательными до закрытия 109.09.
