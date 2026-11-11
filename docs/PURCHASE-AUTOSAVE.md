# Заказы: автоматический рабочий ввод —10.11.2026

Владелец сообщил: выбранный поставщик блокировал верхние вкладки до формирования/ручного сохранения/удаления. Потребовал свободную навигацию, автосохранение и отсутствие отсутствующего в Web меню «⋯» формы. Это прямое уточнение заменяет прежнюю native политику явного Save/Discard для нового заказа.

## Контракт

- PA01 implemented locally: dirty нового заказа не блокирует top navigation или Back. Обычный переход не открывает «Закрыть без сохранения?». Блокировки критического forming/unknown-result и открытой modal сохраняются.
- PA02 implemented locally: supplier/quantity/unit/pack/content изменения автоматически ставятся в последовательную очередь ViewModel. Saving не является pending и не отключает дальнейший ввод/вкладки. Каждая завершённая запись обновляет только persisted draft/revision, а не более новый ввод. RAM-сессия переживает переходы; Room хранит подтверждённый ввод для нового процесса.
- PA03 implemented locally: Working action сохраняет ограниченный по размеру промежуточный текст, включая незавершённые/ещё невалидные экономические поля. В PurchaseDraftCodec version2 обязателен working:true; version1 продолжает декодироваться со строгой прежней проверкой. Shape IDs/names/units/revisions/limits проверяются, готовность quantity/pack проверяется отдельно перед формированием. Ключ purchase-draft тот же, Room schema не меняется. Native restore читает обе версии тем же codec, raw поля не становятся складскими фактами.
- PA04 implemented locally: Working сериализован mutex с Save/Place, имеет отдельный fingerprint и прежние CAS/result/transaction guards. Потеря ACK разрешается исходным commandId до следующей записи; in-flight старый рабочий ввод не восстанавливается после Place. Текущий raw form сохраняется при ошибке, контекстный Retry виден только при реальном отказе, без обычных success banners/spinners.
- PA05 implemented locally: форма без меню Save/Revalidate/Clear, footer только «Сформировать заказ». Ссылки перед формированием актуализируются автоматически, количества/упаковки сохраняются. При очистке всех количеств можно сбросить supplier selection, это тоже автоматически сохраняется. Клавиатура320dp, контекст товара, output64/font28/right, source Clear/Done и Cancel в editor. Не выбранные строки не показывают лишнюю подсказку количества.
- PA06 local checks:16 JVM passed (включая4 настоящих ViewModel/codec concurrency fixtures), app/Android tests compile и lint0errors. Room reopen/UI tab navigation tests добавлены, но на устройстве не выполнялись. Owner acceptance pending.

## Источник и границы

Web05db2b7 purchase-orders.js/pos.html: независимое переключение вкладок, RAM purchaseOrderCart/supplier, supplier guard только при наличии заказанных строк; у новой формы нет меню native drafts. Durable automatic Room persistence — усиление по прямому запросу владельца, не утверждение, что Web сам всегда пишет каждую правку в durable storage. Не переносим raw input в проведённый заказ без строгой проверки. Stock/cost изменяются только существующими отдельными операциями.

Старые command-protocol ViewModel tests используют явный manual fixture mode, чтобы проверять прежние Save/retry/unknown-result API без гонки с новым background writer. Runtime factory использует autoSaveEnabled=true по умолчанию; новая PurchaseAutosaveTest исполняет именно этот режим, с реальным ViewModel и fake persistence/slow write/lost ACK. Добавлена test-only kotlinx-coroutines-test1.10.2, production dependencies не менялись.

## Сверка владельцем

Новый заказ → supplier → quantity/pack → вкладка Чеки/Рабочая зона → вернуться: поля сохранены, меню «⋯» новой формы отсутствует. Повторить с pack0/незавершённым числом: переход разрешён, создание отклоняется до исправления. После подтверждённого автосохранения закрыть процесс/открыть: поля восстановлены. Сформировать один заказ и убедиться, что незавершённый прежний ввод не появился вновь.
