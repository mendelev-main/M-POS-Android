# Контракт первого инкремента: 01a

Статус: стартовая спецификация для реализации, код planned. 2026-10-24 пользователь подтвердил **BYN — белорусский рубль** для первого каталога. Ниже — native defaults для разработки. Они не задают неизвестную финансовую policy скидок/оплаты 03 и уточняются с migration/tests при реальном изменении.

## Что входит и чего ожидаем на выходе

Вход: текущий bootstrap/updater00.1 без Room. Выход: пользователь создаёт категорию, создаёт простой товар с начальным остатком, ищет/редактирует его, перезапускает приложение и видит committed данные. Некорректный ввод, конфликт, повтор нажатия и ошибка записи не создают частичный объект/дубли.

01a использует простую единицу `piece` и явно отображает «шт»; масса/объём, base/display conversion, stock flags и recipes вводятся 01b. Модификаторы/фото —01c. Формы не показывают неработающие поля будущих функций. Рабочая зона/продажи/сайт/импорт реальных данных не входят. До 05 режим single-owner обозначен честно, без заявления о ролевой защите кассира.

## Native validation defaults

| Поле | Контракт 01a |
|---|---|
| Название товара | trim; 1–200 Unicode code points; регистр для отображения сохраняется |
| Категория | optional categoryId; имя 1–100 code points после trim; normalizedKey casefold/ё→е/collapse whitespace уникален |
| Поиск | та же нормализация имени/категории; название хранится отдельно от search key |
| Цена продажи | BYN,0…999999999.99, до 2 знаков после запятой;0 означает ручную цену в будущем 02 |
| Себестоимость simple | BYN за 1 шт;0…999999999.99999999, до 8 знаков; не применяется округление продажи |
| Начальное количество |0…999999999.999999999, до 9 знаков; отрицательное opening в 01a запрещено, flags ещё не включены |
| Изменение остатка | только при создании через opening movement; существующий stock в редакторе read-only |
| Складские flags | noStockTracking=false, allowNegativeStock=false до 01b; состояние не угадывается по цене |
| Повтор/конфликт | commandId+fingerprint; expectedRevision; повтор прежнего результата/Conflict соответственно |

Числовой parser принимает десятичную точку либо запятую, trim краёв; пустое/NaN/Infinity/exponent/смешанные разделители/групповые пробелы отклоняет. Не допускает `parseFloat` частичного ввода и молчаливого округления сверх scale. Необязательное пустое поле UI должно иметь явный default; в 01a себестоимость/начальный остаток показываются как 0 заранее, а стертое значение при save считается ошибкой поля. Отрицательная цена запрещена. Форматирование после commit не меняет decimal.

Внутренний decimal converter: канонический plain string с точкой, без exponent/locale/лишних ведущих/конечных нулей, zero=`0`. Candidate storage boundary — до 18 integer digits и 12 fractional digits; запись вне границы отклоняется, не обрезается. Для 01a арифметика не требует деления; non-terminating recipe division и конверсия 01b получают отдельную precision policy. Цена/cost/quantity не складываются SQL как TEXT и не преобразуются в REAL. При финансовом 03 значения приводятся к money policy со своими утверждёнными rounding fixtures.

Лимиты выше — стартовые продуктовые ограничения для защищённого ввода, не измеренные пределы Android/Room. Изменение лимита/нормализации требует проверки существующих записей и конфликтов ключей, а не слепой перезаписи.

## Candidate первой схемы

Логические таблицы, окончательные Room definitions/exported schema создаются в PR01a:

