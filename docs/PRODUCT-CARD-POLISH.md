# Карточка товара: компактный интерфейс

10.11.2026. Локальная main, без commit/push. Источник текущего Web: product-editor.js blobee5161c2c5dc1fe587a9ef43beb42b38d639fdce; product-configuration.js bloba6bb8a230d3a650d49fb4c42f1027f448d47b57c. Прочитаны через GitHub connector; глобальный reference config не изменялся. Уточнение владельца «компактнее и удобнее» задаёт явную layout variation.

PCE01 implemented: «Назад» без стрелки; подпись типа убрана из левой навигации, в summary справа «Товар» либо «Составной товар». «Себестоимость» сохранена, следующая строка переименована в «Маржа». Значение по-прежнему цена−себестоимость в BYN, не процент. Переключатель типа для нового товара в Основном сохранён; тип уже сохранённого товара не меняется.

PCE02 implemented: «Выбрать фото» / «Удалить фото» расположены в одной Row; при отсутствии фото удаление disabled. Удаление меняет draft.photoId, прежняя staged-photo/save логика сохранена.

PCE03 implemented: модификаторы — отдельные cards групп, общий header «+ Группа», имя/мин/макс и статус обязательности. Товар/название POS расположены попарно; количество списания с единицей/доплата BYN — в следующей строке с компактной framed trash action. На узком месте поля перестраиваются вертикально. Группа/опция keyed по ID. Новый group minimum=1, maximum=1 соответствует Web; существующие minimum/maximum сохранены. Native product picker открывается до добавления опции, без промежуточной пустой ссылки на товар — удобный сокращённый сценарий. Duplicate/self-product filters, maximum20 groups, точные сырые количества и validation при сохранении не ослаблены. В отличие от JS coercion неверный raw ввод не заменяется молча нулём.

PCE04 implemented: у простого товара оба stock policy switches в начале раздела, перед единицами/стоимостью/остатком. ProductEditorSwitch общий для этих двух строк и Онлайн-меню: подпись слева, switch справа, единый доступный toggle Role.Switch. Флаги независимы; noStockTracking отключает поле фактического остатка. Составной товар продолжает списывать ингредиенты по их правилам; неподдерживаемые composite stock flags не добавлялись.

PCE05 validation: compileDebugKotlin/compileDebugAndroidTestKotlin и адресные ModifierRulesTest/ProductStockRulesTest. ProductCardLayoutTest3 fixtures: summary/back/photo row; switch ordering/state/no DB product or stock writes; add required group/picker/exact qty0.0004/price/delete. CatalogExtrasUiTest адаптирован к tags/новому названию удаления, прежние photo/modifier persistence assertions сохранены. Android fixtures только скомпилированы; устройство/pixel acceptance владельцем pending.

Не менялись Room schema, рецептуры, расчёт себестоимости, проведение продаж или публикуемый backend. Никаких пользовательских данных/фото/остатков ради проверки не изменяли. Evidence в harness/build-log.md.
