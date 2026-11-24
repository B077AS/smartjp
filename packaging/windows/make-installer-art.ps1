# Regenerates the Inno Setup wizard artwork from src\jpack\icon.png (the app icon, rendered from frontend\public\logo.svg). Each picture is
# made at three sizes, which Inno Setup picks from by the screen's scaling, so it stays sharp on high-DPI screens:
#   wizard-large*.bmp   the banner on the Welcome and Finish pages (164x314 at 100%)
#   wizard-small*.bmp   the small logo in the header of the inner pages (55x55 at 100%)
# The look is the app's own "Snow": paper background, ink text, a soft burnt-orange glow behind the logo.
# Run from the repo root:  powershell -File packaging\windows\make-installer-art.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Resolve-Path "$PSScriptRoot\..\.."
$logo = [System.Drawing.Image]::FromFile((Join-Path $root 'src\jpack\icon.png'))

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    return $bmp, $g
}

function New-Font([string]$family, [double]$px, [System.Drawing.FontStyle]$style) {
    return New-Object System.Drawing.Font($family, [single]$px, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

# ---- the banner: paper, a warm glow, the logo, the name ---------------------------------------------------
foreach ($size in @(@(164, 314, ''), @(246, 459, '-150'), @(328, 604, '-200'))) {
    $w = $size[0]; $h = $size[1]; $s = $w / 164.0
    $bmp, $g = New-Canvas $w $h
    $rect = New-Object System.Drawing.Rectangle 0, 0, $w, $h
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect,
        [System.Drawing.Color]::FromArgb(247, 247, 245),   # paper at the top (#f7f7f5)
        [System.Drawing.Color]::FromArgb(235, 235, 232),   # tint at the bottom (#ebebe8)
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
    $g.FillRectangle($brush, $rect)
    $brush.Dispose()

    # a soft glow behind the logo, so it sits in the picture rather than on it
    $glowSize = 200 * $s
    $glowRect = New-Object System.Drawing.RectangleF ((($w - $glowSize) / 2), (52 * $s), $glowSize, $glowSize)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse($glowRect)
    $glow = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
    $glow.CenterColor = [System.Drawing.Color]::FromArgb(60, 194, 65, 12)       # the default accent, burnt orange #c2410c
    $glow.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 194, 65, 12))
    $g.FillEllipse($glow, $glowRect)
    $glow.Dispose(); $path.Dispose()

    $icon = 96 * $s
    $g.DrawImage($logo, [single](($w - $icon) / 2), [single](82 * $s), [single]$icon, [single]$icon)

    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $ink = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(27, 27, 26))     # #1b1b1a
    $name = New-Font 'Georgia' (30 * $s) ([System.Drawing.FontStyle]::Regular)
    $g.DrawString('SmartJP', $name, $ink, (New-Object System.Drawing.RectangleF 0, (190 * $s), $w, (42 * $s)), $fmt)
    $tag = New-Font 'Segoe UI' (12 * $s) ([System.Drawing.FontStyle]::Regular)
    $muted = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(98, 98, 94))   # #62625e
    $g.DrawString("Learn Japanese,`r`nkana first", $tag, $muted, (New-Object System.Drawing.RectangleF 0, (234 * $s), $w, (40 * $s)), $fmt)

    $g.Dispose()
    $bmp.Save((Join-Path $PSScriptRoot "wizard-large$($size[2]).bmp"), [System.Drawing.Imaging.ImageFormat]::Bmp)
    $bmp.Dispose()
}

# ---- the small logo: the inner pages have a white header ---------------------------------------------------
foreach ($size in @(@(55, 55, ''), @(83, 80, '-150'), @(110, 106, '-200'))) {
    $w = $size[0]; $h = $size[1]
    $bmp, $g = New-Canvas $w $h
    $g.Clear([System.Drawing.Color]::White)
    $edge = [Math]::Min($w, $h) - 6 * ($w / 55.0)
    $g.DrawImage($logo, [single](($w - $edge) / 2), [single](($h - $edge) / 2), [single]$edge, [single]$edge)
    $g.Dispose()
    $bmp.Save((Join-Path $PSScriptRoot "wizard-small$($size[2]).bmp"), [System.Drawing.Imaging.ImageFormat]::Bmp)
    $bmp.Dispose()
}

$logo.Dispose()
"wrote the wizard pictures in $PSScriptRoot"
