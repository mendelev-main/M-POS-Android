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

13.11.2026 EV01–EV05: владелец выбрал общую историю и разрешил локальную правку backend, deployment отдельно. Новые read-only current/history маршруты, native refresh encrypted inbox и history по5/cursor; старый SSE сохраняет роль доставки новых заказов. При old backend404 явное требование обновить сервер. Snapshot текущих только complete:true и без банковских/складских write-side-effects; source/dataset guards и защита от late SSE. Документ EVENTS-DIRECTORY.md. Эксклюзивный claim заказа не внедряется в этом scope.

13.11.2026: backend обновлён в production коммитом9cfc3f6. Deploy to VDS43466136019 SUCCESS:160Node tests, Flyway/PostgreSQL и VDS setup integration прошли; production Flyway015→016 подтверждён, deployed SHA совпал, health200. Новые current/history без ключа дают401. Реальные заказы не менялись. Android-реализация этой функции остаётся локальной до отдельной команды на push/release. https://github.com/mendelev-main/M-Pos-Backend/actions/runs/43466136019

14.11.2026 EV06 implemented locally: согласованный макет События/Карточки; контакты с иконками, независимый payment badge, верхние back/refresh/close, quick minutes +1/2hours +manual. PreparationEstimate преобразует duration в существующий Web at:HH:mm на момент отправки; backend неизменен, дата ETA в протоколе отсутствует. Детали/ограничения — docs/EVENTS-DIRECTORY.md. Визуальная приёмка pending, без push.

14.11 EV06 visual follow-up: первый перенос отклонён владельцем; исправлены структура cards/history/detail, scoped palette, segmented tabs, soft selected ETA, plain custom-minutes field, badge widths. Реальная повторная визуальная приёмка pending; docs/EVENTS-DIRECTORY.md.

14.11 EV06 payment correction: web_paid_at устанавливается receipt-only settlement (backend015), а не только online payment. Стикер завершённого заказа теперь «Оплачен»/check; «Оплачен на сайте» убран. Источник/способ завершённой оплаты API не сообщает. Backend/данные не менялись.
