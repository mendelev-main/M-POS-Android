# 11.11.2026 — внутренняя заметка по удержанию

Явная новая native возможность владельца, не обязательный перенос Web: удержание2секунды по товару рабочей зоны открывает read-only заметку из карточки (ProductDetails.note / импортированный internalNote). Сфера W/P: home product, category product, folder product. Категории и папки сами заметок не открывают. В edit mode прежний default long-press drag, новый2s callback выключен.

MposWorkspaceTile combinedClickable с локальным ViewConfiguration.longPressTimeoutMillis2000 только там, где есть note action и editing=false. Обычный тап по-прежнему добавляет/конфигурирует товар; распознанный long hold не вызывает click при отпускании. Прокрутка/отмена жеста отменяет удержание; disabled блокирует оба действия. Тексты/иконки категорий и hit keys прежние. Никаких новых денежных/складских команд.

После удержания открывается shared MposDialog max460dp, название товара, подпись «Внутренняя заметка», текст с переносами и прокруткой, стандартные close. Пусто → «Внутренняя заметка не добавлена.» Ошибка чтения отличается от пустой заметки, есть retry. Читаем по требованию на IO через существующий productDetails resolver: local ProductDetails → импортированная Web metadata → legacy source fallback. Все данные остаются локальными, в публикацию/чек/заказ не добавляются; схема/backup/цены не меняются. Родительская папка сохраняется под note dialog.

UI regression fixture: tap,1900ms без срабатывания/2100ms срабатывание1раз/нет add после hold, cancel, edit mode, многострочный текст и пустой случай. Fake reader не обращается к production/базе пользователя. Runtime/device acceptance pending у владельца, работа локально без push.
