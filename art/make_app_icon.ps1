# 개인용 앱 아이콘 만들기 (내 폰에 설치하는 debug 빌드 전용, 저장소에는 올리지 않는다)
#   원본: art/app_icon_source.png (배경이 투명한 원형 이미지. git에서 제외됨)
#   결과: app/src/debug/res/mipmap-*/ic_launcher_foreground.png (적응형 아이콘 전경)
#         app/src/debug/res/mipmap-*/ic_launcher.png, ic_launcher_round.png (옛 방식 아이콘)
#         art/app_icon_preview.png (원·둥근 사각형 마스크 미리보기)
#   debug 리소스가 main 리소스보다 우선이라 debug 빌드는 이 아이콘을 쓰고, 공개용 기본 아이콘은 그대로 남는다.
# 실행: powershell -ExecutionPolicy Bypass -File art\make_app_icon.ps1
# 배경색은 app/src/debug/res/values/ic_launcher_background.xml 과 같아야 한다.

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $PSScriptRoot "app_icon_source.png"
$res = Join-Path $root "app\src\debug\res"
$background = [System.Drawing.ColorTranslator]::FromHtml("#8FBBE7")
$densities = [ordered]@{ "mdpi" = 1.0; "hdpi" = 1.5; "xhdpi" = 2.0; "xxhdpi" = 3.0; "xxxhdpi" = 4.0 }

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    return @($bmp, $g)
}

function Draw-Centered($g, $img, [float]$canvas, [float]$diameter) {
    $offset = ($canvas - $diameter) / 2
    $g.DrawImage($img, (New-Object System.Drawing.RectangleF $offset, $offset, $diameter, $diameter))
}

function New-RoundedRect([float]$x, [float]$y, [float]$size, [float]$radius) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $size - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $size - $d, $y + $size - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $size - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

$img = [System.Drawing.Image]::FromFile($source)
$brush = New-Object System.Drawing.SolidBrush $background
try {
    foreach ($name in $densities.Keys) {
        $scale = $densities[$name]
        $dir = Join-Path $res "mipmap-$name"
        New-Item -ItemType Directory -Force $dir | Out-Null

        # 1) 적응형 아이콘 전경: 108dp 캔버스, 엠블럼은 어떤 마스크에도 잘리지 않는 안전 영역 66dp
        $size = [int][math]::Round(108 * $scale)
        $bmp, $g = New-Canvas $size
        Draw-Centered $g $img $size (66 * $scale)
        $g.Dispose(); $bmp.Save((Join-Path $dir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

        # 2) 옛 방식 아이콘 48dp (Android 8 미만용. minSdk 26이라 실제로는 쓰이지 않지만 기본 로봇 대신 넣어둔다)
        $size = [int][math]::Round(48 * $scale)
        $bmp, $g = New-Canvas $size
        $path = New-RoundedRect 0 0 $size ($size * 0.22)
        $g.FillPath($brush, $path)
        Draw-Centered $g $img $size (40 * $scale)
        $g.Dispose(); $bmp.Save((Join-Path $dir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

        $bmp, $g = New-Canvas $size
        $g.FillEllipse($brush, 0, 0, $size, $size)
        Draw-Centered $g $img $size (40 * $scale)
        $g.Dispose(); $bmp.Save((Join-Path $dir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    }

    # 3) 미리보기: 원 마스크 / 둥근 사각형 마스크를 밝은 배경과 어두운 배경에 올려본다.
    #    적응형 아이콘에서 보이는 영역은 가운데 72dp이고, 엠블럼은 그중 66dp를 차지한다.
    $icon = 192; $gap = 32
    $width = 4 * $icon + 5 * $gap; $height = $icon + 2 * $gap
    $preview = New-Object System.Drawing.Bitmap $width, $height
    $g = [System.Drawing.Graphics]::FromImage($preview)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.FillRectangle([System.Drawing.Brushes]::WhiteSmoke, 0, 0, $width / 2, $height)
    $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#121214"))), $width / 2, 0, $width / 2, $height)
    for ($i = 0; $i -lt 4; $i++) {
        $x = $gap + $i * ($icon + $gap); $y = $gap
        if ($i % 2 -eq 0) { $g.FillEllipse($brush, $x, $y, $icon, $icon) }
        else { $g.FillPath($brush, (New-RoundedRect $x $y $icon ($icon * 0.3))) }
        $crest = $icon * 66 / 72
        $g.DrawImage($img, (New-Object System.Drawing.RectangleF ($x + ($icon - $crest) / 2), ($y + ($icon - $crest) / 2), $crest, $crest))
    }
    $g.Dispose()
    $preview.Save((Join-Path $PSScriptRoot "app_icon_preview.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $preview.Dispose()
} finally {
    $brush.Dispose()
    $img.Dispose()
}
"done"
