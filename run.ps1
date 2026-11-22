# DriveEase launcher: runs the app without installing Maven.
# On the first run it downloads Apache Maven (about 9 MB) into .mvn\maven inside this project.
#
#   .\run.ps1          start the website on http://localhost:8080
#   .\run.ps1 test     run the unit tests

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# ---- 1. check Java (needs 17 or newer) ----
$javaOut = cmd /c "java -version 2>&1" | Out-String
if ($LASTEXITCODE -ne 0 -or -not $javaOut) {
    Write-Host "Java was not found. Install JDK 17 or 21 (https://adoptium.net) and open a new terminal." -ForegroundColor Red
    exit 1
}
$major = 0
if ($javaOut -match 'version "(\d+)(?:\.(\d+))?') {
    $major = [int]$Matches[1]
    if ($major -eq 1) { $major = [int]$Matches[2] }
}
Write-Host "Java version: $major" -ForegroundColor Cyan
if ($major -lt 17) {
    Write-Host "This project needs Java 17 or newer, but Java $major is installed." -ForegroundColor Red
    Write-Host "Install JDK 17 or 21 from https://adoptium.net, then open a new terminal and run .\run.ps1 again." -ForegroundColor Red
    exit 1
}

# ---- 2. get Maven (downloaded once) ----
$mavenVersion = "3.9.9"
$mavenHome = Join-Path $root ".mvn\maven\apache-maven-$mavenVersion"
$mvn = Join-Path $mavenHome "bin\mvn.cmd"
if (-not (Test-Path $mvn)) {
    $zipDir = Join-Path $root ".mvn\maven"
    New-Item -ItemType Directory -Force -Path $zipDir | Out-Null
    $zip = Join-Path $zipDir "maven.zip"
    $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    Write-Host "Downloading Maven $mavenVersion (first run only)..." -ForegroundColor Cyan
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = "SilentlyContinue"
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $zipDir -Force
    Remove-Item $zip
}

# ---- 3. run ----
if ($args.Count -gt 0 -and $args[0] -eq "test") {
    & $mvn test
} else {
    Write-Host "Starting DriveEase... open http://localhost:8080 when you see 'Started DriveEaseApplication'" -ForegroundColor Green
    & $mvn spring-boot:run
}
exit $LASTEXITCODE
