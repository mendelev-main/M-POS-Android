# Происхождение визуальных стандартов

Pinned Web commit `1682c88334e6fb13586ce4b530157c59ca062e43`. Значения извлечены из локального авторитетного Android asset, не из абстрактного макета или сторонней UI-библиотеки. Внешний вид ещё не проверялся парным запуском Web/native.

| Asset / selector | Основание стандарта |
|---|---|
| pos.html, @font-face и :root | Manrope и light/dark semantic palette, radius/control variables |
| pos.html, .btn / .btn-* | button color variants, typography, pressed/disabled source states |
| pos.html, .field / input / select | форма/контур/фокус/label |
| pos.html, .modal-overlay / .modal | scrim rgba(27,31,42,.45), radius22, width460, padding28 |
| pos.html, .parked-modal-close | visual44, radius12, svg22, background/muted; общий close native основан на нём |
| pos.html, .pe-*; product-editor.js renderProductModal | полноценный editor header/navigation/card/summary, не маленький generic dialog |
| pos.html, .parked-orders-modal / .supply-document | широкие списки/документы, разные layout variants |
| android-admin.js injected styles | admin actions используют shared palette/radius-m, не отдельный бренд |
| Web/fonts/Manrope-Variable.ttf и OFL.txt | фактический шрифт и лицензия для будущего включения |

В `config/ui-design.json` сохранены SHA-256 pos.html, исходные палитры и отдельно native mapping размеров. CSS selector overrides существуют; общие tokens и перечисленные final component selectors сверены чтением HTML. Региональные/feature overrides не объявляются отдельными глобальными tokens.

## План сравнения

Синтетический каталог/корзина с одинаковыми именами/количествами, одна тема и доступная ширина; зафиксировать density/font scale/insets/состояние клавиатуры. Сравнить Web и native: список/редактор товара, basic/dirty/confirmation dialog, модификаторы, current/parked, checkout позже03, settings/updater. Следом dark и узкая ширина/большой системный текст.

Не подменять «одинаковый дизайн» обещанием равенства CSS px и hardware pixels. Отличия system dialogs Android ожидаемы. Остальные расхождения отмечаются: ошибка переноса / необходимая platform accessibility адаптация / согласованный variant. Источник правки и evidence сохраняются в build-log.

Верхняя панель: `pos.html:76–108,815–819,1918–1938` и `Web/js/features/employees.js:employeeShortName`; контракт [TOP-NAVIGATION.md](TOP-NAVIGATION.md). Дополнительная строка inventory-reminder условная, не постоянная вкладка.

## Рабочая зона

Текущий native перенос pinned `screen-pos`, `pos-toolbar`, `pcard`, `cart-*` описан в [WORKSPACE-VISUAL](WORKSPACE-VISUAL.md):380dp корзина,16dp outer gap,12dp grid gap, source toolbar и компактные строки заказа. Это контракт геометрии, а не подтверждение полного pixel diff; одинаковый viewport/dark/физический планшет проверяются отдельно. Новые варианты кнопок ограничены POS toolbar/cart, общий MposDialog/Close сохраняется.