| Таблица | Поля/ограничения |
|---|---|
| app_config | локальные registerId/ownerId, catalogCurrency=BYN, configRevision; один согласованный config |
| categories | id PK, name, normalizedKey UNIQUE, sortOrder, revision |
| products | id PK, categoryId nullable FK restrictive, name/searchKey, type=simple, unit=piece, currency=BYN, priceText/costText, revision |
| stock_balances | productId PK/FK restrictive, quantityText, unit, stockRevision; writable только stock transaction |
| stock_movements | id PK, productId FK restrictive, deltaText/unit, reason=opening, document/command refs, timestamp; UNIQUE commandId+effectKey |
| operation_results | scope+commandId PK, inputFingerprint, resultType/resultId/revision; принятый результат в той же transaction |
| editor_drafts | draftId PK, targetId?, expectedRevision?, versioned payload, updatedAt; не committed Product |
| audit_events | eventId PK, commandId, actorId, action, targetId, safe outcome/time; без пароля/PII payload |

Opening=0 тоже задаёт stock balance; допустимо не создавать zero movement, но эта политика фиксируется fixture и не выдаёт второй opening по повтору. Foreign keys удаления продуктов/категорий не каскадируют историю. Новая категория имеет стабильный ID; исходное имя не является FK.

Транзакции: CreateProduct(product+balance+ненулевой opening+result+audit+consumed draft); UpdateProduct(product revision+result+audit+draft); RenameCategory(category+result+audit); DeleteUnreferenced(category/product+result+audit). Проверки uses и revision внутри transaction. Сервис не вызывает сеть. Cancel editor удаляет только свой draft; очистка draft при сохранении происходит согласованно с committed product.

01a удаляет товар только если нет движений/других ссылок; ненулевой opening уже является историей и блокирует delete. Нулевой товар без движения и связей может быть удалён с подтверждением. Архивирование отдельным future action. При изменении последующих схем этот guard расширяется, не обходится ради UI.

## Порты и подключение

M02 определяет IDs/DecimalValue/Quantity/Currency/Error/CommandContext. Контракты CatalogRepository/StockLedger/TransactionRunner/ActorProvider/Clock принадлежат domain/core по владельцу. Implementations Room/Android подключает M01. M03 не импортирует feature Compose. Нет нескольких независимых баз для каталога/stock, которые невозможно записать общей transaction.

Existing UpdateRepository не переносится и не обнуляет markers. AppCoordinator публикует состояние открытия БД; future maintenance/install guard получает общий контракт. Реализация полноценного installer/financial guard —03, поскольку в 01a нет оплаты.

## Экранные маршруты

Start → «Товары» → список с поиском/фильтром/созданием → editor. Категории доступны отдельной командой из каталога. «Настройки → Обновления» сохраняется. На широком экране можно показать список+details, но один draftId независимо от ширины; back учитывает dirty state.

Editor: name/category/price/cost/opening при создании; stock read-only при редактировании; Save/Cancel. Saving блокирует повторное намерение, repository всё равно защищает commandId. Успех выходит в список и выделяет сохранённую запись. Validation показывает поле, StorageFailure сохраняет ввод/предлагает повтор, Conflict предлагает перечитать без молчаливой перезаписи. Loading/empty/no search results/read failure различимы.

## Fixtures для первой PR

F01: «  Чай  »/цена 2,50BYN/opening10 → название «Чай», price=`2.5`, stock10 после reopen. F02: цена`2,501`/`12abc`/пустое имя → отказ, ничего не создано. F03: один commandId дважды → один product/opening. F04: тот же ID с другим input → Conflict. F05: injected DB failure после product insert до stock/result → ни product, ни movement/result. F06: category rename/delete при связях → ID сохранён/удаление отклонено. F07: rotation/process recreation → draft, committed данные не подменены. F08: concurrent updates одной revision → одна победа/один Conflict. F09: existing updater preferences/APK channel после появления Room сохранны. F10: parser/converter roundtrip граничных значений и переполнение.

JVM — parser/normalization/validation. Room instrumentation — FK/UNIQUE/transaction/reopen/concurrent revision. Compose — форма/navigation/failure/draft. Реальный планшет — клавиатура/ширина/restart. До first PR проверить актуальные официальные версии Room/KSP и совместимость с закреплённой Kotlin/AGP, не добавлять их на основании одного текста спецификации.
