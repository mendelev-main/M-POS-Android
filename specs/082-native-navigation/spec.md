# 082 — команды папок, порядка и раскладки

MPosNavigationEngine выполняет save/remove folder, move product, visible-parent
reorder; root add/remove/move tile и normalization positions. Pure DTO version 1,
никаких inventory/payment/backend effects. Category products передаются только
с id/category/sortOrder, без фотографий, финансовых полей и архива чеков.

Normalize повторяет strict version===1, valid category/items, duplicate category
и type:id removal, folder name trim.slice(0,80), один уровень folder, missing
parent → root. Category items сохраняют папки, удаляют отсутствующие products,
добавляют недостающие продукты в stable sortOrder. Folder names сравниваются ru
lowercase, пустое/>80 name отклоняется; rename сохраняет id. Remove folder
возвращает children в root; move product меняет parent и ставит в конец. Reorder
меняет только visible-parent slots, остальные позиции сохраняет. Missing reorder
id всё равно сохраняет normalized navigation, как reviewed update callback.

Root duplicates разрешены. Add: максимум 20, positions в 5 колонках; duplicate/
invalid coords получают первую свободную ячейку. Remove сохраняет Number/splice
semantics, включая отрицательные индексы. Move: cols из существующих view grid
metrics, Manhattan distance до 29 включительно с исходным row/col tie order,
исходный fallback row+1. Pointer geometry/threshold/capture/clone/suppression
350ms и visual rendering остаются JS; cancelled drag не сохраняется. Root
pointerup использует reviewed cancellation cleanup и отдельный native mutation.

Adapter: native calculate → matching input/context/form check → существующий
MPosCore.Storage.set Room compatible document → ack → in-memory dataset/render.
Folder saves сохраняют _posNavigationBusy semantics. Root commands FIFO32;
repeated additions создают отдельные tiles. Пока есть pending command, новый
root drag и category editor mutation ждут. Никакой JS saveKey для перехваченных
writes; root add normalization+add объединены в одну final document write вместо
исходных двух одинаковых final writes. Layout category settings/aliases сохраняет
categoryLayoutSnapshot; backup keys/v13/Room schema неизменны.

Pure late error не объявляет uncertain write. Write timeout блокирует следующие
команды до restart/recovery. Input/form/context change до write отменяет result;
после начавшейся записи durable dataset применяется, новый modal не закрывается.
Если storage-relevant in-memory data изменилась во время write, не перезаписываем
её поздним ack, блокируем critical actions до reload. Bridge result shape и
native authority проверяются, failure не применяет memory patch.
Rollback MPosNativeNavigationEnabled=false возвращает reviewed command handlers.

normalizePosNavigation/posCategoryItems/read rendering, updatePosNavigation
compatibility callbacks, open/close/search/edit UI и passive ensureLayoutPositions
render repair ещё source. Это native mutation boundary, не полный native POS UI
или полный native navigation query runtime; workspace UI остаётся задачей 101.
Нет local APK assembly, нет manufacturer/экранных dependencies, speedup не измерен.

MPosBridgeJson сериализует surrogate UTF-16 units как JSON unicode escapes для
native storage results; значения string не меняются, paired emoji и legacy lone
surrogate проходят без замены. Это сохраняет source .slice(0,80) semantics при
передаче результата. Остальной JSON/control escaping остаётся JSONObject.

Проверки: 52 actual reviewed command fixtures (folders/duplicates/name/missing,
move/reorder, root limit/positions/nearest/invalid coords), JVM immutability,
strict normalization/UTF-16 limit/JSON roundtrip. JS source fixtures, ack order,
field/data/context stale, storage failure, pure-vs-write timeout, FIFO duplicates,
rollback и actual pointer cleanup/cancel; correlated pure bridge без writes.
Полные JS/JVM/lint. Физическая приёмка pending.

Планшет: create/rename/remove folder, move в folder/root, reorder внутри folder
и category; root duplicates/20-limit/add/remove/drag occupied cell/cancel;
rapid add/close/name change, save отказ; restart/v13 с folder/root order/coords;
имя с кириллицей/emoji на границе 80 UTF-16; catalogue/availability не отправлять.

Business questions preserved: лимит name считает UTF-16 units, не graphemes;
normalize может обрезать emoji; root duplicates разрешены; remove root tile
использует splice и принимает отрицательный индекс; normalization удаляет
unknown navigation extensions и stale product IDs. Их пересмотр не входит в 082.

После публикации: 81/110 (73,64%), остаётся 29. Следующая граница 083 —
сотрудники, роли и авторизация без молчаливого изменения политики.

083 command boundary is now specified in ../083-native-employee-commands/spec.md; native credential verification remains outside that boundary.
