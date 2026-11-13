# Период и отправка аналитики — 13.11.2026

Прямое поручение владельца расширяет прежний scope аналитики: добавить PDF sharing. Всегда одна длинная страница, оформление «Панорама», оба нижних блока полностью раскрыты независимо от состояния UI. Дополнительное уточнение: кг и литры отображать с2знаками; расчёты точные.

## План / acceptance

- PDF01: выбранный preset выделен primaryContainer + primary text/border, контур1dp не исчезает. Custom range выделяется при отсутствии совпадения с Сегодня/7/30 и при открытом диалоге. Preset требует through=today в report.zone и точную inclusive длину. Используется scoped ReportPeriodButton, без изменения MposPosControl остальных экранов.
- PDF02: новая иконка share рядом с refresh, стандартное Android ACTION_SEND/application/pdf chooser. Во время подготовки индикатор в самой кнопке, без большого баннера. Нельзя отправить loading/error/null snapshot. Повторное нажатие блокируется.
- PDF03: Native AnalyticsPdf векторно рисует Manrope/цвета/cards19/padding20, сводки, график выбранной выручки/прибыли, категории, donut оплат, fulfillment, сотрудников и полные товары/движение. Форматирование количеств/денег и domain projections переиспользуются. PDF сохраняет компоновку/стиль экрана, не является screenshot viewport: не содержит системной навигации/интерактивных кнопок и раскрывает обе таблицы полностью. Заголовок и диапазон дат включены.
- PDF04: measure pass без Canvas, затем один native PDF page с точными высотами текстов/строк; без гигантского Bitmap. Для очень длинной страницы пропорциональное векторное масштабирование до14400points по максимальной стороне, без обрезки/разделения; для чтения длинных отчётов понадобится zoom. Нет искусственного ограничения числа товаров/отбрасывания строк.
- PDF05: IO/background, фиксированный report на момент нажатия. Смена периода/report/admin generation или уход отменяет задачу; final snapshot/session check перед chooser. Cancellation проверяется между строками/блоками, partial/temp files удаляются при отказе. Успех не появляется до записи файла.
- PDF06: FileProvider `${applicationId}.reports`, exported=false, cache-only analytics-share root, временный read-only grant с ClipData. APK updater provider не расширяется. Файлы private cache, старые (>7суток) удаляются при следующем экспорте; Android вправе чистить cache. Нет storage permission/backend/Telegram/реальной отправки без выбора пользователя.
- PDF07: l/kg только display setScale2 HALF_UP, ml/g/piece без изменения. Передаются исходные BigDecimal в расчёт/сортировку; движение остаётся gross расходом.

## Архитектура и проверки

ReportExportLayout/ReportPeriodSelection — pure domain utility для PDF geometry и выбора preset. AnalyticsPdf — Android PDF/Canvas adapter над уже прочитанным SalesReport; БД не читается/не пишется, схема не меняется. Применяется [стандартный PdfDocument Canvas](https://developer.android.com/reference/android/graphics/pdf/PdfDocument.html), не WebView/HTML runtime и не новая библиотека.

Unit: preset current/исторический/custom/bad dates; обычная и длинная одностраничная geometry. Android integration (без Activity/Compose UI tests): реальные PdfDocument→PdfRenderer, pageCount1/размеры/text contents, обе темы, все500товаров и нижний блок, URI read grant/отказ чужого cache path, cancellation cleanup, точные исходные литры. Fixture PNG сохраняются только для визуальной инспекции PDF. Реальный chooser/выбор приложения и ручная экранная подсветка — приёмка владельцем.

PDF и UI имеют разные native backends (Pdf Canvas и Compose); общий snapshot/formatters, зафиксированные tokens/структура. Их нельзя объявлять пиксельно проверенными без визуальной инспекции. Производительность очень больших рабочих каталогов/реальные сторонние PDF viewers проверяются отдельно; векторный масштаб сохраняет все строки.

## Локальная проверка

13JVM/0fail/error/skip: ReportExportLayoutTest2, ReportTrendsTest5, ReportAnalyticsTest6. compileDebugKotlin/compileDebugAndroidTestKotlin/testDebugUnitTest SUCCESS1m7s; assembleDebug/assembleDebugAndroidTest/13JVM SUCCESS1m11s. После исправления Android provider identity финальная assembleDebug SUCCESS46s; targeted testAPK build18s. preflight/diff passed.

API36 emulator-5554, прямой runner `com.mendelev.mpos.AnalyticsPdfTest`: финальные4tests OK0.524s. Реальные PDF/Renderer: обе темы, текст/цвет/непустая отрисовка, все500товаров, нижний блок, pageCount1 иheight14400 для длинного отчёта; read-only ClipData grant, фактическое openInputStream content URI; отказ URI для другого cache path; cancellation на measure/draw stages без partial; l/kg2знака при точном исходном BigDecimal.

Первый Android-прогон обнаружил реальную SecurityException: две записи одного androidx FileProvider были связаны с updater authority. Исправлено отдельным manifest-классом AnalyticsFileProvider для reports; updater authority/roots не менялись. После нового APK и повтора все4теста прошли. Ошибка не подавлялась.

Fixture PNG из реального PDF осмотрены в светлой и тёмной теме: поля/сводка/графики/правые модули/полные таблицы без наложений в проверенном примере. Файлы /private/tmp/mpos-report-pdf-light.png и mpos-report-pdf-dark.png; это условные тестовые данные, не клиентские. Это visual PDF proof для fixture, не pixel-identity доказательство всех экранных состояний или Compose UI test.

Логи: /private/tmp/mpos-report-pdf-checks.log, mpos-report-pdf-final.log, mpos-report-pdf-provider.log, mpos-report-pdf-android-final.log. Реальный Android chooser/получающее приложение и ручная подсветка периода — pending владельцем. Полный lint/full suites не запускались для этого инкремента; адресные данные/PDF tests выполнены. Без commit/push/release.
