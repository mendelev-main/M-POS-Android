# Проверка аналитики — 13.11.2026

Реализация AN01–AN09 по [контракту и плану](ACCOUNTING-ANALYTICS.md). Изменения локальные, main; commit/push/release не выполнялись. Предшествующие незакоммиченные изменения сохранены.

## Результаты

| Проверка | Результат |
|---|---|
| testDebugUnitTest, весь JVM suite | 506 tests, 0 failures/errors/skipped |
| lintDebug | 0 errors, 89 warnings, 13 hints; warnings не подавлялись |
| assembleDebug | SUCCESS, APK собран |
| compileDebugAndroidTestKotlin / test APK | SUCCESS |
| Android16/API36, прямой MposTestRunner через ADB | OK, 26 tests |
| scripts/harness.py preflight | passed |
| scripts unittest discover | 36 tests, OK |
| git diff --check | passed |

Android: SalesReportRepositoryTest4, ReportViewModelTest3, SaleRepositoryTest6, RefundRepositoryTest8, StockDocumentTest3, WarehouseReportTest2. Это выбранные бизнес/Room регрессии, не весь instrumented suite и не UI-навигация. Базы тестов in-memory/отдельные fixture-файлы, рабочие оплаты/печать/Telegram не выполнялись.

Новые JVM: ReportAnalyticsTest6, ConsumptionReportsTest7, ReportFulfillmentCodecTest1. Контрольный набор пицца/сыр/полный возврат; mixed payment; delivery; default here/invalid type; legacy courier-adjusted profit и новый gross; исходный автор/средний чек; поиск/сортировки; nested/yield/modifier/untracked; точные thirds до итоговой границы; г/кг и мл/л; несовместимые измерения; циклы/нет metadata; отрицательный расход.

Repository дополнен проверками исторического delivery у возврата, gross, employee count, неизменных названий/расхода при изменении каталога; существующее сравнение всех таблиц до/после и после reopen прошло. ViewModel проверил stale request, error/retry, logout/relogin, неверные/будущие даты и SavedState без денежных данных.

## Команды и окружение

JDK17 `/opt/homebrew/opt/openjdk@17`, SDK `/Users/Aleksandr/Library/Android/sdk`, emulator-5554 API36. Для Gradle: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/private/tmp`, `--no-watch-fs -Pkotlin.compiler.execution.strategy=in-process --max-workers=2`.

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline …`
- Финальный набор `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest` завершил unit/lint/build, но Gradle connected gate не прошёл по инфраструктурным причинам ниже.
- `adb install -r …/outputs/apk/debug/app-debug.apk` и `adb install -r …/outputs/apk/androidTest/debug/app-debug-androidTest.apk` — Success.
- `adb shell am instrument -w -r -e class com.mendelev.mpos.SalesReportRepositoryTest,com.mendelev.mpos.ReportViewModelTest,com.mendelev.mpos.SaleRepositoryTest,com.mendelev.mpos.RefundRepositoryTest,com.mendelev.mpos.StockDocumentTest,com.mendelev.mpos.WarehouseReportTest com.mendelev.mpos.debug.test/com.mendelev.mpos.MposTestRunner` — OK(26).
- `python3 scripts/harness.py preflight`
- `python3 -m unittest discover -s scripts/tests -q`
- `git diff --check`

Локальные XML/HTML: `/Users/Aleksandr/Library/Caches/M-POS-Android/build/app/test-results/testDebugUnitTest/`, `…/reports/tests/testDebugUnitTest/`, `…/reports/lint-results-debug.html`. Raw run logs: `/private/tmp/mpos-analytics-final.log`, `mpos-analytics-online.log`, `mpos-analytics-instrumented.log`, `mpos-analytics-python.log` (временные).

## Ограничения и незакрытые проверки

1. Offline Gradle connected не нашёл UTP31.13.2 в кэше. После сетевого повтора UTP завершился host IOException «No such file or directory» до выполнения тестов. Поэтому Gradle connected gate **не объявляется пройденным**. Тот же APK/runner с теми же6 классами напрямую через ADB отработал успешно. Первый перекрытый прогон остановлен после запуска финальной проверки; его незавершённый лог не используется как evidence успеха.
2. SDK сообщил об ограниченной записи analytics.settings; для ~/.android предоставлен доступ. Это не ошибка доменной аналитики; предупреждения не скрыты.
3. UI/Compose tests не добавлялись по AGENTS.md. Визуальная приёмка планшета, узкого экрана/шрифтов и прокрутки — pending владельцем, чеклист в контракте.
4. Производительность на большой рабочей истории не профилирована; существующая загрузка всей истории и таблицы в памяти сохранены. Web-архив исключён и сопровождается предупреждением. PDF/Excel и backend не добавлены.
5. Миграций БД и изменений реальных кассовых/складских правил нет. Отсутствующий type читается как here без перезаписи исходного документа. Неизвестный type/неполные исторические данные блокируют недостоверный отчёт.
