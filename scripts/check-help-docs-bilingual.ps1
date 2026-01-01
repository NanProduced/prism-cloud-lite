param(
  [string]$ZhRoot = "docs/help",
  [string]$EnRoot = "docs/help-en",
  [switch]$RequirePairs
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-FrontMatter([string]$content) {
  $pattern = '(?s)^(?:\uFEFF)?---\s*\r?\n(.*?)\r?\n---\s*\r?\n'
  $m = [regex]::Match($content, $pattern)
  if (-not $m.Success) { return $null }
  return $m.Groups[1].Value
}

function Get-FrontMatterValue([string]$frontMatter, [string]$key) {
  $m = [regex]::Match($frontMatter, '(?m)^' + [regex]::Escape($key) + ':\s*(.+)$')
  if (-not $m.Success) { return $null }
  return $m.Groups[1].Value.Trim()
}

function Collect-DocKeys([string]$root, [string]$expectedLang) {
  if (-not (Test-Path $root)) { throw "Root not found: $root" }
  $docs = Get-ChildItem -Recurse $root -Filter *.md
  $keys = New-Object System.Collections.Generic.HashSet[string]
  $errors = New-Object System.Collections.Generic.List[string]
  foreach ($f in $docs) {
    $content = Get-Content -Raw $f.FullName
    $fm = Read-FrontMatter $content
    if (-not $fm) { $errors.Add("MISSING front-matter: $($f.FullName)"); continue }
    $lang = Get-FrontMatterValue $fm "lang"
    if ($lang -ne $expectedLang) {
      $errors.Add("LANG mismatch in $($f.FullName): expected=$expectedLang actual=$lang")
    }
    $docKey = Get-FrontMatterValue $fm "docKey"
    if (-not $docKey) {
      $errors.Add("MISSING docKey in $($f.FullName)")
      continue
    }
    $keys.Add($docKey) | Out-Null
  }
  return @{ keys = $keys; errors = $errors; count = $docs.Count }
}

# Validate each root with the single-root checker (includes link + slug checks)
pwsh -NoProfile -File scripts/check-help-docs.ps1 -HelpRoot $ZhRoot -ExpectedLang "zh"
pwsh -NoProfile -File scripts/check-help-docs.ps1 -HelpRoot $EnRoot -ExpectedLang "en"

$zh = Collect-DocKeys $ZhRoot "zh"
$en = Collect-DocKeys $EnRoot "en"

if ($zh.errors.Count -gt 0 -or $en.errors.Count -gt 0) {
  $zh.errors | ForEach-Object { Write-Host $_ -ForegroundColor Red }
  $en.errors | ForEach-Object { Write-Host $_ -ForegroundColor Red }
  exit 1
}

$missingEn = New-Object System.Collections.Generic.List[string]
foreach ($k in $zh.keys) {
  if (-not $en.keys.Contains($k)) { $missingEn.Add($k) }
}

$missingZh = New-Object System.Collections.Generic.List[string]
foreach ($k in $en.keys) {
  if (-not $zh.keys.Contains($k)) { $missingZh.Add($k) }
}

if ($missingEn.Count -gt 0) {
  Write-Host "Missing EN docs for docKey:" -ForegroundColor Yellow
  $missingEn | Sort-Object | ForEach-Object { Write-Host ("- " + $_) -ForegroundColor Yellow }
}

if ($missingZh.Count -gt 0) {
  Write-Host "Missing ZH docs for docKey:" -ForegroundColor Yellow
  $missingZh | Sort-Object | ForEach-Object { Write-Host ("- " + $_) -ForegroundColor Yellow }
}

if ($RequirePairs -and ($missingEn.Count -gt 0 -or $missingZh.Count -gt 0)) {
  exit 1
}

Write-Host "OK: bilingual help docs validated (zh=$($zh.count), en=$($en.count))." -ForegroundColor Green

