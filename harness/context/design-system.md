# Единый UI и уточнение stock editor —2026-10-24

Пользователь: финал как Web, последовательность типов несущественна; stock можно редактировать в карточке без причины; дизайн из старого приложения, close/buttons/radii едины. Применён frontend-design с приоритетом пользовательского reference, существующие mpos-phase/domain правила сохранены.

Исследованы pinned pos.html palette/btn/field/modal/pe/parked-close и поздние overrides, product-editor header, android-admin styles, Manrope/OFL. Добавлены DESIGN-SYSTEM/UI-REFERENCE и specification-only config/ui-design.json с source hash. Значения CSS отделены от native dp/sp mapping; визуальный запуск не выполнялся, DS cases planned.

Stock UI editable без обязательной причины, внутри product_card delta с expectedStockRevision/commandId. Старый запрет ADR-003 отменёнADR-005, актуальные docs обновлены. Первый scope остаётся инкрементом, итоговые единицы/типы обязательны. Kotlin/theme/business code не менялись.

Обнаружено расхождение: Web VISUAL_PARITY описывает POS presentation текущего cart, тогда как native спецификация всех parked reservations была более строгой. Перед02 нужно сверить реальный handler+android adapter и согласовать на примере, не считать оба утверждения доказанным паритетом. Эта задача не меняет reserve policy молча.
