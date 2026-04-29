param(
    [string]$Source = "app/src/main/res/drawable/logo_app.png",
    [string]$Output = "app/src/main/res/drawable/ic_launcher_foreground_safe.png",
    [int]$CanvasSize = 432,
    [int]$TargetSize = 220,
    [int]$Threshold = 38
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function Get-ColorDistanceSquared([System.Drawing.Color]$a, [System.Drawing.Color]$b) {
    $dr = [int]$a.R - [int]$b.R
    $dg = [int]$a.G - [int]$b.G
    $db = [int]$a.B - [int]$b.B
    return ($dr * $dr) + ($dg * $dg) + ($db * $db)
}

$sourcePath = (Resolve-Path $Source).Path
$outputPath = Join-Path (Get-Location) $Output
$outputDir = Split-Path -Parent $outputPath
if (!(Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

$sourceBitmap = [System.Drawing.Bitmap]::new($sourcePath)

try {
    $backgroundRef = $sourceBitmap.GetPixel(0, 0)
    $thresholdSquared = $Threshold * $Threshold
    $mask = New-Object 'bool[,]' $sourceBitmap.Width, $sourceBitmap.Height
    $queue = [System.Collections.Generic.Queue[System.Drawing.Point]]::new()

    for ($x = 0; $x -lt $sourceBitmap.Width; $x++) {
        $queue.Enqueue([System.Drawing.Point]::new($x, 0))
        $queue.Enqueue([System.Drawing.Point]::new($x, $sourceBitmap.Height - 1))
    }
    for ($y = 1; $y -lt ($sourceBitmap.Height - 1); $y++) {
        $queue.Enqueue([System.Drawing.Point]::new(0, $y))
        $queue.Enqueue([System.Drawing.Point]::new($sourceBitmap.Width - 1, $y))
    }

    while ($queue.Count -gt 0) {
        $point = $queue.Dequeue()
        $x = $point.X
        $y = $point.Y

        if ($x -lt 0 -or $y -lt 0 -or $x -ge $sourceBitmap.Width -or $y -ge $sourceBitmap.Height) {
            continue
        }
        if ($mask[$x, $y]) {
            continue
        }

        $pixel = $sourceBitmap.GetPixel($x, $y)
        if ((Get-ColorDistanceSquared $pixel $backgroundRef) -gt $thresholdSquared) {
            continue
        }

        $mask[$x, $y] = $true
        $queue.Enqueue([System.Drawing.Point]::new($x + 1, $y))
        $queue.Enqueue([System.Drawing.Point]::new($x - 1, $y))
        $queue.Enqueue([System.Drawing.Point]::new($x, $y + 1))
        $queue.Enqueue([System.Drawing.Point]::new($x, $y - 1))
    }

    $subjectBitmap = [System.Drawing.Bitmap]::new($sourceBitmap.Width, $sourceBitmap.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $minX = $sourceBitmap.Width
        $minY = $sourceBitmap.Height
        $maxX = -1
        $maxY = -1

        for ($y = 0; $y -lt $sourceBitmap.Height; $y++) {
            for ($x = 0; $x -lt $sourceBitmap.Width; $x++) {
                if ($mask[$x, $y]) {
                    $subjectBitmap.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                    continue
                }

                $pixel = $sourceBitmap.GetPixel($x, $y)
                $subjectBitmap.SetPixel($x, $y, $pixel)

                if ($pixel.A -gt 0) {
                    if ($x -lt $minX) { $minX = $x }
                    if ($y -lt $minY) { $minY = $y }
                    if ($x -gt $maxX) { $maxX = $x }
                    if ($y -gt $maxY) { $maxY = $y }
                }
            }
        }

        if ($maxX -lt 0 -or $maxY -lt 0) {
            throw "No visible subject remained after background removal."
        }

        $croppedWidth = $maxX - $minX + 1
        $croppedHeight = $maxY - $minY + 1
        $croppedBitmap = [System.Drawing.Bitmap]::new($croppedWidth, $croppedHeight, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $croppedGraphics = [System.Drawing.Graphics]::FromImage($croppedBitmap)
            try {
                $croppedGraphics.Clear([System.Drawing.Color]::Transparent)
                $croppedGraphics.DrawImage(
                    $subjectBitmap,
                    [System.Drawing.Rectangle]::new(0, 0, $croppedWidth, $croppedHeight),
                    [System.Drawing.Rectangle]::new($minX, $minY, $croppedWidth, $croppedHeight),
                    [System.Drawing.GraphicsUnit]::Pixel
                )
            } finally {
                $croppedGraphics.Dispose()
            }

            $scale = [Math]::Min($TargetSize / $croppedWidth, $TargetSize / $croppedHeight)
            $scaledWidth = [Math]::Max(1, [int][Math]::Round($croppedWidth * $scale))
            $scaledHeight = [Math]::Max(1, [int][Math]::Round($croppedHeight * $scale))
            $offsetX = [int][Math]::Round(($CanvasSize - $scaledWidth) / 2.0)
            $offsetY = [int][Math]::Round(($CanvasSize - $scaledHeight) / 2.0)

            $canvasBitmap = [System.Drawing.Bitmap]::new($CanvasSize, $CanvasSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            try {
                $graphics = [System.Drawing.Graphics]::FromImage($canvasBitmap)
                try {
                    $graphics.Clear([System.Drawing.Color]::Transparent)
                    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
                    $graphics.DrawImage(
                        $croppedBitmap,
                        [System.Drawing.Rectangle]::new($offsetX, $offsetY, $scaledWidth, $scaledHeight),
                        [System.Drawing.Rectangle]::new(0, 0, $croppedWidth, $croppedHeight),
                        [System.Drawing.GraphicsUnit]::Pixel
                    )
                } finally {
                    $graphics.Dispose()
                }

                $canvasBitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
            } finally {
                $canvasBitmap.Dispose()
            }
        } finally {
            $croppedBitmap.Dispose()
        }
    } finally {
        $subjectBitmap.Dispose()
    }
} finally {
    $sourceBitmap.Dispose()
}
