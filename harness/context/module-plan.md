# Модули и порядок старта —2026-10-24

Запрос: карта модулей/зависимостей, продолжить неописанные области, конечный объём документации и начало системы. Применены mpos-phase/mpos-domain. Пользователь явно подтвердилBYN.

Решение:21 логическая область в одном initial:app,8 пакетов документацииD01–D08. D01 спецификация01a создана; real schema/versions/tests — кодовая PR, не следующий большой документ. СемьD02–D08 перед соответствующими этапами. Дополнительно описаны warehouse/procurement/inventory, staff/shift/reporting, customers/hall. Early stock01a/02/03, minimal shift03a и remote loyalty binding06 учтены до UI сайта09.

ИсходныйWeb pin1682c88334e6fb13586ce4b530157c59ca062e43 сохранён. Изучены targeted shift/report/supplier/purchase/hall handlers; выводы code review, не runtime evidence. Схема01a/input limits — native defaults, финансовое округление отдельноD02. Все продуктовые статусы planned. Код/Gradle/dependencies/APK не менялись.
