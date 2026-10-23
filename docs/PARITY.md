# Карта функционального переноса

Все продуктовые области пока **planned**. Bootstrap не закрывает продуктовый acceptance. Для каждого ID менять статус только с evidence. Детали P/W — в `docs/PRODUCTS-WORKSPACE.md`; остальные acceptance IDs — в соответствующих планах.

| Acceptance IDs | Web features / adapters | Этап | Статус |
|---|---|---|---|
| P01–P10 | product-catalog/configuration/persistence/categories, product-photo | 01 | planned |
| W01–W07 | pos.html, cart-composition/presentation, receipts, android-pos-stock | 02 | planned |
| S01–S05 | payment, receipts, android-safety, stockConsumption | 03 | planned |
| I01–I04 | inventory, receiving-ui/drafts, purchase-orders, suppliers, warehouse-reporting, android-inventory | 04 | planned |
| H01–H04 | shifts, employees, analytics, administration guards | 05 | planned |
| C01–C05 | loyalty, privacy, hall-bookings, customer adapters | 06 | planned |
| B01–B05 | storage, backup/photo native boundaries | 07 | planned |
| D01–D05 | network-printer, cash drawer, native report/Telegram | 08 | planned |
| N01–N06 | web-orders, availability, delivery-tariffs, HTTPS/SSE native boundaries | 09 | planned |

В строке после реализации указывать commit/Actions URL и остающиеся device cases. Для частично завершённой области перечислять точные IDs; не присваивать процент готовности всему приложению по числу файлов или экранов.

## Инфраструктура обновлений (00.1)

U01–U06: [план](../harness/build/00.1-updates.md), [механизм](UPDATES.md). Native-only возможность, продуктовые P/W acceptance не изменяет. Статус in-progress до CI/e2e evidence.
