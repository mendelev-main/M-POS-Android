# Нативный стиль M POS

Источник — действующий интерфейс POS, CSS tokens pos.html и локальный Manrope.
Задача — одинаковый визуальный язык Web/native, без нового бренда или новой логики.

| Роль | Светлая тема | Тёмная тема |
|---|---|---|
| Фон | #F5F4F0 | #171A21 |
| Поверхность | #FFFFFF | #222631 |
| Текст | #1B1F2A | #F4F6FA |
| Вторичный текст | #767C8C | #A9B0BF |
| Акцент | #0E8F6F | #31B98D |
| Текст на акценте | #FFFFFF | #07140F |
| Мягкий акцент | #E4F3EE | #173B31 |
| Граница | #E7E4DD | #353B49 |
| Ошибка | #E0483E | #FF6B61 |

Шрифт: assets/pos/Web/fonts/Manrope-Variable.ttf, уже поставляется с приложением
и лицензией OFL; новые сетевые загрузки во время работы POS не нужны. Native
Typeface кешируется. Размеры текста в sp, отступы в dp. Заголовок экрана 24–28,
секция 18–20, основной текст 15–16, вторичный 13–14. Денежный итог выделяется
весом/размером, а не десятком одинаково ярких кнопок.

Сетка отступов: 8/12/16/24. Карточки и окна: radius 22, поля/кнопки radius 10–12.
Минимальная высота интерактивного элемента 48dp. Общие примитивы — MPosNativeTheme.
Главная кнопка зелёная с контрастным текстом; вторичная — поверхность с границей;
опасные действия — мягкая красная поверхность. Без OS all-caps. Focus/ripple и
disabled state должны оставаться различимыми.

Экран смены: заголовок/статус, сводка, финансовые секции, действия с кассой,
отдельные карточки истории. Значения выровнены вправо, длинные подписи переносятся.
Открытие смены: выбор сотрудника как оформленное поле, пароль только для admin,
сумма переноса как отдельная сводка, ясная главная кнопка. Scroll для клавиатуры,
landscape и крупного шрифта. Не менять доступность действий или расчёт сумм.

Skills в проекте: `.agents/skills/frontend-design/SKILL.md` (upstream/pinned/license
рядом) и `.agents/skills/mpos-native-design/SKILL.md` (правила M POS). Они доступны
локальным агентам через AGENTS.md; это не глобальная установка плагина.

Превью реализации: [светлая и тёмная темы](design/071/README.md).

076: monetary preview refresh reuses existing POS DOM/classes/typography. Only
line/total/gift text nodes update after matching native quote; root, forms and
scroll identity remain. Physical light/dark/font-scale acceptance pending.


097: analytics loading/error uses existing Web card/analytics-title/center-note/btn-primary tokens and bundled Manrope in both themes. Successful rendering retains reviewed chart/KPI layout. Async replacement retains screen scroll and waits for date-input blur. This is a Web presentation adapter, not native analytics UI acceptance; full UI remains 107. Physical theme/font-scale checks pending.

100: native settings/network cards and settings/employee forms reuse MPosNativeTheme and offline Manrope. Compact actions wrap at large font scales; long forms scroll with a keyboard-height cap, short forms fit their content. Source cancel/back is represented once. Both palettes, primary/secondary/danger hierarchy and credential clearing are covered by native view tests. [Synthetic previews](design/100/README.md); tablet keyboard/landscape/font-scale acceptance is pending.

101: normal POS workspace uses native category/product/folder grid cards, stock/price hierarchy, compact cart rows and wrapping actions with shared light/dark/Manrope tokens. Wide tablets show catalogue/cart side by side; narrow layouts stack them with scrollable cart content so keyboard/large-font actions remain reachable. Root grid spans come from the original presentation. Layout editing and planned modals retain reviewed presentation. Synthetic native previews are distinct from tablet acceptance.

[Синтетические превью рабочего места 101](design/101/README.md).

102: expanded product/contextual forms use shared Manrope palettes, soft-accent selected navigation, labelled sections/recipe/modifier cards and right-aligned formatted monetary summary. Dynamic patches retain focus/cursor/drafts and scroll; actions wrap and forms scroll under the keyboard. Photo previews are sampled/cancellable off the UI thread and reused while their source matches. [Synthetic native previews](design/102/README.md) are not HONOR screenshots or physical acceptance. Reviewed configuration/auth runtime remains a compatibility boundary until 109.

103: native payment forms use shared light/dark Manrope, receipt panels with right-aligned formatted metrics, primary cash/secondary card choice, clear part status/disabled paid actions, accessible tender role-buttons and wrapping denominations/actions. Deferred native hardware Back preserves paid-split refusal. Existing cash/count/amount dialogs remain styled with shared primitives. [Synthetic previews](design/103/README.md) are not HONOR screenshots or physical acceptance.

104: receipt history uses shared native theme/Manrope, structured multiline selected rows and formatted right-aligned metrics. History/detail columns scroll independently and retain positions by page/receipt; narrow viewport stacks panels. Return is visibly dangerous, print secondary; confirmation/result use existing modal primitives. Synthetic palette previews are distinct from physical tablet acceptance.

