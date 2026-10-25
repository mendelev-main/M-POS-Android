# Единый дизайн M-POS

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
