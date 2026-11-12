# Единый дизайн M-POS

12.11.2026: MposCountBadge — единый счётчик по Web .park-badge: radius100dp, padding1×8dp, font12.5sp/700, line18sp, white ink, ширина по цифрам без фиксированного круга. Parked light#0E8F6F/dark#31B98D, Events light#E0483E/dark#FF6B61. Цвета не берутся из feature-local MaterialTheme: событийный индикатор одинаков во всех экранах, включая MposSupplyTheme. При count<=0 скрыт.

12.11.2026 SF11–SF12: поля приёмки одинаковой ширины в общей5-колоночной сетке, все44dp, подписи16sp line-height. Авто-сумма теперь из текущей себестоимости карточки; жёлтый цвет снимается при изменении суммы, не от фокуса. В новом заказе касание поля включает строку с qty1, положительное количество сохраняется; включённые поля зелёные, qty0/очистка снимают выбор. Numeric focus выделяет значение для замены.

12.11.2026 SF09–SF10: контур активной folder-tab рассчитывается из общей с Row геометрии (28dp/20dp/три равные доли), без boundsInParent и отложенного замера. Иконки18dp у всех вкладок. В редакторе поля110/130/110/120dp начинаются после1 доли имени; блок полей2.7 доли. Приёмка с тем же левым выравниванием1:3 и компактным footer после товаров. Глобального пункта Приёмка больше нет; внутренний route подсвечивает Поставки.

12.11.2026 уточнение: вкладки MposFolderPanel теперь равной ширины и заполняют строку; боковые28dp/промежутки20dp/вогнутые плечи10dp. MposSupplyTheme — локальная палитра из утверждённого макета (dark background191C24/surface242833, lightF3F4F3/FFFFFF), не меняет остальные экраны. MposSupplyField44/radius10/gap4/label12/input14, авто-сумма жёлтая до focus. MposSupplyAction сохраняет shared POS geometry и мягкий warning fill как в макете; обе кнопки footer44. SF05–SF08: SUPPLIES-FOLDER-UI.md.

12.11.2026: утверждённый shared variant MposFolderPanel — синие Поставки слева, зелёный Новый заказ и жёлтая История справа; единый контур панели/активной закладки, height56/radius18/плечи16, цветные плашки10dp, обе темы. Контракт и границы: [SUPPLIES-FOLDER-UI.md](SUPPLIES-FOLDER-UI.md).

Актуальный запрос точной визуальной сверки от 2026-11-07: [атлас окон и план V01–V07](VISUAL-PARITY-PLAN.md). До парных скриншотов размеры/token specs и наличие shared компонентов не доказывают идентичность Web.

Требование пользователя от 2026-10-24: **внешний вид берём из старого M-POS-Android-Web; общие элементы одинаковы во всех окнах**. Это обязательный стандарт native реализации, не новый визуальный стиль. В01a реализованы базовые shared theme/Primary/Secondary/Danger/Close/Dialog/Field/Notice и debug gallery. Это частичное выполнение стандарта; полный visual parity/variants/font-scale/device приёмка ещё pending.

## Источник и степень точности

Зафиксирован Web commit `1682c88334e6fb13586ce4b530157c59ca062e43`. Главный источник — `app/src/main/assets/pos/pos.html`: palette/font variables, `.btn`, `.field`, `.modal`, `.pe-*`, `.parked-modal-close`; также просмотрены product-editor и Android admin styles. Таблица источников и SHA — [UI-REFERENCE.md](UI-REFERENCE.md), машиночитаемая спецификация — [config/ui-design.json](../config/ui-design.json). JSON пока не используется приложением.

Переносится визуальный язык: Manrope, цвета, плотность, компоновка редактора/корзины/окон, смысл и иерархия кнопок. CSS px являются исходными значениями; dp/sp ниже — native mapping, которое проверяется на одинаковой доступной ширине/масштабе. Pixel-perfect без сравнения screenshots не заявляется. Системные Android picker/installer/permission экраны сохраняют платформенный вид.

## Цвета

