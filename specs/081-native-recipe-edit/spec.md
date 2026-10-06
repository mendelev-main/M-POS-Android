# 081 — правила рецептов и модификаторов редактора

MPosRecipeEditEngine проверяет состав draft: DFS left-to-right, Number quantities,
positive finite qty, наличие ингредиента, cycle trail, non-empty composite,
finite summed leaf quantities. Native iterative stack сохраняет first-error
precedence source, включая null component после предыдущей ошибки. Возвращает
unrounded ingredient totals всех simple продуктов, включая noStockTracking;
это validation редактора, не settlement stockConsumption фильтр 063.
Repository разрешает компоненты через authoritative Room catalogue, не через
присланный WebView массив. Никаких writes/резервов/stock changes.

Перед сохранением редактора native recipe → 080 type policy → native modifiers
→ existing async storage/media handler. Нативный result cache заменяет только
matching synchronous productIngredients draft validation, validateModifierGroups
и normalizeModifierGroup в этом gesture. Other helper calls/rollback идут к
reviewed source. Stale name/draft/groups/catalog/edited product/modal generation
отменяет late result; repeated save ждёт. Не переносим global cart helpers целиком.

Modifiers normalization совпадает с source: id/option id сохраняются или берутся
из existing uid-generated request IDs; String name/posName; max >=1 без нового
округления; min clamp; Number qty||1 и priceDelta||0. Требуются group name,
достаточное число options, существующий non-self product, отсутствие duplicate
productId, finite positive normalized qty. Для composite отдельно сохраняется
проверка finite positive recipeYield после type policy, перед modifier validation.

WeakMap cache сопоставляет каждый raw group object отдельно, возвращает copy
normalized DTO. Row-only signature для normalize не сканирует весь catalogue
на каждом варианте; validation/draft cache по full relevant fingerprint.
Сохраняются исходные price/cost/units/configuration, group field omissions,
photo/local persist before upload, backup v13. UI add/remove/reorder, inferred
recipe yield, display/base-unit conversion и edit configuration ещё source/102.
Rollback MPosNativeRecipeEditEnabled=false возвращает source validators;
для полного editor rollback отключить также MPosNativeCatalogEditEnabled.
Native read failure не сохраняет товар через молчаливый bypass.

Явная legacy compatibility: Infinity/-Infinity в modifier normalization fields
или numeric ids использует reviewed synchronous modifier path (без native DTO),
поскольку JS Infinity→JSON null и in-memory semantics нельзя подменять silently.
Native validation обычных finite groups/qty authoritative. Неизвестные fields
raw groups/options не передаются через bridge: source normalizer их не сохраняет.
При modifier compatibility native recipe/type checks по-прежнему активны.

37 actual source fixtures: nested/shared recipes/untracked leaves, missing/
cycle/null/zero/negative/legacy string-array qty/first error; modifiers clamps,
zero→1, negative/error, absent/self/duplicate products/default omission. JVM
immutability/generated IDs/yield плюс Room catalogue authority/no write. JS
actual source outputs, cache bypass counters (no duplicate source validators on
matching save), ordering, stale/transport/malformed/refusal/rollback/Infinity
compatibility, correlated frozen pure bridge. Полные JS/JVM/lint, physical pending.

Планшет: nested recipes/shared ingredient/cycle/missing ingredient/positive qty;
редактирование yield/units без смены original stock units; groups name/min/max,
missing/self/duplicate options и negative qty; new IDs/reopen/v13; modifiers с
priceDelta и noStockTracking при оплате/return; rapid edit/close/repeated save;
offline photo save/upload; no new availability/catalogue trigger.

Бизнес-вопросы (source preserved): qty=0/NaN становится 1 до validation;
default flag и extra modifier fields не входят в normalized saved group;
max может быть дробным в raw data; Infinity legacy path может сериализоваться
в null. Никакого автоматического пересмотра этих правил.

После публикации: 80/110 (72,73%), остаётся 30. Следующий 082 — команды
папок, порядка плиток, перемещения и навигации.

## Этап 082

082 переносит navigation mutations, не меняет product recipe, modifiers или stock. Category editor ждёт pending navigation command; source UI/theme сохранены.
