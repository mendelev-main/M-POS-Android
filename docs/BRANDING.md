# Иконка и бренд M-POS

Источник — PNG1254×1254, предоставленный пользователем2026-10-24. Оригинал сохранён без изменения в `app/src/main/res/drawable-nodpi/mpos_logo_original.png`. Для launcher создан нативный векторный foreground с белыми M/POS на чёрном фоне: масштабируется без растрового размытия. Это адаптация геометрии логотипа, не новый знак.

`mipmap-anydpi-v26/ic_launcher{,_round}` используют adaptive icon; Android/launcher выбирает маску. Контент размещён внутри безопасной центральной области. ВAPI33+ добавлен monochrome layer для themed icons по выбору пользователя launcher. Не требуется своё жёстко закруглённое bitmap окно поверх системной маски.

Manifest icon/roundIcon подключены к тем же package/signing identity. Инструментальный тест рендерит настоящий ApplicationIcon в PNG512 для QA; это проверка resource drawable, не фотография физического launcher. Размер/форма на пользовательском планшете проверяются отдельно.

Manrope переносится из pinned Web; SIL OFL находится в `third_party/manrope/OFL.txt` и внутри APK assets/licenses/Manrope-OFL.txt. Дизайн интерфейса: [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md).