105: native customer/parked/loyalty forms and administrator page use shared Manrope/palettes, stable live phone/search/checkbox/select fields, accessible source action labels and primary/danger hierarchy. Native page geometry updates preserve input identity/focus/cursor with keyboard resize; scoped overlays retain reviewed navigation decisions. [Synthetic previews](design/105/README.md); physical keyboard/theme/font-scale and long client/program lists pending.

106: warehouse, suppliers, purchase orders, receiving documents and inventory use shared MPosNativeTheme/Manrope cards and wrapping actions. Purchase keypad preserves a three-column numeric layout and explicit Done; Android date pickers keep ISO source values. Report tables have shared themed headers and recycled visible rows rather than one view per historical cell. Both theme previews are synthetic; tablet font scale, keyboard, touch/scroll and runtime performance remain pending. [Previews](design/106/README.md).

107: analytics uses shared Manrope/light/dark native date controls, wrapping presets, responsive KPI/chart cards and recycled labelled horizontal bars. Reviewed formatted values/relative widths are passed through; hidden cashier employee sums are omitted, including accessibility. DatePicker editing defers async report replacement until dismissal. [Synthetic previews](design/107/README.md); physical font/scroll/performance acceptance pending.

108: native floor map/table forms/bookings use shared Manrope and responsive independent map/list panels. Info blue and warning amber tokens match source hall CSS in both themes; selected tables keep scale/elevation, squares/rectangles/rotation and relative positions. Tight table padding keeps status legible, labelled native time/date pickers preserve source formats. [Synthetic previews](design/108/README.md); physical touch/font/keyboard/performance acceptance pending.

109.06: native shift invalidation reuses the existing loading text, Manrope/palettes and layout. No visual redesign; controls from the previous root revision are removed while loading and their callbacks cannot run. Physical foreground/scroll checks remain pending.


08.10.2026: тесты Telegram/WEB/печати используют существующее поле сообщения native settings формы для ожидания и результата. Layout, Manrope, palette и touch targets прежние; late result привязан к исходной форме, физическая оценка обеих тем pending.

109.07 employee confirmation uses MPosNativeTheme/Manrope, native light/dark palettes, a 52dp password input, primary confirmation and secondary cancellation. It scrolls with the keyboard/font scale; wrong credentials show an inline error. The password is excluded from view-state saving and never mirrored to the mounted settings form. Busy state locks confirmation/cancel during dispatched commit; a credential rejection before timeout clears input and restores controls. An unknown outcome disables resubmission and offers closing with a restart message. Synthetic-data Robolectric view checks passed; physical keyboard/font-scale acceptance remains pending.


109.07: подтверждение удаления каталога и разблокировка редактора используют
существующий MPosNativeTheme/Manrope, обе темы, прокрутку и нативное поле пароля
без state saving. Для администратора при удалении товара поле скрыто;
при изменении роли перед commit оно появляется после нативного отказа.
Существующие формы и их layout не менялись. Проверка на планшете pending.


109.08 workspace toolbar: native state supplies title and Back/layout/folder-close
labels. Shared MPosNativeTheme/Manrope, both palettes, 48dp secondary buttons and
horizontal scrolling remain; payment stays primary. Source HTML toolbar labels
are not extracted for these controls. Typed callbacks bind to view token;
busy/recovery disables controls. Physical tablet/font-scale checks pending.


109.08 integration correction: production lexical state activates shared native
workspace/settings surfaces. Shift cash Dialog keeps native themed summary behind
it, disabling background buttons; visible legacy HTML modals keep the compatibility
path. Manrope/palettes/touch targets unchanged. Tablet verification pending.

## Стабильное обновление рабочей зоны — 08.10.2026

[Подробности и проверка](NATIVE_WORKSPACE_SMOOTHNESS_RU.md): обновления остатков/количества/итогов и строк корзины сохраняют Views; действие/token обновляются без пересоздания сетки. Cash/card/secondary/outline передаются в shared theme; busy блокирует повторы без серого мигания рабочих кнопок. Новые тесты выполняет Actions, физическая плавность pending. Новых завершённых migration IDs нет: 114/129 (88,37%), 109 — 7/20.


## Рабочая зона — визуальный паритет iPad, 08.10.2026

См. [перенос исходной раскладки](NATIVE_WORKSPACE_IPAD_PARITY_RU.md). Изменения ограничены рабочей зоной; CI и физическое сравнение pending. Новых завершённых этапов нет: 114/129 (88,37%), осталось 15; 109 — 7/20.


109.08 native fixed topbar tabs: shared Manrope, source navy and white/muted-white
pill labels, selected fill rgba(255,255,255,.14), 48dp controls. Auxiliary events/
shift controls retain source presentation. Source outer horizontal scrolling is
preserved when fixed groups do not fit; resize restores native controls. Modals/
payment/editor own their upper layer. Physical font-scale/portrait checks pending.


109.08 shift header: shared Manrope/navy, white employee label, rounded muted-white
pill, reviewed green/red dot and 48dp targets. Native opening launches the existing
themed employee/carryover dialog without a hidden HTML form from this entry point.
Long labels can scroll within their group; source geometry/narrow root fallback
remain. Both themes and typed busy controls have native View tests; tablet pending.


109.08 default shift opening: all opening entry points use the same themed native
dialog without hidden HTML fields. Native workspace/shift background remains
mounted and disabled beneath it; cancel restores controls via native opening
state, without relying on a DOM mutation. Tablet acceptance pending.
