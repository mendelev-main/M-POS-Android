# 109.09 — рабочая зона из нативных моделей

Состояние: в работе. 115/129 задач, внутри 109 — 8/20. Каталог проверен;
вся 109.09 не закрыта. Физическая приёмка отложена до 110.

## Выполненный каталог

MPosWorkspaceReadRepository читает authoritative products/layout/posNavigation,
root/recovery и parked в Room transaction. MPosWorkspaceReadModel строит плитки,
доступность, строки корзины, скидки/итоги и типизированную таблицу действий.
Default native-workspace.js читает только геометрию оболочки; имена, цены,
строки и команды не извлекаются из HTML. Сохранены координаты, units,
рецепты/unlimited, strict IDs, render/live search и область папок.
Ingredient lookup строится один раз; availability раскрывает только видимые
composite товары и кешируется внутри модели. Полный catalogue publication
сохраняет прежний обход и clamp; POS показывает отрицательные simple остатки
как раньше. Время/нагрузка на физическом планшете ещё не измерены.

Main **5b94b88**, [Actions 37787596243](https://github.com/mendelev-main/M-POS-Android/actions/runs/37787596243):
JS/Kotlin tests, lint и подписанная APK — успешно. Dispatch зарегистрирован в
основной очереди, вложенный async add ожидается даже при отсутствии return
у source addToCart. Отдельный legacy adapter доступен только явным флагом
MPosNativeWorkspaceReadModelsEnabled=false.

## Редактор строки корзины

MPosCartItemController владеет черновиком количества, комментария и скидки.
Данные строки читаются из сохранённого currentOrderSession; JS передаёт явный
список скидок и currency, но не DOM inputs. UI использует MPosNativeTheme,
Manrope, light/dark, scroll и 48dp controls. Native workspace остаётся за
формой, заблокированным. Отмена/Back не сохраняют данные.

MPosCartItemRepository сохраняет совместимый session в одной Room transaction:
проверяет navigation, recovery и SHA256 revision документа, повторно проверяет
остатки при изменении количества и обновляет projection через RecoveryStorage.
Другие поля заказа, клиент, WEB, kitchen marks, paymentDraft/paid parts и
неизвестные расширения сохраняются. Подтверждение возвращается после записи.
Никакой отправки, печати, списания или пересчёта оплаченных частей нет.

Сохранены reviewed правила: минимум 1, без округления количества до целого;
при одинаковом количестве stock preflight не вызывается; numeric string
считается изменением; комментарий обрезается по ECMAScript trim; IDs выбранной
скидки превращаются в strings, как dataset/input. При дублирующихся ключах
preflight проверяет все matching rows, сохраняется первая, как раньше.

Rollback: MPosNativeCartItemFormEnabled=false возвращает source форму.
Native ошибка не вызывает скрытого повторного сохранения. После изменения
импортированного/сохранённого session устаревшую форму нужно открыть заново.

Добавлены Room проверки CAS/duplicate, свежих остатков, импортированного заказа,
recovery и SQLite rollback shadow/projection; controller busy/cancel/theme/ID
checks и JS late-read/ack/cancel checks. Выполнение — GitHub Actions,
результат этой части pending. Локальные tests/lint/APK не запускались (§17).

## Остаётся внутри 109.09

- Нативное владение всем черновиком заказа и операции add/remove/changeQty.
- Формы модификаторов, ручной цены и параметров заказа.
- Полный редактор товара, recipe/modifier/photo и действующие права.
- Удаление активной зависимости этих операций от source handlers и Actions.

Read model корзины ещё принимает явный transient order/config snapshot;
это не полное native draft authority. WebView удаляется по 109.19.

## Проверки на планшете — pending

- Открыть строку заказа, изменить количество/комментарий/скидку, сохранить;
  закрыть приложение и проверить восстановление ровно одного изменения.
- Отменить форму и нажать Android Back: прежний заказ остаётся.
- Недостаток остатка не сохраняет ни количество, ни комментарий/скидку;
  исправление количества позволяет сохранить форму.
- Проверить WEB заказ и сохранённую часть split: связь, отметки кухни и paid
  parts сохраняются; автоматической печати/повторной оплаты нет.
- Импорт/перезапуск во время формы не применяют старый черновик к новому заказу.
- Light/dark, длинные названия, клавиатура, landscape и увеличенный системный
  шрифт; за формой видна native рабочая зона, доступна прокрутка.
