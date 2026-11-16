# Shared by the Windows build and QA runners.
function Get-BuildTools([string]$ToolsRoot) {
    $versions = if ($env:BUILD_TOOLS_VERSION) { @($env:BUILD_TOOLS_VERSION) } else { @("34.0.0", "36.0.0", "android-14") }
    foreach ($version in $versions) {
        $directory = Join-Path $ToolsRoot ("sdk\build-tools\" + $version)
        if (Test-Path -LiteralPath (Join-Path $directory "aapt2.exe")) { return $directory }
    }
    throw "Missing Android build tools in $ToolsRoot\sdk\build-tools. See docs/BUILDING.md."
}

function Clear-BuildDirectory([string]$SourceRoot, [string]$Directory) {
    $expected = [IO.Path]::GetFullPath((Join-Path $SourceRoot "build"))
    $target = (Resolve-Path -LiteralPath $Directory).Path
    $item = Get-Item -LiteralPath $target -Force
    if ($target -ne $expected -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Refusing to clean unexpected build directory: $target"
    }
    Remove-Item -LiteralPath $target -Recurse -Force
}
