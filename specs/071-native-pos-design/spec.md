# 071 — Единый нативный визуальный стиль M POS

Пользователь просит подключить навыки дизайна и исправить визуальное расхождение
экрана смены/выбора сотрудника с основным POS. Эта спецификация изменяет presentation,
не денежную политику, авторизацию или persistence.

Установлены локальные навыки: upstream frontend-design от Anthropic, pinned
683bc88e56f3e09ba94f7055977f3d3aa499f202 с Apache-2.0 лицензией/источником рядом,
и собственный mpos-native-design для Android POS. AGENTS.md требует их чтения
при UI задачах. Это repo skills, не глобальный плагин/изменение облачного каталога.
Внешние scripts не выполнялись. Правила пользователя/проекта имеют приоритет.

MPosNativeTheme повторяет reviewed CSS палитру light/dark и использует уже
поставляемый offline Manrope. Переменная ось wght задана явно (400/600/700),
Typeface кешируется. Радиусы 10–12/22dp, spacing 8/12/16/24, touch >=48dp,
sp текст, ripple/focus/disabled цвета, primary/secondary/danger, без all-caps.
Новых UI/runtime зависимостей или сетевых шрифтов нет. Skills/docs не входят APK.

Экран смены: ясные секции и значения, Manrope/веса, карточки истории вместо
многострочных кнопок, оформленные кассовые действия, общие поля и фон. Financial
rows переносят значение вниз при ширине <600dp или fontScale>1.3, сохраняя scroll.
Денежные строки используют запятую как основной POS; валюта сохраняется дословно.
Данные/формулы/порядок истории и action→shiftId сохраняются.

Открытие смены: themed loading/result dialog, оформленный employee Spinner с
правой стрелкой и кастомными строками списка, пароль admin только по прежнему
условию, сводка carryover, scroll, главная кнопка, ошибки/disabled/busy состояния.
Тема передаётся из POS; employee mapping, verifier, clearing/no-save password,
read correlation, blocked operations и callback submission не меняются.

Общий стиль также используется card confirmation, cash input/split cash,
cash movements и closing forms. Show/Cancel/Confirm, token correlation,
суммы/округление/readonly quote, storage-before-network, rollback и v13 неизменны.
Внешний вид progress/ошибок меняется, их смысл и обработка остаются прежними.

Проверки: полные существующие JS/JVM и lint; native graphics Robolectric
рендерит синтетические превью выбора сотрудника/смены в обеих темах. Проверены
палитра, touch target, disabled state и денежное значение без изменения. Превью
просмотрены и повторены после исправления font axis. Физическая landscape,
keyboard/fontScale приёмка отложена и добавлена в общий список. APK локально
не собирается. Preview artifacts/docs сохраняются отдельно от app assets.

Новый денежный домен этим этапом не мигрирован; общий payment shell ещё WebView.
Стиль централизован, чтобы следующие native screens/Compose использовали тот же
визуальный язык. Репозиторные design skills остаются для последующих задач.

Проверено: 238 JS и 228 JVM tests passed, failures/errors/skips=0.
Lint: 0 errors, 15 оставшихся прежних warnings (один прежний UseKtx устранён
централизацией палитры). Превью: `docs/design/071/README.md`. APK не собиралась.
