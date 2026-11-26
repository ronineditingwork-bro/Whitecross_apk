# WHITECROSS — каталог, остатки, счета и КП (Android)

Приложение для Android 8+ в стиле сайта wcross.ru: каталог WHITECROSS по разделам, фильтр по цвету отделки,
остатки Краснодар и Москва, корзина, «Счёт на оплату» и «Коммерческое предложение» в PDF.

## Установка
1. Откройте на телефоне https://ronineditingwork-bro.github.io/Whitecross_apk/WHITECROSS.apk (собирается при каждом push).
2. Откройте скачанный файл, разрешите установку из этого источника.
   Новые версии ставятся поверх старой, корзина и настройки сохраняются.

## Как устроено
- `app/src/main/assets/` — интерфейс: `index.html`, документы `docs.js`, продавцы `sellers.js`, каталог `products.js`, шрифты сайта.
- `app/src/main/java/ru/whitecross/price/MainActivity.java` — окно приложения и сохранение PDF в «Загрузки/WHITECROSS».
- Робот GitHub Actions (`.github/workflows/pages.yml`) собирает APK и каждый час скачивает файлы
  «sanjet_krd/msk XLS» с sanjet.ru и публикует `stock.json` на GitHub Pages; приложение его загружает.
  Settings → Pages → Source: **GitHub Actions**.

## Обновить каталог
Новая выгрузка Sanjet (CSV, «;»), затем push — APK пересоберётся:

    python3 -I tools/import_csv.py export.csv app/src/main/assets/products.js

## Проверки
    node --test test/*.test.js
