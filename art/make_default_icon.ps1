# MJ money 기본 앱 아이콘 (저장소에 들어가는 공개용)
#   결과: app/src/main/res/mipmap-*/ic_launcher_foreground.png, ic_launcher.png, ic_launcher_round.png
#   배경색은 app/src/main/res/values/ic_launcher_background.xml 과 같아야 한다.
# 실행: powershell -ExecutionPolicy Bypass -File art\make_default_icon.ps1
#
# 개인용 아이콘을 쓰려면 art/make_app_icon.ps1 로 app/src/debug/res 에 만든다. (git에서 제외됨)

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$res = Join-Path $root "app\src\main\res"
$background = [System.Drawing.ColorTranslator]::FromHtml("#185FA5")
$densities = [ordered]@{ "mdpi" = 1.0; "hdpi" = 1.5; "xhdpi" = 2.0; "xxhdpi" = 3.0; "xxxhdpi" = 4.0 }

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    return @($bmp, $g)
}

function Draw-Monogram($g, [float]$canvas, [float]$fontPx) {
    $font = New-Object System.Drawing.Font "Segoe UI", $fontPx, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString("MJ", $font, [System.Drawing.Brushes]::White, (New-Object System.Drawing.RectangleF 0, 0, $canvas, $canvas), $format)
    $font.Dispose()
}

function New-RoundedRect([float]$size, [float]$radius) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc(0, 0, $d, $d, 180, 90)
    $path.AddArc($size - $d, 0, $d, $d, 270, 90)
    $path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
    $path.AddArc(0, $size - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

$brush = New-Object System.Drawing.SolidBrush $background
foreach ($name in $densities.Keys) {
    $scale = $densities[$name]
    $dir = Join-Path $res "mipmap-$name"
    New-Item -ItemType Directory -Force $dir | Out-Null

    # 적응형 아이콘 전경: 108dp 캔버스 가운데에 글자 (안전 영역 66dp 안)
    $size = [int][math]::Round(108 * $scale)
    $bmp, $g = New-Canvas $size
    Draw-Monogram $g $size (34 * $scale)
    $g.Dispose(); $bmp.Save((Join-Path $dir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

    # 옛 방식 아이콘 48dp
    $size = [int][math]::Round(48 * $scale)
    $bmp, $g = New-Canvas $size
    $g.FillPath($brush, (New-RoundedRect $size ($size * 0.22)))
    Draw-Monogram $g $size (17 * $scale)
    $g.Dispose(); $bmp.Save((Join-Path $dir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

    $bmp, $g = New-Canvas $size
    $g.FillEllipse($brush, 0, 0, $size, $size)
    Draw-Monogram $g $size (17 * $scale)
    $g.Dispose(); $bmp.Save((Join-Path $dir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}
$brush.Dispose()
"done"
