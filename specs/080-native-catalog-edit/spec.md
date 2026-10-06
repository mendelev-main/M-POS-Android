# 080 — правила редактирования товаров и категорий

MPosCatalogEditEngine принимает typed JSON version 1. Product policy запрещает
пустое имя/пустой composite, смену типа товара с unreturned stockConsumption,
назначенной stockUnit или входящими transitive recipe dependencies. Прямые и
циклические зависимости обходятся с seen, как source. Repository читает полные
compatible products/orders из Room, без передачи архива из WebView; решение
pure, не резервирует остатки и не меняет хранилище. UI permissions, photo upload,
price/cost/unit form builder, delete authorization и persistence handler ещё JS.

Category save/channel/deleteCheck — native decisions и full patch: trim имени,
case-insensitive duplicates, add/rename order, category у товаров, id category
плиток, first matching navigation entry, colors/symbols/menu/order maps. Source
limitTileSymbol сохраняет platform grapheme semantics (на этом этапе не мигрирует).
Занятая категория не допускается к delete confirmation, неизвестная не делает
ничего. Подтверждение удаления/пароль и окончательный delete остаются 083/102.

Adapter применяет patch с сохранением object references/compatibility online
alias, сохраняет navigation → products (edit) → layout → close/render/toast.
Category channel сохраняет layout и открывает список, как source. Writes остаются
существующим JS saveKey/Room путём, без новой multi-document atomicity и без
утверждения durable acknowledgement category save. Product оригинальный async
storage/media path работает после native policy gate. Backup v13 неизменён.

Pending/read, changed form/context/catalog/history/modal generation, malformed
patch, bridge failure или rollback не применяют late result. Нет автоматического
catalogue/availability sync. Source publication guard 033 сохраняет ограничение
availability только после сохранённой оплаты. Не обещаем измеренный speedup.
Rollback MPosNativeCatalogEditEnabled=false возвращает reviewed handlers.
На native policy failure не переходим молча к source writes.

45 category fixture от actual reviewed handlers: rename/add/duplicate/empty,
missing old category, channels/used delete, Unicode trim/keys. JVM input
immutability, type/history/returned/unit/dependency policy + real Room authority
против forged client archive. JS source fixture regeneration, object references,
persistence order, stale/malformed/failure, compact product request, correlation.
Физическая приёмка pending. Source файлы неизменны, adapters сохраняет sync.

Планшет: add/rename категории, цвет/символ/каналы, порядок/nav/плитки; duplicate
и used delete; edit simple/composite, blocked type с units/зависимостями/receipt
и после return; фото offline/local save/upload; rapid close/fields/rollback;
restart/v13; availability не отправляется просто от category/product edit.

Бизнес-вопросы: при edit simple source сохраняет прежнюю cost вместо введённой;
category final writes не объединены в transaction. Эти правила не меняются
молча. Recipe/modifier validation переходит в 081, остальные формы — 102.

После публикации: 79/110 (71,82%), остаётся 31. Следующий 081 — рецепт/модификаторы.

## Этап 081

081 ставит native recipe gate перед 080 policy и native modifier gate после него; matching cache исключает повторное source вычисление. Editor auth/media/write handler остаётся прежним.
