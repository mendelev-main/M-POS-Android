# 11.11.2026 — единый крестик закрытия

По прямому запросу владельца canonical MposCloseButton теперь использует стиль прежнего крестика отложенных заказов: MposPosControl Close,44×44dp, radius12dp, фон background, border1dp outline, красный path close22dp. Старый48dp IconButton с собственным Canvas удалён. Modifier/description optional позволяют сохранить test tags и accessibility названия. Enabled/closable/busy guards прежние.

MposDialog default headers используют общий компонент; MposWebModal получил такой header close. Особые заголовки: picker/purchase наследуют shared button; online detail и folder заменили локальный вариант; customer profile получил общий крестик; полноэкранное диалоговое окно admin customer использует его вместо своего back-close. Возвраты, сохранение, отмена операций не меняются. Footer actions остаются там, где относятся к текущим сценариям; по отдельному уточнению владельца у product note footer «Закрыть» удалён полностью — только X.

В отложенных header и список используют одну44dp action column. Header inset = border1 + horizontal card padding10/12; delete40dp центрирован в44dp. Это выравнивает центры X и урны в compact/обычном виде. Height окна/карточек прежние.

Регрессии: generic/Web close44 и busy; ParkedAppearanceTest сравнивает центры close/delete и размеры; note test проверяет отсутствие footer text/закрытие X; folder UI selectors заменены на explicit tag. Только compile до приёмки владельцем на устройстве, локально без push.
