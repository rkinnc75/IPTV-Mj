param(
    [Parameter(Mandatory=$true)][string]$Note,
    [string]$VersionName
)

$gradlePath = "app/build.gradle"
$changelogPath = "CHANGELOG.md"

# Read current build.gradle
$gradle = Get-Content $gradlePath -Raw

# Bump versionCode
$currentCode = [int]([regex]::Match($gradle, 'versionCode (\d+)').Groups[1].Value)
$newCode = $currentCode + 1
$gradle = $gradle -replace "versionCode $currentCode", "versionCode $newCode"

# Update versionName if provided, else keep current
$currentName = [regex]::Match($gradle, 'versionName "([^"]+)"').Groups[1].Value
if ($VersionName) {
    $gradle = $gradle -replace "versionName `"$currentName`"", "versionName `"$VersionName`""
    $displayName = $VersionName
} else {
    $displayName = $currentName
}

[System.IO.File]::WriteAllText("$PWD\$gradlePath", $gradle)

# Prepend to changelog
$date = Get-Date -Format "yyyy-MM-dd HH:mm"
$existing = Get-Content $changelogPath -Raw
$header = "# IPTV App - Changelog`r`n`r`n"
$body = $existing -replace [regex]::Escape($header), ""
$newEntry = "## v$displayName (build $newCode) - $date`r`n- $Note`r`n`r`n"
[System.IO.File]::WriteAllText("$PWD\$changelogPath", $header + $newEntry + $body)

# Keep the in-app changelog (bundled in assets) in sync
$assetsDir = "app/src/main/assets"
if (-not (Test-Path $assetsDir)) { New-Item -ItemType Directory -Path $assetsDir | Out-Null }
Copy-Item $changelogPath "$assetsDir/CHANGELOG.md" -Force

Write-Host "Bumped to build $newCode (v$displayName)"
Write-Host "Added changelog entry: $Note"
