# Local Windows build using Android Studio SDK and Gradle 8.9 on PATH.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java 17+ is required. Install Android Studio JBR or a JDK.' }
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) { throw 'Gradle 8.9 is required on PATH. Install with Android Studio or Gradle distribution. See README.' }
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) { Write-Warning 'Set ANDROID_HOME to your Android SDK directory if Gradle cannot discover it.' }
gradle --no-daemon :app:assembleStudentDebug :app:assembleAdminDebug
if ($LASTEXITCODE -ne 0) { throw 'Gradle APK build failed.' }
$student = Join-Path $PSScriptRoot 'app\build\outputs\apk\student\debug\app-student-debug.apk'
$admin = Join-Path $PSScriptRoot 'app\build\outputs\apk\admin\debug\app-admin-debug.apk'
foreach ($apk in @($student, $admin)) {
  if (-not (Test-Path $apk)) { throw "APK missing: $apk" }
  Write-Host "Built: $apk"
  Get-FileHash $apk -Algorithm SHA256
}
