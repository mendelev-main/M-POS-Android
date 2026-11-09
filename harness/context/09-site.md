# 09 — Backend, сайт, онлайн-заказы

09.11 владелец возобновил этап. Для организаций отдельные deployments+БД; URL/key вводятся в native. Онлайн-заказы теперь в scope, лояльность TODO. Полный контракт, решения, подключение и ограничения: [BACKEND-09.md](../../docs/BACKEND-09.md).

Решения: цена сайта сохраняется при смене цены POS; обязательные модификаторы исключаются из публикации; backend не меняем, ETA только локально, готовность существующим PATCH status. Сервер b8b6ba589b052da202dbc07e25b18ec302518cf6 изучен read-only, production API не вызывался.

N01.CFG/N02.INBOX/N03.ACCEPT/N03.READY/N04.PUB implemented locally: encrypted config/inbox/publication journal; explicit menu/photo/tariff sync; version/retry checks; durable local-first parked+ACK/ready/cancel; paid marker в транзакции receipt; stock settlement; target/dataset binding/rebind; foreground SSE/heartbeat/overload; ProductDetails v3 online fields. Quoted line price сохраняется в WorkspaceCodec и после revalidation; обычные ключи строк/старые metadata fingerprints сохранены.

Online state хранится отдельными backend-online:session документами: indexed per-session reads, активные задачи отдельно от завершённой истории. NativeRestoreValidator проверяет prefix/детерминированный ID/связи; restore не разрешает автоматическую пересылку на другой target. ACK обновления не заставляют пересчитывать/перерисовывать рабочую зону.

N05/N06 и визуальная/staging приёмка не закрыты. Не объявлять live backend готовым по одному наличию кода/JVM tests. Один активный принимающий POS на deployment; перенос сетевого контекста Web-принятых заказов, редактирование category-online flags и merge online receipts — TODO. Pending/недоступность сервера не блокируют обычную локальную кассу.
