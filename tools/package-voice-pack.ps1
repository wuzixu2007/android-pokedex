[CmdletBinding()]
param(
    [string]$SourceRoot = 'D:\AndroidApp\aac_64k_48kHz\aac_64k_48kHz',
    [string]$CatalogPath = "$PSScriptRoot\..\artifacts\cloud-resources\staging\assets\pokemon\catalog.json",
    [string]$VoiceId = 'original',
    [string]$OutputRoot = '',
    [int]$Revision = 1,
    [string]$ContentVersion = 'v1',
    [string]$BundleUrl = '',
    [int]$MinAppVersion = 2
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-Sha256([string]$Path) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
        try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
        finally { $stream.Dispose() }
    } finally { $sha.Dispose() }
}

function Assert-SafePath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path) -or $Path.StartsWith('/') -or $Path.Contains('\\') -or $Path.Split('/') | Where-Object { $_ -in @('', '.', '..') }) {
        throw "Unsafe archive path: $Path"
    }
}

if (!(Test-Path -LiteralPath $SourceRoot -PathType Container)) { throw "Audio source does not exist: $SourceRoot" }
if (!(Test-Path -LiteralPath $CatalogPath -PathType Leaf)) { throw "Catalog does not exist: $CatalogPath" }
if ($Revision -lt 1) { throw 'Revision must be positive' }
if ($VoiceId -notin @('original', 'rotom', 'ggbond', 'lazy_sheep', 'gray_wolf')) { throw "Unsupported voice ID: $VoiceId" }
if ([string]::IsNullOrWhiteSpace($OutputRoot)) { $OutputRoot = "$PSScriptRoot\..\artifacts\voice-packs\$VoiceId\v$Revision" }
if ([string]::IsNullOrWhiteSpace($BundleUrl)) {
    $visibility = if ($VoiceId -eq 'original') { 'public' } else { 'private' }
    $BundleUrl = "https://example.invalid/pokedex/voices/$visibility/$VoiceId/$VoiceId-v$Revision.zip"
}

$catalogJson = Get-Content -LiteralPath $CatalogPath -Raw -Encoding UTF8 | ConvertFrom-Json
$expectedKeys = @($catalogJson.records | ForEach-Object { $_.key })
$audioFiles = @(Get-ChildItem -LiteralPath $SourceRoot -File -Filter '*.aac')
$actualKeys = @($audioFiles | ForEach-Object { $_.BaseName })
$missing = @($expectedKeys | Where-Object { $_ -notin $actualKeys })
$extra = @($actualKeys | Where-Object { $_ -notin $expectedKeys })
if ($missing.Count -gt 0) { throw "Missing AAC files: $($missing -join ', ')" }
if ($extra.Count -gt 0) { throw "Unexpected AAC files: $($extra -join ', ')" }
if ($audioFiles.Count -ne $expectedKeys.Count) { throw "Expected $($expectedKeys.Count) AAC files, found $($audioFiles.Count)" }
if (@($audioFiles | Where-Object Length -le 0).Count -gt 0) { throw 'Empty AAC file found' }

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputRoot)
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$zipPath = Join-Path $resolvedOutput "$VoiceId-v$Revision.zip"
$manifestPath = Join-Path $resolvedOutput 'manifest.json'
Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $manifestPath -Force -ErrorAction SilentlyContinue

$entries = [System.Collections.Generic.List[object]]::new()
$jar = Get-Command jar -ErrorAction SilentlyContinue
if ($null -eq $jar) {
    $bundledJar = 'C:\Users\jy420\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\jar.exe'
    if (Test-Path -LiteralPath $bundledJar -PathType Leaf) { $jar = Get-Item -LiteralPath $bundledJar }
}
if ($null -eq $jar) { throw 'JDK jar tool is required to create a ZIP with stored (uncompressed) entries.' }

$jarArguments = [System.Collections.Generic.List[string]]::new()
foreach ($file in $audioFiles | Sort-Object BaseName) {
    $path = "$($file.BaseName).aac"
    Assert-SafePath $path
    # jar applies -C only to the next path, so repeat it for every entry.
    $jarArguments.Add('-C')
    $jarArguments.Add($SourceRoot)
    $jarArguments.Add($path)
    $entries.Add([ordered]@{
        path = $path
        sizeBytes = [int64]$file.Length
        sha256 = Get-Sha256 $file.FullName
    })
    Write-Progress -Activity 'Hashing voice files' -Status $path -PercentComplete (($entries.Count / $audioFiles.Count) * 100)
}
Write-Progress -Activity 'Packaging voice files' -Completed

# The .NET ZIP implementation writes Deflate entries even at NoCompression.
# jar --no-compress emits ZIP method 0 (store), which avoids wasting CPU on AAC.
$jarPath = $jar.Path
if ([string]::IsNullOrWhiteSpace($jarPath)) { $jarPath = $jar.Source }
if ([string]::IsNullOrWhiteSpace($jarPath)) { $jarPath = $jar.FullName }
$jarArgumentFile = Join-Path $resolvedOutput (".jar-arguments-{0}.txt" -f [guid]::NewGuid().ToString('N'))
try {
    @('--create', "--file=$zipPath", '--no-compress', '--no-manifest') + @($jarArguments) |
        Set-Content -LiteralPath $jarArgumentFile -Encoding ASCII
    & $jarPath "@$jarArgumentFile"
    if ($LASTEXITCODE -ne 0) { throw "jar failed while creating $zipPath" }
}
finally {
    Remove-Item -LiteralPath $jarArgumentFile -Force -ErrorAction SilentlyContinue
}

$manifest = [ordered]@{
    voiceId = $VoiceId
    revision = $Revision
    contentVersion = $ContentVersion
    minAppVersion = $MinAppVersion
    bundle = [ordered]@{
        url = $BundleUrl
        sizeBytes = [int64](Get-Item -LiteralPath $zipPath).Length
        sha256 = Get-Sha256 $zipPath
    }
    files = $entries
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
[pscustomobject]@{
    Zip = $zipPath
    Manifest = $manifestPath
    Files = $entries.Count
    ZipBytes = $manifest.bundle.sizeBytes
    ZipSha256 = $manifest.bundle.sha256
}
