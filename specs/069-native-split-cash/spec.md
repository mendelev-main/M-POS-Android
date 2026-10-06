# 069 — Нативный ввод наличных для смешанной части

Заменяем openSplitCashPayment HTML modal целиком на MPosSplitCashDialog
(Android Views/AlertDialog, обе темы, scroll для крупного шрифта/клавиатуры).
MPosCashTender владеет локальным вводом, номиналами, preview сдачи и проверкой
подтверждения. На каждое изменение EditText мост не вызывается. Через мост
передаются только transient данные одного окна и окончательные cashGiven/change.

Номиналы: точная сумма и 5/10/20/50/100/200/500 не меньше суммы, без повторов.
Preview: max(0, given-amount). Confirm: Math.round(given*100)/100, строго given>=amount,
затем Math.round((given-amount)*100)/100; используем общий MPosJsonNumbers.roundMoney.
Сохраняем binary double арифметику источника. Дробная внесённая сумма не обрезается
при вводе. Точка/десятичная запятая поддержаны нативной клавиатурой. Невалидные,
отрицательные, бесконечные числа и переполнение не могут подтвердить часть.
Начальная строка прежнего cashGiven передаётся как given.toFixed(2), чтобы импорт
с дробями вроде 2.675 не изменил начальную денежную величину из-за Java formatting.
Preview/номиналы форматируются через то же округление до центов.

Кнопка потребляет token один раз. Отмена/Back/outside → прежние closeModal и
renderSplitPayment, без сохранения. Старые кнопки/ответы/hide не подтверждают
новое окно. Замена на карточное окно или другое modal закрывает старое окно.
Изменение заказа/смены/частей/клиента/доставки и pending критические/cart операции
отклоняют устаревший confirm. Проверка состава/остатков до открытия — этап 066,
финальное списание после всех частей — прежняя атомарная payment transaction.

JS адаптер присваивает p.cashGiven/p.change и вызывает исходный completeSplitPayment.
Это сохраняет источник: tender меняется ДО попытки сохранения progress; paid=true
появляется только ПОСЛЕ успешного commit. При отказе tender остаётся, paid=false;
никакой новый авто-retry не добавлен. Следующая оплаченная часть/финализация/печать/
сеть сохраняют прежний порядок. Исходный payment.js, v13 backup, legacy keys и
форма paymentDraft не изменены.

Граница: обычная наличная оплата всё ещё использует inline WebView keypad.
Её epsilon 0.0001 и split строгое сравнение пока различаются, как в источнике;
унификация этого правила — отдельный бизнес-рефакторинг, не часть переноса.
Основной payment UI пока WebView; нативные cash/card окна — шаг его замены.
Compose ещё не подключён, скорость на физическом планшете не измерена.

Rollback: MPosNativeSplitCashEnabled=false. При отказе отправки show используется
reviewed HTML. Отправленный confirm не повторяется при неизвестном результате.

Проверки: 50 общих source/JVM денежных примеров, реальные UI события Robolectric
(обе темы, short amount, comma, preview, номинал, duplicate button, cancel, stale
hide, начальное toFixed), реальные completeSplitPayment JS с удержанным commit
и ошибкой записи, отмена/stale/malformed reply, взаимная замена cash/card и rollback.
Общие JS/JVM/lint, без локальной APK. Физические кейсы ожидают общей приёмки.

Проверено: 230 JS tests и 224 JVM tests passed, failures/errors/skips=0.
Lint: 0 errors, 16 прежних warnings. Robolectric теперь включает ресурсы
приложения (unitTests.isIncludeAndroidResources=true); тест этого диалога
проверяет реальную resource-строку сдачи. Локальная продуктовая APK не собиралась.
