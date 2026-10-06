# 070 — Обычная наличная оплата: нативный ввод и tender

При открытии основного денежного keypad используем native input-mode общего
MPosSplitCashDialog: EditText, быстрые суммы и preview сдачи, «Готово»/«Отмена».
Ввод и preview не требуют bridge на каждую клавишу. Обычная последовательность
ввод → «Оплатить» на основном экране сохраняется; дополнительного подтверждения
оплаты не добавляем. «Готово» лишь обновляет transient _paymentCashGiven; оно не
сохраняет чек, не списывает stock, не вызывает сеть. Пустой ввод означает 0,
максимум две десятичные цифры, запятая/точка; начальный input пустой, как keypad.
Нативная «Отмена» закрывает редактор без применения новой суммы (прежняя сумма
сохраняется); это UI cancellation, не новая денежная операция.

MPosCashTender.confirmWhole сохраняет исходную арифметику:
roundMoney(given), given + 0.0001 >= unroundedTotal, roundMoney(given-total).
Нулевая стоимость допускается, как в источнике. Split остаётся строгим и >0.
Обычные quick values: total и ceil(total / 5/10/20/50/100/200) * denomination,
roundMoney, filter >=total && >0, distinct. Расчёт preview max(0,given-total).
Переиспользуем действующее двоичное double округление, без незаявленного перехода
на Decimal. Signed zero эквивалентен 0 на JSON границе чеков/бэкапа.

MPosCartTotalsEngine принимает опциональный cashGiven, использует вычисленный
pricing.total и возвращает cash {allowed,cashGiven,change}. Отдельный денежный
запрос не добавлен: данные tender включаются в уже существующий cartTotalsRead.
Кеш cash требует совпадения входных данных и конкретного given. Невалидный,
неполный ответ и read failure не приводят к JS fallback/нулевому чеку.

Native preflight сначала сохраняет прежние delivery/stock/quote guards. Затем
NativeCashPayment.confirm выполняет исходную серверную проверку выбранного подарка
перед проверкой достаточности суммы. Повторное подтверждение во время gift lookup
не повторяет stock/quote запросы. При успешной проверке или явном продолжении
без подарка callback снова проходит stock/quote, чтобы учитывать новые программы/
redemptions. Обновление программ сервером и явное удаление подарка разрешены.
Закрытый/заменённый payment root, изменённые корзина/клиент/скидки/доставка/части/
внесённая сумма/смена отменяют ожидающий gift callback. Затем finalizePayment
получает единственную cash part с native amount/given/change. Сохранение Room,
повторная проверка stock/pricing, availability, Telegram и печать не меняются.

UI tokens защищают редактор от повторных/устаревших ответов, закрытия/замены окна,
изменения контекста и busy операций. Cash/card/split окна взаимно закрываются
через существующий closeModal/showModal orchestration. Процесс/Activity destroy
не создаёт платёж из transient ввода.

Граница: shell оплаты, чек-preview, inline быстрые кнопки и обновление общей
денежной строки остаются reviewed WebView. HTML keypad хранится для rollback,
но штатный openPaymentKeypad отображает native editor. Это не полный native
payment screen и не подключение Compose. Legacy payment.js не редактируется.
Backup v13, ключи и JSON чеков не меняются.

Rollback: MPosNativeCashPaymentEnabled=false возвращает source keypad и source
cash confirm. Отключение PaymentTotals/preflight возвращает соответствующий
прежний путь проверки; readonly native stock/commit не изменяются. Если show не
отправлен, source keypad остаётся доступным. Отправленный confirm не повторяется.

Проверки: 56 общих source/JVM ordinary cash примеров (epsilon, 0, дробный total,
cent ties и quick values); все 50 split fixtures сохранены. Native edit UI в
Robolectric (две цифры, comma, недостаточная сумма допустима для редактирования,
Done без оплаты), quote-тест вычисленного total/tender, JS actual preflight/
source gift guards/офлайн продолжение/отмена/rollback/ошибки. Общие JS/JVM/lint.
Физические кейсы отложены до общей приёмки, APK локально не собирается.

Проверено: 238 JS tests и 227 JVM tests passed, без failures/errors/skips.
Lint: 0 errors, 16 прежних warnings. Скорость на планшете не измерена.