| Token | Светлая | Тёмная | Назначение |
|---|---|---|---|
| background | #F5F4F0 | #171A21 | фон приложения |
| surface | #FFFFFF | #222631 | окно/карточка |
| ink | #1B1F2A | #F4F6FA | основной текст |
| muted | #767C8C | #A9B0BF | вторичные подписи |
| accent | #0E8F6F | #31B98D | основное действие/выбор |
| accentInk | #FFFFFF | #07140F | текст основного действия |
| accentSoft | #E4F3EE | #173B31 | выбранный элемент/мягкий акцент |
| navy | #22273A | #10131A | карта/тёмный акцент |
| danger | #E0483E | #FF6B61 | удаление/ошибка |
| dangerSoft | #FBE7E5 | #432522 | мягкое опасное действие |
| border | #E7E4DD | #353B49 | контуры/разделители |

success/warning/info и их soft/border пары сохраняются из JSON источника. Статус не обозначается только цветом: текст/иконка обязательны. Dynamic color от обоев не подменяет фирменную палитру. Light/dark используют одну структуру и semantic roles; при реализации проверяются контраст/disabled состояния, необходимые доступные поправки фиксируются отдельно, а не маскируются под точную копию Web.

## Шрифт и типографика

Manrope из `Web/fonts/Manrope-Variable.ttf`, веса200–800; при включении в APK переносится OFL license. До переноса шрифта нельзя считать системный Roboto эквивалентом. Табличные цифры используются для цен/итогов там, где поддерживаются метриками шрифта; отдельный шрифт сумм не вводится случайно.

| Role | Native baseline | Источник |
|---|---|---|
| screenTitle |22sp/800 | product editor heading |
| dialogTitle |18sp/800 | `.modal-title`; большой заголовок22sp — утверждённый variant |
| sectionTitle |18sp/700 | editor card heading |
| body / input |15sp/400; editor input16sp | `.field`, product editor |
| buttonLabel |14.5sp/700 | `.btn` |
| fieldLabel |12.5sp/700 | `.field label` |
| supportingText |12sp/400–600 | editor notes |
| majorAmount |30sp/800 | price summary |

Font scale применяется системой; высота строк/кнопок растёт при необходимости. Сумма/ошибка/подтверждение не обрезаются ради фиксированной высоты. Размеры/начертания сведены к named roles, индивидуальные `fontSize` каждого экрана не допускаются без component variant.

## Геометрия и интервалы

| Элемент | Стандарт |
|---|---|
| Обычное поле | radius10dp, minHeight48dp, border1.5dp, padding12×14dp |
| Кнопка | radius16dp, minHeight48dp, horizontal padding12–16dp |
| Кнопка закрытия | shape12dp; visual44×44dp внутри общей touch area минимум48×48dp; icon22dp |
| Обычная карточка | radius16dp, border1dp |
| Большая карточка редактора | variant radius20dp |
| Диалог | radius22dp, padding28dp, maxWidth460dp, внешний gutter20dp |
| Большие действия оплаты | minHeight52dp, остальные свойства semantic button |
| Переключатель | visual48×28dp/thumb22dp из Web; touch area минимум48dp |

Spacing scale:4/6/8/10/12/14/16/18/20/24/28/32dp по исходным паттернам. Для screen layout основные интервалы16/24/28, для соседних controls8/10/12. Не выбирать27dp/17dp на каждом экране без причины. Pills/badges могут быть capsule; это отдельная роль, обычные action buttons не превращаются в случайные pills.

Shadow Web — два мягких слоя; native elevation уточняется screenshot сравнением, численного равенства CSS blur и Compose elevation нет. Border/тон surface важнее добавления тяжёлой тени ко всем карточкам.

## Окна и редакторы

Общий `MposDialog` содержит header/body/actions. Header: заголовок слева, единый `MposCloseButton` справа. Close — один вектор X22dp со stroke2dp и скруглёнными концами, background token background, tint muted, shape12dp. Никаких emoji/Unicode крестиков, разных размеров, красных close в одном модуле и серых в другом. Tooltip/contentDescription «Закрыть» одинаковы. Новый единый close основан на Web `.parked-modal-close`; он унифицирует другие разнородные legacy окна по запросу пользователя.

Dialog variants: Compact до460dp; Wide для списков/настроек с maxWidth820dp; Editor/Document — отдельный большой surface/full-screen route по Web editor/supply layout, не растянутый AlertDialog. Конкретный variant выбирается общим API, не локальным copy-paste. Gutter адаптируется к маленькой ширине, safe insets и клавиатуре; body скроллится, заголовок/основное действие остаются доступными.

Для редактора товара сохраняются Web паттерны: header с «Назад» слева и «Сохранить» справа, секции/навигация, основной контент и summary при достаточной ширине. Back — навигация, close — закрытие окна; иконки различаются по смыслу, каждый тип повторяем во всех соответствующих местах. На узкой ширине секции/summary перестраиваются без потери draft.

