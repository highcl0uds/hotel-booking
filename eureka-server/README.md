# eureka-server

Реестр сервисов (Spring Cloud Netflix Eureka). Порт **8761**, панель: http://localhost:8761.

В нём регистрируются `hotel-service`, `booking-service` и `api-gateway`.
- Gateway находит сервисы по адресам `lb://hotel-service` и `lb://booking-service`.
- Booking Service обращается к Hotel Service через `http://hotel-service` (Spring Cloud LoadBalancer).

Настройки для локального запуска (`application.yml`):
- self-preservation выключен, остановленные экземпляры удаляются через несколько секунд;
- клиенты обновляют реестр раз в 5 секунд.

| Переменная | По умолчанию |
|---|---|
| `EUREKA_PORT` | `8761` |
| `EUREKA_HOSTNAME` | `localhost` |
