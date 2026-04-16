# Автотесты для микросервисов Home Sweet Home

## Структура тестов

### Shared модуль

- **JsonDtoParserTest** - тесты парсера JSON данных от сенсоров
- Покрывает различные типы данных (числа, строки, булевы значения)

### Event Service

- **SensorServiceImplTest** - unit тесты сервиса обработки данных сенсоров
- **SensorServiceIntegrationTest** - интеграционные тесты с MongoDB
- **EventServiceApplicationTest** - тест загрузки Spring контекста

### Presence Service

- **PresenceHandlerTest** - unit тесты обработчика данных присутствия
- **PresenceServiceIntegrationTest** - интеграционные тесты с RabbitMQ
- **PresenceServiceApplicationTest** - тест загрузки Spring контекста

## Технологии тестирования

- **JUnit 5** - основной фреймворк для тестирования
- **Mockito** - мокирование зависимостей
- **AssertJ** - улучшенные assertions
- **Spring Boot Test** - интеграционное тестирование Spring приложений
- **Reactor Test** - тестирование реактивных потоков
- **Testcontainers** - интеграционные тесты с реальными базами данных

## Конфигурация тестов

Каждый сервис имеет отдельную тестовую конфигурацию `application.properties`:

- Настройка тестовых баз данных
- Логирование для отладки

## Интеграционные тесты

Используют Testcontainers для:

- MongoDB (event-service)

Контейнеры автоматически запускаются и останавливаются для каждого теста.