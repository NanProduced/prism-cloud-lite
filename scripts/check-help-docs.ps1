param(
  [string]$HelpRoot = "docs/help",
  [string]$ExpectedLang = ""
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

function Get-RelativeMdLinks([string]$content) {
  $matches = [regex]::Matches($content, '\]\(((?:\./|\.\./)[^)#\s]+\.md)\)')
  $out = New-Object System.Collections.Generic.List[string]
  foreach ($m in $matches) { $out.Add($m.Groups[1].Value) }
  return $out
}

if (-not (Test-Path $HelpRoot)) {
  Write-Error "Help root not found: $HelpRoot"
}

$docs = Get-ChildItem -Recurse $HelpRoot -Filter *.md
if ($docs.Count -eq 0) {
  Write-Error "No markdown docs found under: $HelpRoot"
}

$required = @(
  "title",
  "slug",
  "module",
  "audience",
  "lang",
  "docKey",
  "status",
  "owner",
  "lastUpdated"
)

$errors = New-Object System.Collections.Generic.List[string]
$slugToFiles = @{}
$docKeyToFiles = @{}

foreach ($f in $docs) {
  $content = Get-Content -Raw $f.FullName
  $fm = Read-FrontMatter $content
  if (-not $fm) {
    $errors.Add("MISSING front-matter: $($f.FullName)")
    continue
  }

  foreach ($k in $required) {
    $v = Get-FrontMatterValue $fm $k
    if (-not $v) { $errors.Add("MISSING `$${k}` in $($f.FullName)") }
  }

  $slug = Get-FrontMatterValue $fm "slug"
  if ($slug) {
    if ($slugToFiles.ContainsKey($slug)) { $slugToFiles[$slug] += ,$f.FullName }
    else { $slugToFiles[$slug] = @($f.FullName) }
  }

  $lang = Get-FrontMatterValue $fm "lang"
  if ($ExpectedLang -and $lang -and $lang -ne $ExpectedLang) {
    $errors.Add("LANG mismatch in $($f.FullName): expected=$ExpectedLang actual=$lang")
  }

  $docKey = Get-FrontMatterValue $fm "docKey"
  if ($docKey) {
    if ($docKeyToFiles.ContainsKey($docKey)) { $docKeyToFiles[$docKey] += ,$f.FullName }
    else { $docKeyToFiles[$docKey] = @($f.FullName) }
  }

  $dir = Split-Path $f.FullName
  foreach ($rel in Get-RelativeMdLinks $content) {
    $target = Join-Path $dir $rel
    if (-not (Test-Path $target)) {
      $errors.Add("BROKEN link in $($f.FullName): $rel")
    }
  }
}

foreach ($kv in $slugToFiles.GetEnumerator()) {
  if ($kv.Value.Count -gt 1) {
    $errors.Add("DUPLICATE slug: $($kv.Key) -> $($kv.Value -join ', ')")
  }
}

foreach ($kv in $docKeyToFiles.GetEnumerator()) {
  if ($kv.Value.Count -gt 1) {
    $errors.Add("DUPLICATE docKey: $($kv.Key) -> $($kv.Value -join ', ')")
  }
}

if ($errors.Count -gt 0) {
  $errors | ForEach-Object { Write-Host $_ -ForegroundColor Red }
  exit 1
}

Write-Host "OK: help docs validation passed ($($docs.Count) files)." -ForegroundColor Green
