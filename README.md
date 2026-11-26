# WHITECROSS — прайс, остатки, счета и КП

Веб-приложение для iPhone и Android (Safari/Chrome → «На экран „Домой“»), устроено как приложение MAPEI.

- Каталог: 1741 товар WHITECROSS из выгрузки Sanjet (`products.json`), розничные цены (РРЦ).
- Остатки Краснодар и Москва: файлы `sanjet_krd XLS.xls` и `sanjet_msk XLS.xls` из кабинета sanjet.ru. Робот GitHub Actions обновляет `stock.json` каждый час, пароль не нужен.
- Корзина → «Счёт на оплату» или «Коммерческое предложение» → PDF прямо в браузере. Продавцы — `sellers.js`.

## Публикация
Settings → Pages → Source: **GitHub Actions**. Workflow `.github/workflows/pages.yml` публикует сайт при каждом push в `main` и каждый час.

## Обновить каталог
Новая выгрузка Sanjet (CSV, «;»):

    python3 -I tools/import_csv.py export.csv products.json

## Проверки
    node --test test.js
