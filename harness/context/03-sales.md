# Контекст подготовки03a —2026-10-25

Владелец поручил перед новым этапом сливать готовый блок в main и выпускать релиз. PR1 merge87f57ae после checks40198146760; main release40198464669 success, stable0.1.41.1/code5101 опубликован; отдельный public channel upgrade40199534929 success:production API36,2601→5101,settings/receipt preserved. Develop fast-forward к87f57ae. Подготовка03a только docs, финансового кода нет.

mpos-phase/mpos-domain: pinned shifts.js currentShift/shiftTotals/cashDrawerBalance/open/close просмотрены предметно. Web openingCash наследует countedCash последней закрытой, первая0; close допускает разницу и сохраняет фактический counted. Single-owner boundary03a явно отделена от05employees/auth. Нет переноса Web credentials/внешних действий.

Пользователь подтвердил net line HALF_UP2 (0.05×3−10%=0.135→0.14), total=sum rounded lines. Первый refund scope полный чек; cross-shift наличная выдача учитывается в текущей смене один раз, продажа остаётся в исходной истории. ADR007/MONEY03/SHIFT03A. Скидки/order allocation/split/delivery/mixed refund ещё pending до применимого кода; не домысливать согласование.

АудитPHASE-02-REVIEW отделяет CI47JVM/47instrumentation/2scale/4seed от physical/OEM/process-kill/paired Web/screenreader200%pending. User tablet later сохраняется. Следующий runtime шаг —03a pure/domain→Room migration→sharedUI→tests/actual update; не начинать оплату до её gates.

## Реализация03a начата

Согласована текущим запросом. Pure shift/minor money +Room6(shifts/register), common UI «Смена», nav изcatalog/workspace. InstallGate shared mutex предотвращает параллельный write/installer; новыеwrites запрещены пока installer lease не снят. Single-owner и перенос previous counted/initial0 из pinned Web соблюдены; open/close с confirmed preview/revision, audit/result atomic. ScopeH01/H02 minimal, не S01–S05.41supplemental domain tests passed cached Kotlin2.1.20, actual Android CI pending; local wrapper blocked ~/.gradle lock/нет SDK. Новые3JVM/4Room/1UI; explicit update seed расширен4→5.


## 03a итог —2026-10-25

[run40206673038](https://github.com/mendelev-main/M-POS-Android/actions/runs/40206673038), final commit fc7cde5 (runtime d8185dc): checks121208976198/emulator36 121208976292 success.50JVM/0failures,6Python,lint/assembleDebug;52instrumentation passed, затем2explicit scale+5explicit update UI seed. Новые3JVM/4Room/1UI покрывают exact minor/overflow/owner/stale/clock/carry/difference, конкурентные open и close/один победитель, replay/fingerprint/Room reopen/unique active slot,4write rollback boundaries+настоящий SQLite update refusal, installation wait/queued cancellation/wrong-owner lease release, migration5→6, invalid counted/recreate/close140/next carry140. Прежние migration1–4 теперь проходят до6; committed schema6 byte-identical final CI export.

External updater подтвердил CATALOG/WORKSPACE/PARKED/HOME/SHIFT UPDATE PASSED и UPDATE E2E PASSED:actual APK1→2,stock8/currentqty5total15/parked deliverylabeltotal5/homeproductslot8/active shiftId и opening140 BYN сохранены. Signing/publish/production-upgrade skipped на develop; stable0.1.41.1/main не менялись.

shift-closed.png1600×1125 после Compose captureToImage просмотрен: закрытая смена,expected0/counted140/difference140/carry140 и общая Primary Open. Updated-shift прошлого runtime-эквивалентного run40205795493 просмотрен: тот же active ID/opening140 после APK replacement. Final portrait150% workspace screenshot просмотрен: новая navigation «Смена» помещается, total/footer доступны. Наблюдаемые состояния без перекрытий/system overlay; полный paired Web/physical/shift font200% не объявляются device-verified.

Local41supplemental domain tests cached Kotlin2.1.20/JDK17 и442pinned source regressions passed;6Python/preflight/diff passed. Local android blocked до wrapper sandbox ~/.gradle lock/SDK отсутствует, actual pinned Kotlin2.2.21/Android проверены CI. История ошибок40204979930(JUnit return type),40205417469(optional schema indices),40205795493(success, compositor screenshot stale) сохранена выше; gates не ослаблялись.

03a H01/H02 minimal ci-verified в scopeSHIFT-03A. Нет sale/refund/deposit/withdrawal/employee auth/эквайринга;S01–S05 и full05 остаются planned. Следующий шаг перед03b — релиз готового03a через PRdevelop→main, затем утверждение оставшихся discount/split/delivery/mixed-refund examples и реализация sale transaction/admission/stock snapshot/installation recovery. User tablet tests later сохранено.
