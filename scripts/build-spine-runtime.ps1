$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$spineTsRoot = Join-Path $projectRoot "src/main/resources/static/lib/spine/spine-runtime-3.5/spine-ts"
$bundlePath = Join-Path $spineTsRoot "build/spine-webgl.js"

if (-not (Test-Path $spineTsRoot)) {
    throw "Không tìm thấy Spine TypeScript source: $spineTsRoot"
}

Push-Location $spineTsRoot

try {
    $tsc = Get-Command tsc -ErrorAction SilentlyContinue

    if ($null -ne $tsc) {
        & $tsc.Source -p tsconfig.webgl.json
    } else {
        $npx = Get-Command npx -ErrorAction SilentlyContinue

        if ($null -eq $npx) {
            throw "Cần cài Node.js/npm hoặc TypeScript compiler (tsc) trước khi build Spine."
        }

        & $npx.Source --yes --package typescript tsc -p tsconfig.webgl.json
    }

    if ($LASTEXITCODE -ne 0) {
        throw "TypeScript compiler kết thúc với mã lỗi $LASTEXITCODE."
    }

    if (-not (Test-Path $bundlePath)) {
        throw "Build không tạo ra bundle: $bundlePath"
    }

    Write-Host "Đã tạo Spine WebGL runtime:"
    Write-Host $bundlePath
    Write-Host "Bây giờ có thể chạy .\gradlew.bat bootRun."
} finally {
    Pop-Location
}
