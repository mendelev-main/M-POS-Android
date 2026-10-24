# Каталог 01b: единицы и состав

## Текущий инкремент

Реализовано чистое Kotlin-ядро RecipeCalculator: единицы шт/кг/г/л/мл, физические количества компонентов, вложенный выход рецепта, агрегирование расхода, расчёт себестоимости и дефицита. Ядро подключено к Room schema2 и редактору: units/display, minimum/packaging, simple stock flags, recipe components/yield. Это продолжение01b; проверка CI и ограничения ниже. Фото/модификаторы01c и продажи не входят.

Источник: pinned Web1682c883, product-configuration.js (PRODUCT_UNITS, componentBaseQty, convertProductQty), android-pos-stock.js и tests/source-product-stock.test.cjs (yield около986, noStock около2329, negative около2540). В Web component.qty составного ингредиента — число партий; в новом доменном API RecipeComponent.quantity — физическое количество с явной единицей. При сохранении будущей схемы не смешивать эти представления. Старый backup будет конвертировать отдельный импорт07.

## Precision policy

Внутри одного расчёта количества — точные рациональные дроби на BigInteger. Нормирование выхода и вложенность не округляются. Сначала складываем все пути к одному простому ингредиенту и все строки запроса, затем один раз переводим итог в decimal scale12, HALF_EVEN. Граница: до18 целых знаков. Положительный расход, округлившийся в0, отклоняется с precision error: операция не может незаметно потерять списание. Это инженерное уточнение native вместо Number/15 significant digits Web; деньги продажи03 оно не определяет.

Примеры: расход1 на выход3 → одна порция0.333333333333; три порции в одном запросе → ровно1. Три отдельные будущие транзакции могут дать0.999999999999: это ограничение decimal ledger, без скрытого переноса остатка между чеками. 0.4г при базе кг →0.0004кг; сто порций →0.04кг. Отображение не меняет сохранённый расход.

Себестоимость включает все простые ингредиенты, в том числе noStockTracking, агрегируется точно и выдаётся scale12 HALF_EVEN; округление денег чека здесь отсутствует. Simple.cost задаётся за базовую единицу, composite имеет выход в recipe unit. Смена display unit сама не меняет цену продажи.

noStockTracking исключает только соответствующий simple из расхода и дефицита. allowNegativeStock сохраняет расход, но разрешает дефицит только этого simple. Другие ингредиенты продолжают ограничивать заказ. Нет собственного остатка composite. Резервы вычитаются из доступного остатка, отрицательный резерв и отсутствующий остаток — ошибки. Калькулятор не записывает движения и не реализует оплату.

Ошибки: несовместимые единицы, повтор ID, отсутствующий ингредиент, цикл, пустой состав, неположительный выход/компонент/запрос. Глубина свыше64 отклоняется с явной ошибкой. Цикл не превращается в частичный успешный результат.

## Хранение и редактор

Миграция1→2 добавляет поля products и таблицу recipe_components с FK owner→products CASCADE, ingredient→products RESTRICT. Старые товары остаются simple/piece, display unit берётся из базовой; balances/movements/results/audit/config/drafts не переписываются. Нет destructive fallback. Схема2 экспортирована KSP, схема1 сохранена для migration fixture.

Simple имеет фиксированную базовую единицу; выбранная совместимая display unit управляет вводом/выводом stock, minimum, cost и packSize. При сохранении количества и стоимость нормируются в base, конверсия без округления; если результат не помещается в scale12, сохранение отклоняется. Цена продажи остаётся за base unit и не пересчитывается. У нового товара base выбирается до первого сохранения. Тип сохранённого товара также фиксируется: для смены создаём отдельную карточку, не переосмысляем историю.

Минимум и упаковка — необязательные defaults будущей закупки04. purchaseLabel хранит код единицы закупки: compatible physical unit / pack / box / bottle / пусто. packSize хранится в base, в редакторе показан в текущей display unit. Отдельного purchaseContentUnit пока нет: физический смысл размера сохраняется нормированием, приёмка04 сможет использовать его без повторного списания. Закупки и уведомления о минимуме пока не выполняются.

Флаги применимы только к simple. Переключение flags/display unit не создаёт складского движения. Ручной ввод нового отрицательного остатка требует allowNegativeStock; отключение флага сохраняет уже отрицательный остаток, не исправляет историю. Прямая корректировка остатков по-прежнему без причины, с revision и delta ledger.

Composite не имеет balance row: список явно показывает «По составу». Редактор задаёт положительный выход/единицу и существующие ингредиенты с qty/unit; показывает вычисляемую себестоимость за единицу выхода. Поле products.cost для composite —0, не источник расчёта: recipeSnapshot всегда разворачивает актуальные simple costs. Все корни графа проверяются в транзакции перед commit. Повтор команды не пишет второй рецепт/движение. Ошибка связи/цикла/хранения откатывает product, components, ledger и result. Удаление используемого ингредиента запрещено; после удаления зависимостей composite без истории можно удалить.

Черновик JSON дополнен конфигурацией и ингредиентами; старый JSON01a открывается с defaults. Редактор читает product/balance/components единым Room snapshot. Fingerprint простого товара с defaults01a сохранён для replay ранее завершённой команды после обновления.

UI использует MposSelect/MposFlag вместе с существующими Field/Button/Dialog. Базовая цена и остаток не получают отдельных локальных стилей. Переключатели используют Material native control с общей обёрткой/label/accessibility; точная физическая визуальная приёмка и font scale остаются pending.

## Проверки

JVM: RecipeCalculatorTest, CatalogConfigurationRulesTest, CatalogRulesTest. Room: CatalogConfigurationTest — unit conversion/flags/negative/revisions, graph/repeat/delete guards/rollback, reopen+expanded draft, actual schema1→2 migration. Compose: CatalogConfigurationUiTest — kg→g, recipe yield/components, recreate и повторное открытие сохранённого состава. Старый LaunchTest и update-e2e остаются regression gates.

CI-verified на commit6acfe83: [run40094842882](https://github.com/mendelev-main/M-POS-Android/actions/runs/40094842882).19 JVM tests,4 Python tests, lint/debug build; по10 instrumentation tests на API28/35. В их числе actual schema1→2 migration, сохранность stock/config/draft/results, граф/rollback/delete guards и Compose kg→g/recipe/recreate. На обоих API внешний update-e2e подтвердил CATALOG UPDATE PASSED (Room product/stock8), UPDATE E2E PASSED (проверка/скачивание/восстановление/Cancel/system install1→2). Signing/publish skipped: develop.

Первоначальный run40094594582 выявил2 ошибки fixtures (kg product с display piece; optional indices schema JSON), исправлены в6acfe83. Экспорт schema2 из успешной KSP сборки сохранён в Git. После CI уточнена только подпись поля продажной цены «BYN за базовую единицу», чтобы display g не воспринималась как цена за грамм; повторный полный pipeline ради текста не запускался. Физическая визуальная приёмка pending.

## Остаётся

Физическая приёмка на планшете: поля/скролл/числовая клавиатура и native controls в масштабе шрифта. Перед переходом02 закончить01c (модификаторы/фото), порядок категорий и оставшиеся acceptance этапа01. Выпуск01b — после завершённого блока через PR develop→main; этот диагностический запуск в develop не публикует обновление.