Закрытие: X/Back/outside tap не сбрасывают dirty editor молча — общий discard guard. Для destructive confirmation outside tap только отменяет подтверждение, не выполняет действие. Во время commit dialog нельзя закрыть так, чтобы выдать неизвестный результат за rollback; правило задаёт командное состояние. Read-only информационное окно допускает свободное закрытие.

## Единые кнопки

| Variant | Вид | Примеры |
|---|---|---|
| Primary | accent/accentInk, radius16 | Сохранить, Добавить, Подтвердить |
| Secondary | background/ink | Отмена, Назад в action row |
| Outline | transparent, border1.5/ink | второстепенное действие с контуром |
| Danger | danger, доступный contrast text | Удалить в подтверждении |
| DangerOutline | прозрачный/soft контур danger | вход в опасное действие |
| PaymentCash / PaymentCard | accent / navy | явные варианты оплаты, не общий шаблон всех кнопок |
| Close | отдельный icon-only компонент | всегда X справа в dialog header |
| Back | единый arrow+label pattern | назад из полноценного редактора |

В паре: Secondary слева, Primary справа, gap10dp. Destructive confirm: «Отмена» слева, «Удалить» справа. Один главный Primary на группу действий; label описывает результат, а не везде «ОК». Кнопки общей строки одинаковой базовой высоты; длинный текст переносится/row перестраивается по ширине. Icon+text использует одну размерную шкалу; icons не меняются между экранами.

Состояния default/pressed/focused/disabled/loading централизованы. Web pressed scale0.97/disabled opacity0.4 — source evidence; native pressed/ripple и читаемый disabled фиксируются shared implementation после сравнения. Loading сохраняет ширину/label meaning, показывает progress, повтор action не запускает. Цвет/радиус не задаётся в feature для исправления одного состояния.

## Поля, сообщения, списки

`MposTextField` имеет label, input, supporting/error text; фокус accent, ошибка danger+текст. Единицы/валюта показываются consistently, placeholder не заменяет label. Числовая клавиатура/parse contract отдельны от визуального вида. Stock input расположен в карточке и редактируется без обязательного поля причины, см. [CATALOG-WORKSPACE-SPEC.md](CATALOG-WORKSPACE-SPEC.md).

Shared SearchField/CurrencyField/QuantityField/SelectField используют один базовый control. Toggle/checkbox/selection используют accentSoft/accent и ясное выбранное состояние. Общие EmptyState/ErrorState/LoadingState/Notice/ConfirmDialog одинаковы по сообщениям, отступам и кнопке повтора. Toast не служит единственным подтверждением финансового результата/ошибки поля.

## Реализация и контроль единства

Design system — часть M01: `core/ui/theme` (colors/type/shape/spacing), `core/ui/components`, `core/ui/preview`. Не новый22-й модуль и не отдельный Gradle project. Compose Material3 используется как primitive с явной M-POS theme; дефолтная Material геометрия/динамическая палитра не заменяет этот стандарт.

В01a сначала реализовать tokens/theme+Primary/Secondary/Close/Dialog/TextField/Notice и component gallery, затем экран каталога. Не копировать JSX/CSS/WebView. Все feature screens используют common components. Повторяющийся новый элемент сначала добавляется общим variant, затем применяется во всех местах; исключение оформляется решением с примером/причиной. Updater получает ту же theme при общей UI интеграции.

ПриёмкаDS01: источники/Manrope/palette; DS02: все dialog close одинаковы; DS03: button/input states light/dark; DS04: keyboard/font scale/48dp target; DS05: paired screenshots Web/native на одинаковых сценариях; DS06: no lost dirty form приX/Back; DS07: component gallery показывает все variants. Статус planned; сравнение скриншотов и physical tablet pending.


## Рабочая зона02a — shared variants

MposAppBar переносит существующий navy/Manrope header каталога в общий компонент; рабочая зона использует те же navigation actions. MposWorkspaceTile: radius18dp/min-height144dp/gap12dp из pos.html pcard/product-grid; фото — общий sampled private ProductPhoto. MposDialog wide=true — только ширина до900dp для папки, те же header/Close, radius22dp, padding28dp и кнопки; обычные диалоги не меняют ширину. Раскладка/корзина: grid слева + cart340dp при ширине≥840dp, при меньшей ширине cart снизу; физическая orientation/font-scale приёмка позже. Full fixed-slot/drag parity не заявляется, контракт WORKSPACE-02A.md.

