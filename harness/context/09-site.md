# 09 — Backend, сайт, онлайн-заказы

09.11 владелец возобновил этап. Для организаций отдельные deployments+БД; URL/key вводятся в native. Онлайн-заказы теперь в scope, лояльность TODO. Полный контракт, решения, подключение и ограничения: [BACKEND-09.md](../../docs/BACKEND-09.md).

Решения: цена сайта сохраняется при смене цены POS; обязательные модификаторы исключаются из публикации; backend не меняем, ETA только локально, готовность существующим PATCH status. Сервер b8b6ba589b052da202dbc07e25b18ec302518cf6 изучен read-only, production API не вызывался.

N01.CFG/N02.INBOX/N03.ACCEPT/N03.READY/N04.PUB implemented locally: encrypted config/inbox/publication journal; explicit menu/photo/tariff sync; version/retry checks; durable local-first parked+ACK/ready/cancel; paid marker в транзакции receipt; stock settlement; target/dataset binding/rebind; foreground SSE/heartbeat/overload; ProductDetails v3 online fields. Quoted line price сохраняется в WorkspaceCodec и после revalidation; обычные ключи строк/старые metadata fingerprints сохранены.

Online state хранится отдельными backend-online:session документами: indexed per-session reads, активные задачи отдельно от завершённой истории. NativeRestoreValidator проверяет prefix/детерминированный ID/связи; restore не разрешает автоматическую пересылку на другой target. ACK обновления не заставляют пересчитывать/перерисовывать рабочую зону.

N05/N06 и визуальная/staging приёмка не закрыты. Не объявлять live backend готовым по одному наличию кода/JVM tests. Один активный принимающий POS на deployment; перенос сетевого контекста Web-принятых заказов, редактирование category-online flags и merge online receipts — TODO. Pending/недоступность сервера не блокируют обычную локальную кассу.


10.11: ON02 modal parity follow-up и ON06 admin silent remove. Web940/zero root padding/heading/type badge/scroll body/fixed actions. Новый admin action -> existing PATCH /status cancelled, reviewed handler без Telegram; это soft delete, не физический DELETE. Native role/generation + target/dataset + not locally accepted guards, only validated ACK then encrypted inbox2 tombstone; read inbox1. Поздний accept removed ID отклоняется. Никаких production мутаций/серверных изменений. CAS на сервере отсутствует; сохраняется режим одного принимающего POS, live/pixel acceptance pending. Контракт ONLINE-LOYALTY-PARITY.md.


2026-11-10: Always available online через существующий quantity:null availability snapshot. Backend service source проверен, сервер не меняется. Stock/local sales unchanged; ../../docs/ONLINE-ALWAYS-AVAILABLE.md. Без production requests/push.

13.11.2026: QR01–QR03/D04-online — CUSTOMER-QR.md и TELEGRAM-08.md. Сервер00f8fa9 изучен read-only, QR=полный phone, уведомление=deviceChatId через PUT существующего маршрута. Backend/production не менялись; локальные fake-тесты, камера/реальный бот — ручная приёмка.

13.11.2026: Telegram monthly warehouse/monthly owner retired владельцем. UI/manual preview/send sections удалены; runtime не планирует/не отправляет monthly, legacy settings flags нормализуются false. Смены/статистика смены владельцу/online device notification остаются текущими сценариями.
