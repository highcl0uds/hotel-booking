# Запускает Eureka, Hotel Service, Booking Service и API Gateway из собранных jar (Windows PowerShell).
# Сначала соберите: .\mvnw.cmd clean install   Вывод консоли: logs\<service>.out   Остановка: scripts\stop-all.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$version = '1.0.0-SNAPSHOT'
$services = 'eureka-server', 'hotel-service', 'booking-service', 'api-gateway'
New-Item -ItemType Directory -Force logs | Out-Null

foreach ($service in $services) {
    if (-not (Test-Path "$service\target\$service-$version.jar")) {
        throw "Missing $service\target\$service-$version.jar - run .\mvnw.cmd clean install first"
    }
}

function Start-Service-Jar($service) {
    $process = Start-Process java -ArgumentList '-jar', "$service\target\$service-$version.jar" `
        -RedirectStandardOutput "logs\$service.out" -RedirectStandardError "logs\$service.err" `
        -WindowStyle Hidden -PassThru
    Set-Content -Path "logs\$service.pid" -Value $process.Id
    Write-Host "started $service (pid $($process.Id))"
}

function Wait-For($name, $url) {
    for ($i = 0; $i -lt 60; $i++) {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 2 | Out-Null
            Write-Host "$name is up"
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "$name did not start in time, see logs\"
}

Start-Service-Jar 'eureka-server'
Wait-For 'Eureka' 'http://localhost:8761/eureka/apps'
Start-Service-Jar 'hotel-service'
Start-Service-Jar 'booking-service'
Start-Service-Jar 'api-gateway'
Wait-For 'Hotel Service' 'http://localhost:8082/actuator/health'
Wait-For 'Booking Service' 'http://localhost:8081/actuator/health'
Wait-For 'API Gateway' 'http://localhost:8080/actuator/health'

Write-Host ''
Write-Host 'Gateway:    http://localhost:8080   (Swagger UI: http://localhost:8080/swagger-ui.html)'
Write-Host 'Eureka:     http://localhost:8761'
Write-Host 'Services register in Eureka within ~10 s; until then the gateway may answer 503.'
