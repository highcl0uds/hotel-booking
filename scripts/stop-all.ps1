# Останавливает сервисы, запущенные scripts\start-all.ps1.
Set-Location (Join-Path $PSScriptRoot '..')

Get-ChildItem logs -Filter '*.pid' -ErrorAction SilentlyContinue | ForEach-Object {
    $service = $_.BaseName
    $processId = [int](Get-Content $_.FullName)
    try {
        Stop-Process -Id $processId -Force -ErrorAction Stop
        Write-Host "stopped $service (pid $processId)"
    } catch {
        Write-Host "$service is not running"
    }
    Remove-Item $_.FullName
}
