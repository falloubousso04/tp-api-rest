<#
.SYNOPSIS
    Génère une clé de signature JWT : 32 octets aléatoires (générateur cryptographique) encodés en Base64.
.EXAMPLE
    .\scripts\generate-jwt-secret.ps1
    .\scripts\generate-jwt-secret.ps1 -Bytes 64
#>
param(
    [ValidateRange(32, 1024)]
    [int]$Bytes = 32
)

$buffer = New-Object byte[] $Bytes
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($buffer)
} finally {
    $rng.Dispose()
}
[Convert]::ToBase64String($buffer)
