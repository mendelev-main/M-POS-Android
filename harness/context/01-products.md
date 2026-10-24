# 01a — реализация каталога,2026-10-24

Авторизована пользователем после выбораBYN/direct stock/no reason и дизайнаWeb. Scope: simple/category01a, Room first schema, commandId/fingerprint/revision, stock opening/adjustment ledger, owner mode, draft recovery, shared UI components/theme/Manrope и пользовательская иконка. Recipe/units expansion/modifiers/photo01b/01c и checkout02/03 не входят.

Room2.8.4/KSP2.2.21-2.0.4 выбраны по официальным release notes для закреплённой Kotlin2.2.21; зависимости проверяет CI. SDK локально отсутствует; Android tests/lint/compile выполняет Actions. Legacy PNG1254x1254 сохранён без изменения; adaptive foreground вручную перенесён в vector с чёрным фоном, Android задаёт mask. Key/package неизменны.

Новые проверки: parser/precision/fingerprint JVM; actual Room rollback, duplicate/conflict/stock/category constraints/concurrent updates/reopen/draft; Compose создать/stockedit/recreate/shared-close. Сначала результатCI, затем parity/release.