## Рабочая зона02d — ширина папки

Shared MposDialog maxWidth — ограничение ширины без отдельного оформления Close/header/buttons/radius/padding. Для папок280/448/660/872dp по1–4колонкам из pinned pos.html611; narrow viewport уменьшает колонки по доступной ширине. wide900dp остаётся общим вариантом для других сценариев. [Контракт](WORKSPACE-02D.md).

## Рабочая зона02e — пустое место и цель перетаскивания

Пустое место: фон background, общая граница outlineVariant/1dp, shapes.large, высота144dp как минимальная плитка. Стандартный Material surfaceVariant не используется, чтобы не вносить фиолетовую заливку. Цель жеста выделяется primary/2dp по общей shape. Home product tile использует общий ProductPhoto и принятую подсказку «Ввести цену» для manual0. Все действия Add/Move/Remove/Reset используют существующие MposButton/Field/Dialog/Close. [Контракт](WORKSPACE-02E.md).


## Адаптация рабочей зоны02f

Текущие правила заменяют исходный порог840dp из02a: cart340×fontScale dp (scale1–2), рядом с grid при наличии ещё460×scale dp +20dp. При достаточной высоте cart снизу300×scale dp; иначе общие кнопки «Товары / Заказ» переключают полную панель. Footer и основные действия должны оставаться доступны при крупном шрифте; сохраняются общий Manrope, цвета, размеры touch targets и единые Close/buttons. Контракт и границы emulator/physical — [WORKSPACE-02F](WORKSPACE-02F.md).

## Верхняя панель

Обязательный shared variant и NAV01–NAV03 — [TOP-NAVIGATION.md](TOP-NAVIGATION.md): точный порядок renderTopbar pinned Web,68/62dp, capsule tab14/12sp, настройки44dp, события/смена. Не заменять её разными наборами TextButton на экранах. Реальные маршруты подключаются по готовности модулей; состояния команд задают блокировку общей навигации.

## Рабочая зона: актуальная геометрия

[WORKSPACE-VISUAL](WORKSPACE-VISUAL.md) заменяет исторические340dp корзины/18dp плитки:380dp/16dp, cart22dp, icon44dp/radius12dp, source toolbar и компактные строки. PosAction/PosIconButton — документированные варианты shared POS controls; диалоги используют общий MposDialog.

2026-10-29 settings variant: shared MposButton.Outline implemented (surface/ink, border1.5dp, radius16/min48) и MposInfoButton (touch48/vector circle-i24/contentDescription). Настройки используют grid1.2/.8>700dp, gap16/cards16/padding20 и общий Wide dialog до820dp; контракт SETTINGS-VISUAL.md. Legacy settings pill buttons адаптированы к общему native button стандарту, а не объявлены pixel-perfect.


05c.2 shared variant: MposAnalyticsCard(metric=true) переносит analytics-kpi radius18/padding16/border1, panel radius20/padding18/border1; остальные controls прежние. Контракт/адаптивность/неподключённые показатели — [ANALYTICS-UI-05C2](ANALYTICS-UI-05C2.md).

2026-11-07, V04.1: существующий PosAction.footer — именованный Web cart footer variant: font14,5sp/700, lineHeight19sp, vertical padding14dp, высота47dp при font100%; park radius12dp, pay radius16dp. Это measured exception к обычному min48dp, по новому требованию точного Web. При увеличении шрифта содержимое растёт; фиксированная высота не навязывается. Ширина park —34% полной строки до gap8.

2026-11-07, V03.1: MposWorkspaceTile теперь имеет именованный editing variant из `.pcard.edit-tile`; compact следует `.folder-product-card`. Символы получают alpha всего слоя, чтобы цветные emoji также соответствовали opacity Web. Padding включает ширину CSS border. Shadows/glow и рисунок dashes требуют парной проверки, поэтому native equality ещё не подтверждена.

## Верхняя системная строка Android

