#!/usr/bin/env bash
# Запускает Eureka, Hotel Service, Booking Service и API Gateway из собранных jar.
# Сначала соберите: ./mvnw clean install   Вывод консоли: logs/<service>.out   Остановка: scripts/stop-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="1.0.0-SNAPSHOT"
SERVICES=(eureka-server hotel-service booking-service api-gateway)
mkdir -p logs

for service in "${SERVICES[@]}"; do
  if [[ ! -f "$service/target/$service-$VERSION.jar" ]]; then
    echo "Missing $service/target/$service-$VERSION.jar - run ./mvnw clean install first" >&2
    exit 1
  fi
done

start() {
  nohup java -jar "$1/target/$1-$VERSION.jar" > "logs/$1.out" 2>&1 &
  echo $! > "logs/$1.pid"
  echo "started $1 (pid $!)"
}

wait_for() {
  local name=$1 url=$2
  for _ in $(seq 1 60); do
    if curl -sf "$url" > /dev/null 2>&1; then
      echo "$name is up"
      return 0
    fi
    sleep 2
  done
  echo "$name did not start in time, see logs/" >&2
  exit 1
}

start eureka-server
wait_for "Eureka" "http://localhost:8761/eureka/apps"
start hotel-service
start booking-service
start api-gateway
wait_for "Hotel Service" "http://localhost:8082/actuator/health"
wait_for "Booking Service" "http://localhost:8081/actuator/health"
wait_for "API Gateway" "http://localhost:8080/actuator/health"

echo
echo "Gateway:    http://localhost:8080   (Swagger UI: http://localhost:8080/swagger-ui.html)"
echo "Eureka:     http://localhost:8761"
echo "Services register in Eureka within ~10 s; until then the gateway may answer 503."
