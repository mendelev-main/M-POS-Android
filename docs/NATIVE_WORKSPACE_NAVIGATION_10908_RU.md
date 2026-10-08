# Граница этапа 109.08

## Что входит в этап

| Участок | Production путь | Проверка |
|---|---|---|
| Разделы и запрос поиска | MPosWorkspaceNavigationOwner, StateFlow, revision; fixed header / setTab / onSearch | Owner/header/search parity и stale tests |
| Категория и папка | Типизированная native команда; актуальная папка проверяется по Room, proposal/accept/discard | Repository/route ownership tests |
| Кнопка смены | Room shifts, digest CAS; default открытие native формы | Shift header/open-form tests; Actions 37773405619 |
| Раскладка root | MPosLayoutController + MPosLayoutRepository, read/commit через storage FIFO | Добавление, удаление, перемещение, collision, 20 плиток, metadata |
| Редактор категории | Native формы имени/перемещения/удаления, native drag/reorder и folder parent | Папки/create/rename/move/reorder/delete, товары остаются неизменными |
| Android Back | MPosBackStateOwner + explicit lifecycle notifications; выбор действия Kotlin | Все приоритеты, replacement/revision, native modal, stale/background |

Editor read model собирается по products/layout/posNavigation из Room без HTML
extraction. Коммит проверяет навигацию и digest всех трёх документов в одной
транзакции; caller не поставляет авторитетный список товаров или раскладку.
Сохраняется полный совместимый layout с categoryOrder/colors/symbols/online flags
и другими metadata; используются существующие native parity алгоритмы.
Источник товаров не изменяется редактором, финансовые документы не записываются.

JS acknowledgement только проецирует уже сохранённый результат. Структурное
сравнение не зависит от порядка ключей JSON. После смены данных/импорта старый
результат не затирает новую проекцию; приложение предлагает перезапуск. При
отклонённом CAS или failed persistence кнопка обновления запрашивает новую модель,
автоматической повторной записи нет. Busy блокирует повторы и отмену записи.

Папка редактора и его диалоги принадлежат native controller. Lifecycle уведомляет
Back и блокирует Web фон без скрытой формы. Закрытие папки возвращает редактор в
категорию и очищает совместимый descriptor. Add dialog остаётся открыт после
добавления, как в исходном POS. ListView переиспользует строки списка товаров.

Back получает Boolean flags из lifecycle переходов, не из DOM presence. Приоритет:
pending import, modal (включая native-only открытие смены/редактор), warehouse,
receiving, background. Новая форма/импорт меняет revision даже при одинаковых
Boolean flags. Поздний callback не закрывает новый экран; background требует
актуального acknowledgement. Reload сбрасывает native owner и старые callbacks.

## Оставшаяся оболочка и следующий этап

В reviewed POS нет видимого поля поиска. Сохраняем прежний onSearch API, scope и
coercion; Kotlin владеет запросом и live решением. Новое поле ради закрытия этапа
не добавляется. Render-time фильтрация списка, DOM projection, цены/остатки плиток,
HTML extraction каталога/корзины и их source handlers относятся к **109.09**.

Geometry native header/workspace/editor пока берётся из bounds оболочки. Это
презентационная зависимость: кнопки навигации передают типизированную команду,
не ищут HTML onclick target и не вызывают click скрытого элемента. Узкая topbar
сохраняет source scroller; её handlers используют native navigation owner.
Окончательный root layout и удаление WebView идут по утверждённым 109.19–109.20.

Legacy редактор сохраняется как explicit rollback:
`MPosNativeLayoutUiEnabled=false`. System Back rollback:
`MPosNativeSystemBackEnabled=false`. Native navigation/route/header flags из
предыдущих участков сохранены. Legacy DOM capture используется только без
подключённого lifecycle owner/при rollback, default route stale guard использует
lifecycle stamp. Это ещё не полный уход от WebView и не физическая приёмка.

## Верификация

Новые проверки: MPosLayoutRepositoryTest, MPosLayoutControllerTest,
MPosSystemBackTest, native-layout-ui.test.cjs, native-overlay-lifecycle.test.cjs.
Существующие reviewed fixtures navigation/workspace routes/search и baseline hash
не изменены. Новые adapters зарегистрированы в source sync и parity restoration.

Проверки выполняет GitHub Actions; локальные JS/JVM/lint/APK не запускались по
AGENTS §17. Первый run 37780826205 остановился на ошибке компиляции нового теста;
скобка исправлена в e867895. Actions **37781537606 (e867895)**: JS/Kotlin/lint и APK success.
Actions **37781952323 (1e27c5f)**: storage-failure/folder validation tests success;
APK ещё выполняется. Итоговая проверка дополненных search/strict ID cases pending.
Физические сценарии находятся в NATIVE_TABLET_ACCEPTANCE.md, pending в этапе 110.
