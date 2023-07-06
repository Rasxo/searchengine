# searchengine
Учебный проект поискового движка, осуществляющий поиск по предварительно индексированным сайтам, указанным в в конфигурационном файле.
Взаимодействие осуществляется через специальный API.

## Веб-страница
Веб-интерфейс проекта представляет собой одну веб-страницу с тремя вкладками:
### Dashboard.

Эта вкладка открывается по умолчанию. На ней отображается общая статистика по всем сайтам, а также детальная статистика и статус по каждому из сайтов (статистика, получаемая по запросу /api/statistics).
### Management.
На этой вкладке находятся инструменты управления поисковым движком — запуск и остановка полной индексации (переиндексации), а также возможность добавить (обновить) отдельную страницу по ссылке:

### Search.
Эта страница предназначена для тестирования поискового движка. На ней находится поле поиска, выпадающий список с выбором сайта для поиска, а при нажатии на кнопку «Найти» выводятся результаты поиска (по API-запросу /api/search):

## Используемые технологии
Приложение построено на платформе Spring Boot.
Необходимые компоненты собираются с помощью фреймворка Maven. Maven подключает следующие относящиеся к Spring Boot стартеры:
spring-boot-starter-web — подтягивает в проект библиотеки, необходимые для выполнения Spring-MVC функций приложения. При этом обмен данными между браузером и сервером выполняется по технологии AJAX;
spring-boot-starter-data-jpa — отвечает за подключение библиотек, требующихся для работы приложения с базой данных;
spring-boot-starter-thymeleaf — шаблонизатор веб-страницы программы.

Дополнительно исспользуются следующие бибблиотеки:
jsoup — для парсинга и анализа страниц с сайтов;
mysql-connector-java — для работы с СУБД MySQL; 
org.apache.lucene.morphology — для морфологического анализа слов;
lombok - для удобства написания программного кода.

## Инструкция по запуску проекта
Репозиторий с приложением находится по адресу https://github.com/Rasxo/searchengine.

Проект можно скомпилировать и запустить с помощью среды разработки IntelliJ IDEA.

До первого запуска программы необходимо:
1.Установить СУБД MySql 8.0, если она ещё не установлена.
2.В СУБД MySql 8.0 создать схему search_engine.
3.В созданной схеме нужно создать пользователя username с паролем password, которые внести в файл application.yaml в параметры spring.datasource.username и spring.datasource.password
4.Установить параметр jpa.hibernate.ddl-auto.

После запуска программы перейти на стартовую страницу движка: http://localhost:8080/
