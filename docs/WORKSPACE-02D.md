# Рабочая зона02d — размеры папок и сумма конфигурации

Часть W01/W02 и общей UI приёмки02. Fixed-slot drag, performance большого каталога и physical walkthrough не входят в этот инкремент; остаются следующими задачами. Использованы mpos-phase/mpos-domain. Pinned1682c883: pos-navigation.js renderPosFolderModal и pos.html611; cart-composition.js updateModifierSelectionTotal/confirmModifierSelection. Нативный общий Dialog/Close/buttons не заменяются отдельными стилями.

Папка: число колонок clamp(products,1,4), ширины280/448/660/872dp соответствуют Web280/448/660/872 CSSpx. Shared MposDialog получает documented maxWidth variant. Сетка ограничивает число колонок фактически доступной шириной (минимум150dp на tile +12dp gap); высота по числу рядов (144dp/ряд, gap12dp) до420dp, далее прокрутка. В пустой папке сообщение. Компактная карточка папки ограничивает имя двумя строками и не повторяет подсказку выбора модификаторов, как в pinned Web. Состояние папки/выбора и return-after-configuration остаются Saveable. Pixel-perfect density и font-scale проверяются отдельно на планшете.

Конфигурация: сумма базы и выбранных доплат обновляется сразу. Пример ручная цена2.50, обязательное молоко+0.50: до выбора2.50 BYN, после3 BYN. Незавершённый выбор может показывать сумму, но Add по-прежнему вызывает строгую domain validation min/max/unknown options; не разрешает сохранить неполную обязательную группу. Недопустимая ручная цена не показывается как0, вместо суммы приглашение исправить ввод. CartEngine использует прежнюю строгую selectionPrice по умолчанию, preview явно отключает только minimum cardinality. Никаких Double/нового ledger/миграций.

Проверки: доменный тест partial preview/strict commit/unknown option, расширенный WorkspaceUiTest проверяет2.5→3 BYN и возврат в папку после добавления, recreate и updater seed. Screenshots folder/configuration сохраняются существующим driver. CI evidence в build-log; до фактического результата статус implemented.


## CI evidence

[run40172821096](https://github.com/mendelev-main/M-POS-Android/actions/runs/40172821096), final runtime da8db71: checks121107096327 и emulator36 job121107097017 success.41JVM/0failures,6Python,lint/assembleDebug;41instrumentation и4explicit UI seed tests passed. Обновлённый WorkspaceUiTest подтверждает live total2.5→3 BYN, strict required-group refusal, folder return/recreate; прежние Room/revalidation/parked regression suites проходят. External updater: actual package1→2, CATALOG stock8, current quantity5/total15 BYN, parked delivery label/total5 BYN сохранены. Signing/publish/production-upgrade skipped на develop.

Screenshots workspace-folder/workspace-configuration просмотрены: компактная папка с1товаром, единые Close/buttons, сумма3 BYN при manual2.50+milk0.50; перекрытий в показанных состояниях нет. Снимки на1600×1125/200dpi, viewport1280×900dp. Это второй последовательный успешный полный run с новой AVD конфигурацией (после40172190371); Quickstep recovery в обоих логах не вызывалась. Причина прошлых ANR окончательно не доказана, системные ошибки не подавлялись. Физическая font-scale/orientation/планшетная приёмка отложена пользователем;4колонки/прочие размеры не объявляются device-verified по одному screenshot.

02c gate закрыт,02d UI в указанном scope ci-verified. SQLschema5/payload2, main/stable/key не менялись. Следующая часть W01: persistent fixed slots и drag с atomic CAS/replay/rollback, пустые места и доступная альтернатива перемещению. Большой каталог/performance/Coil и physical acceptance остаются открытыми. Полный02 не закрыт,03 не начинать автоматически.
