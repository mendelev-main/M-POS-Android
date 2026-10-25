# Контекст подготовки03a —2026-10-25

Владелец поручил перед новым этапом сливать готовый блок в main и выпускать релиз. PR1 merge87f57ae после checks40198146760; main release40198464669 success, stable0.1.41.1/code5101 опубликован; отдельный public channel upgrade40199534929 success:production API36,2601→5101,settings/receipt preserved. Develop fast-forward к87f57ae. Подготовка03a только docs, финансового кода нет.

mpos-phase/mpos-domain: pinned shifts.js currentShift/shiftTotals/cashDrawerBalance/open/close просмотрены предметно. Web openingCash наследует countedCash последней закрытой, первая0; close допускает разницу и сохраняет фактический counted. Single-owner boundary03a явно отделена от05employees/auth. Нет переноса Web credentials/внешних действий.

Пользователь подтвердил net line HALF_UP2 (0.05×3−10%=0.135→0.14), total=sum rounded lines. Первый refund scope полный чек; cross-shift наличная выдача учитывается в текущей смене один раз, продажа остаётся в исходной истории. ADR007/MONEY03/SHIFT03A. Скидки/order allocation/split/delivery/mixed refund ещё pending до применимого кода; не домысливать согласование.

АудитPHASE-02-REVIEW отделяет CI47JVM/47instrumentation/2scale/4seed от physical/OEM/process-kill/paired Web/screenreader200%pending. User tablet later сохраняется. Следующий runtime шаг —03a pure/domain→Room migration→sharedUI→tests/actual update; не начинать оплату до её gates.

## Реализация03a начата

Согласована текущим запросом. Pure shift/minor money +Room6(shifts/register), common UI «Смена», nav изcatalog/workspace. InstallGate shared mutex предотвращает параллельный write/installer; новыеwrites запрещены пока installer lease не снят. Single-owner и перенос previous counted/initial0 из pinned Web соблюдены; open/close с confirmed preview/revision, audit/result atomic. ScopeH01/H02 minimal, не S01–S05.41supplemental domain tests passed cached Kotlin2.1.20, actual Android CI pending; local wrapper blocked ~/.gradle lock/нет SDK. Новые3JVM/4Room/1UI; explicit update seed расширен4→5.
