<#
.SYNOPSIS
    Charge les variables d'un fichier .env dans la session PowerShell courante.
.DESCRIPTION
    Spring Boot ne lit pas les fichiers .env. Ce script lit chaque ligne CLE=VALEUR
    (les lignes vides et les commentaires # sont ignorés) et crée la variable
    d'environnement correspondante pour la session en cours uniquement.
    Les valeurs ne sont jamais affichées.
.EXAMPLE
    . .\scripts\load-env.ps1
    . .\scripts\load-env.ps1 -Path .env.test
#>
param(
    [string]$Path = ".env"
)

if (-not (Test-Path $Path)) {
    Write-Error "Fichier $Path introuvable. Copiez .env.example en $Path puis complétez-le."
    return
}

$names = @()
foreach ($line in Get-Content -Path $Path -Encoding UTF8) {
    $trimmed = $line.Trim()
    if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
    $separator = $trimmed.IndexOf("=")
    if ($separator -lt 1) { continue }
    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1)
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
    $names += $name
}
Write-Host "Variables chargées depuis ${Path} : $($names -join ', ')"
