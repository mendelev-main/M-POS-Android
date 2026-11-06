# Визуальная сверка Web → native

Основание: запрос владельца2026-11-07. План и реестр: [VISUAL-PARITY-PLAN.md](../../docs/VISUAL-PARITY-PLAN.md). Источник и SHA: config/reference.json; native базаmain b36f610 (PR6). Dependencies: shared UI/Manrope/topbar/workspace/cart/payment уже реализованы; их CI не доказывает pixel parity.

V01/V02 ci-verified:42299895910/4677a38,172PNG/86states×2themes/DOM+CSS metrics,0fail, no stray toast. Сохраняем fixture/reference SHA/viewport/тему, не принимаем снимки с разными данными. V03–V07 planned: парная сверка верхнего меню и рабочей зоны, затем текущий заказ, оплата/части, остальные окна; native-only окна отдельно. Не изменять деньги, stock, recovery или role guards ради оформления. Device/font150%/keyboard/long-content/публикация отдельны.

Текущий main merge выполнен; release42298540849 blocked на boot ANR до app install после attempts1/2, signing/publish не запускались. Не ослаблять guard и не называть атлас релизом.
