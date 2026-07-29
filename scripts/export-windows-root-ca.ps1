param(
    [Parameter(Mandatory = $false)]
    [string]$SubjectContains = "Sophos SSL CA",

    [Parameter(Mandatory = $false)]
    [string]$OutputPath = "certs\corporate-ca.crt"
)

$ErrorActionPreference = "Stop"

$certificate = Get-ChildItem Cert:\LocalMachine\Root |
    Where-Object { $_.Subject -like "*$SubjectContains*" } |
    Sort-Object NotAfter -Descending |
    Select-Object -First 1

if (-not $certificate) {
    throw "Nenhuma CA com '$SubjectContains' foi encontrada em Cert:\LocalMachine\Root."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutputPath = Join-Path $repositoryRoot $OutputPath
$outputDirectory = Split-Path -Parent $resolvedOutputPath

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$base64 = [Convert]::ToBase64String(
    $certificate.RawData,
    [Base64FormattingOptions]::InsertLineBreaks
)
$pem = "-----BEGIN CERTIFICATE-----`n$base64`n-----END CERTIFICATE-----`n"
$utf8WithoutBom = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($resolvedOutputPath, $pem, $utf8WithoutBom)

Write-Host "CA exportada para: $resolvedOutputPath"
Write-Host "Subject: $($certificate.Subject)"
Write-Host "Thumbprint: $($certificate.Thumbprint)"
