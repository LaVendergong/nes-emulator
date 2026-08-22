# 用 JDK 自带 jpackage 打 Windows 便携包。产物：dist/FC-NES/FC-NES.exe 与 zip。
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$version = (Select-String -Path pom.xml -Pattern '<version>([^<]+)</version>' |
    Select-Object -First 1).Matches.Groups[1].Value
if (-not $version) {
    throw "读不到 pom.xml 的 version"
}

mvn -q -DskipTests package
$jar = "target/emulator-$version.jar"
if (-not (Test-Path $jar)) {
    throw "缺少 $jar"
}

$in = "target/jpackage-in"
Remove-Item -Recurse -Force $in -ErrorAction SilentlyContinue
New-Item -ItemType Directory $in | Out-Null
Copy-Item $jar (Join-Path $in "emulator.jar")

$dest = "dist"
Remove-Item -Recurse -Force (Join-Path $dest "FC-NES") -ErrorAction SilentlyContinue
New-Item -ItemType Directory $dest -Force | Out-Null

jpackage `
    --type app-image `
    --name FC-NES `
    --app-version $version `
    --vendor LaVendergong `
    --description "NTSC FC/NES emulator" `
    --dest $dest `
    --input $in `
    --main-jar emulator.jar `
    --main-class nes.host.Main `
    --add-modules java.desktop `
    --java-options "-Dfile.encoding=UTF-8"

$zip = Join-Path $dest "fc-nes-$version-windows.zip"
if (Test-Path $zip) {
    Remove-Item $zip
}
Compress-Archive -Path (Join-Path $dest "FC-NES") -DestinationPath $zip
Write-Host "packed $zip"
