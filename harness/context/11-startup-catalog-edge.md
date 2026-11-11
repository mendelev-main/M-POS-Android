# 11.11.2026 — стартовая рабочая зона / край каталога

По запросу владельца fresh launch default route workspace вместо catalog, глобальный back fallback ведёт workspace. rememberSaveable продолжает восстанавливать текущий экран при recreation, а не сбрасывать открытый редактор. Catalog page bottom padding0, таблица заполняет доступную высоту даже при малом числе товаров; нижние углы прямые, верхние22dp прежние. Убрана подсказка «Для изменения каталога войдите…»; доступ/защита записи прежние.

UI fixtures, которым нужен каталог, теперь явно заходят через settingsCatalog; default startup не подменяется test override. Добавлен StartupWorkspaceTest fresh/recreate. Общий helper работает до получения ленивого CatalogViewModel, предотвращая ожидание loading VM, чей экран ещё не открывался. TopNavigationUiTest обновлён под доступное верхнее меню карточки товара (предыдущая задача). Локальная компиляция/UI runtime evidence — build-log; без push.
