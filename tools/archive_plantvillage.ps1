[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ArchivePath,

    [Parameter(Mandatory = $true)]
    [string]$OutputRoot,

    [ValidateSet('color', 'grayscale', 'segmented')]
    [string]$Variant = 'color',

    [string]$ClassName
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ArchivePath -PathType Leaf)) {
    throw "Archive not found: $ArchivePath"
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ArchivePath))
try {
    $prefix = "plantvillage dataset/$Variant/"
    $entries = @(
        $zip.Entries |
            Where-Object {
                $_.FullName.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase) -and
                    -not $_.FullName.EndsWith('/')
            }
    )

    if ($ClassName) {
        $classPrefix = "$prefix$ClassName/"
        $entries = @(
            $entries |
                Where-Object {
                    $_.FullName.StartsWith($classPrefix, [System.StringComparison]::OrdinalIgnoreCase)
                }
        )
    }

    if ($entries.Count -eq 0) {
        throw "No archive entries found for variant '$Variant' and class '$ClassName'."
    }

    $copied = 0
    $skipped = 0

    foreach ($entry in $entries) {
        $relative = $entry.FullName.Substring($prefix.Length).Replace('/', [IO.Path]::DirectorySeparatorChar)
        if ($relative.StartsWith("..")) {
            throw "Unsafe archive path: $($entry.FullName)"
        }

        $destination = Join-Path $OutputRoot $relative
        $parent = Split-Path -Parent $destination
        New-Item -ItemType Directory -Force -Path $parent | Out-Null

        if (Test-Path -LiteralPath $destination -PathType Leaf) {
            $existing = Get-Item -LiteralPath $destination
            if ($existing.Length -eq $entry.Length) {
                $skipped++
                continue
            }
        }

        $input = $entry.Open()
        $output = [System.IO.File]::Open($destination, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
        try {
            $input.CopyTo($output)
        }
        finally {
            $output.Dispose()
            $input.Dispose()
        }
        $copied++
    }

    [PSCustomObject]@{
        Variant = $Variant
        Class = if ($ClassName) { $ClassName } else { '(all)' }
        Entries = $entries.Count
        Copied = $copied
        Skipped = $skipped
        OutputRoot = (Resolve-Path -LiteralPath $OutputRoot).Path
    } | Format-List
}
finally {
    $zip.Dispose()
}
