# 109.09 — добавление товара и формы цены

Статус участка: production подключён, Actions pending. Общий этап не закрыт:
115/129 задач, внутри 109 — 8/20. Physical acceptance pending 110.

MPosCartAddRepository читает products/currentOrderSession в Room transaction,
проверяет authority, native navigation и recovery. View не сохраняет данные.
Commit проверяет SHA256 обоих документов, читает текущие выбранные modifiers,
рассчитывает цену существующим MPosConfiguredPriceEngine, делает whole-cart stock
preflight и сохраняет session/shadow/projection одной transaction. Только после
ack обновляются отображаемые items. Нет сетевых, кухонных или платёжных эффектов.

MPosCartAddModel нормализует min/max, количество и delta; native controller
владеет выбором и ручной ценой. Минимум/максимум, single/multi choice, DOM order,
dataset string identity и первый match для duplicate option IDs сохранены.
Неизвестный modifier отклоняется; delta влияет на цену один раз, modifier qty —
на расход. Неполные legacy IDs используют source compatibility path до commit,
без генерации/записи новых ID в каталог. Price arrays также сохраняют rollback.

Обычные позиции с одинаковой сигнатурой modifiers объединяются только при
отсутствии comment/discount. Исторические name/price/basePrice строки не меняются;
регулярное добавление может объединиться с прежней manual строкой, как source.
Ручное добавление всегда создаёт отдельную строку. Положительность manual input
проверяется до округления: 0,004 допустимо и даёт basePrice 0. Numeric string
quantity сохраняет source странность: "2" + 1 проверяет остаток для 21,
затем ++ сохраняет число 3. Эти правила не исправлены скрытно.

Новые cartLineId — уникальные strings; существующие ID не переписываются.
Ключи и backup v13 shape сохранены. Для пустого устройства первая строка создаёт
совместимый session только после успешной проверки. WEB/kitchen/paid parts и
неизвестные сохранённые extensions сохраняются в native commit. Остальные поля
контекста заказа пока берутся из явного currentOrderSessionSnapshot: полное
native order draft ownership всё ещё остаётся внутри 109.09.

## JS переход и сохранение

Default native workspace вызывает cartAddView/cartAddCommit напрямую. Выбор
модификаторов и ввод цены не читаются из HTML. Вход из folder в форму закрывает
папку до формы; обычное добавление закрывает папку после успешной записи.
Фон остаётся native. Cancel/Back не сохраняют черновик; busy исключает повторы.

Общий barrier для native add и cart-item save сообщает CartOperations pending.
saveCurrentOrderSession во время mutation откладывается: после известного ack
snapshot берётся заново с уже сохранёнными items и актуальными metadata.
Изменение metadata само по себе не теряет добавленные items. Новый runtime
не получает старую projection/deferred save. Ошибка/неопределённый ack не
повторяется через source handler и блокирует legacy save/следующее добавление
до перезапуска и восстановления native session. При известном stock отказе
можно исправить форму и явно повторить; авто-повтора нет.

Rollback MPosNativeCartAddEnabled=false возвращает source add/forms; общий
MPosNativeWorkspaceReadModelsEnabled=false включает старый workspace adapter.
Runtime failure после начала commit не включает автоматический rollback write.

## Дизайн и проверки

MPosNativeTheme/Manrope, две темы, сетка вариантов, scroll, 48dp buttons,
увеличенный font scale. Primary — «Добавить в заказ»; manual ввод сохраняет raw
string до native проверки. Detached button предыдущей фазы не отправляет второй
save и не читает input новой формы.

Room: cold start, modifiers/price/stock, merge/history/numeric string, отдельные
manual строки, доокруглённая положительность, duplicate/CAS, recovery,
SQLite rollback shadow/projection. Pure model: order/signature/strict IDs,
missing modifier. Controller: min/select/manual transition, stale controls,
busy/cancel/themes. JS: typed commands, ack/late response/cancel/folder,
deferred metadata saves и uncertain repeat gate. Выполнение — GitHub Actions.

На планшете, pending 110:

- Добавить простой товар, рецепт с ингредиентом вне раскладки и товар с
  modifiers; проверить расход после оплаты, delta один раз и qty modifier.
- Проверить обязательный min, single choice и multi max, отмену и Android Back.
- Цена 0 → manual, 0/пустой input отклоняются, 0,004 проходит; две ручные
  позиции отдельные, обычные одинаковые объединяются с прежней ценой строки.
- Недостаток остатка/недоступный modifier не меняет session; повтор только явный.
- Открыть modifier/manual из папки и проверить возврат к каталогу категории.
- Background customer/loyalty обновление при добавлении, force-stop, импорт,
  перезапуск и неопределённый ack: нет потерянной/добавленной дважды строки.
- Light/dark, keyboard, landscape, длинные названия и большой системный шрифт.