По запросу владельца2026-11-07 в рабочих экранах и диалогах скрыта status bar с часами/батареей/уведомлениями. Единая policy `MposSystemBars.kt` применяется к MainActivity и отдельным Dialog windows; при возврате фокуса восстанавливается. Safe drawing для вырезов, клавиатуры и нижней навигации сохраняется. Политика не распространяется на внешние системные окна (например установщик APK). Системный жест временно показывает скрытую панель по [штатному Android API](https://developer.android.com/develop/ui/views/layout/immersive); это не kiosk-mode.


### LO02 — профиль клиента Web05db2b7

Для customer-profile-modal допускается custom heading общего MposDialog: аватар52, имя/телефон, Telegram; без дублирующего заголовка и крестика, закрытие «Готово»/dismiss. Card border1/radius14/padding14; gift choice radius11. Нейтральная outlined кнопка удаления с danger text — variant source loyalty-remove-customer. Выбранная cart-customer-button44/radius12 использует primaryContainer/primary border+ink (как accent-soft/accent Web); подробности и статус приёмки — ONLINE-LOYALTY-PARITY.md.


10.11: PosControl.Receiving — Web receiving-resume light bg#ffe08a/ink#6b5200/border#e4be45; dark bg#695015/ink#ffe9aa/border#aa8434. Обе кнопки приёмки по заказу используют54dp/radius14, общие с PurchaseAction.


10.11: Web online-order-modal940 — допустимый вариант shared MposDialog с нулевыми contentPadding/spacing и собственными heading/body/footer inset20. Значения по умолчанию28/16 для остальных окон сохранены. Header eyebrow12/title18, type badge radius8, section radius16, fixed bottom actions; admin-only destructive action — MposButton Danger.


10.11: customer-picker variant560dp — heading44/title18/44, shared close и primary add icon, result avatar44/card radius14. Customer input variant из android-customer-input.js:56dp pill, border1.5, immutable country/suffix prefix, text20; новый клиент460dp. MposPosIcon получил optional tint (default unchanged), чтобы shared add на primary surface использовал onPrimary.

## Временные сообщения об успехе — уточнение10.11.2026

В товарах постоянные зелёные MposNotice для успешного save/delete/reorder заменены компактным MposTransientNotice: overlay сверху по центру (отступ80dp под шапкой), max-width400, radius16, padding18×12, shared primaryContainer, тонкая рамка и вежливый accessibility live region. Сообщение не занимает строку в потоке страницы и исчезает через2500ms.

TransientMessages(viewModelScope) отделён от validation errors: повторный успех, в том числе с тем же текстом, получает новый срок2500ms; старый timer отменяется. Таймер продолжает работать при уходе с экрана и не воспроизводит устаревший баннер при возврате. Ошибки остаются в форме; overlay скрыт при активной ошибке. Показ успеха только после успешной записи. Компонент пригоден для дальнейшего применения в других модулях; массовая замена всех сообщений приложения этим изменением не выполнялась.

2026-11-11: active admin indicator — общий shield22dp внутри44dp карточки/radius16, primaryContainer/primary и border2dp, семантика «Сессия администратора активна». Это индикатор runtime-сессии в topbar, не отдельный вход/роль. История чеков: статус возврата в header справа, compact sticker radius12/padding12×7, label11sp/date10sp; footer возвращённого чека только print. Выбор печати — shared MposDialog460dp с двумя52dp PosControl/radius16; body подтверждения возврата — общая карточка radius16/отступ18, одинаковые48dp кнопки.

2026-11-11 уточнение владельца: receipt header return sticker теперь одна строка «Возврат · dd.MM.yyyy HH:mm», radius100 как компактные метки истории; прежний двухстрочный radius12 вариант заменён. Панели истории/чека имеют верхние углы22dp и прямой низ, bottom gutter0; даты с годом. Network status strips заполняют ширину карточки. Блок Управление использует общие карточки22/12, plus44 и shared поля/кнопки модального окна.

11.11.2026: SettingsDirectoryRow/settingsRowModifier — единая карточка записи внутри модуля (сотрудники, поставщики, скидки, доставки, шаблоны и список принтеров): radius16dp, контур1dp outline как у невыбранных чеков истории, background для отделения от поверхности модуля, gap8dp, padding14×12dp. Нажатие всей строки и права прежние.

11.11.2026: новый обязательный стандарт modal close по образцу Отложенных — MposCloseButton44×44/radius12, background/outline1/red cross22, без48dp IconButton-обёртки. Default и custom modal headers используют его; в note только X без footer «Закрыть». В отложенных X и delete центрируются в одной44dp action column с одинаковым inset. Прежние размеры/варианты закрытия в исторических записях ниже/выше заменены этим уточнением; busy/access guards неизменны.
