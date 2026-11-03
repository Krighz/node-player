Add-Type -AssemblyName System.Drawing

$dir = "D:\AI MAKING\node-player\app-desktop\src\main\resources"
if (-not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir -Force
}

$bmp = New-Object System.Drawing.Bitmap 256, 256, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

$center = New-Object System.Drawing.PointF 128.0, 128.0
$radius = 110.0

# 1. Subtle Outer Glow
$glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$glowPath.AddEllipse(($center.X - $radius - 8), ($center.Y - $radius - 8), (($radius + 8) * 2), (($radius + 8) * 2))
$glowBrush = New-Object System.Drawing.Drawing2D.PathGradientBrush $glowPath
$glowBrush.CenterColor = [System.Drawing.Color]::FromArgb(80, 232, 213, 183)
$glowBrush.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 232, 213, 183))
$g.FillEllipse($glowBrush, ($center.X - $radius - 8), ($center.Y - $radius - 8), (($radius + 8) * 2), (($radius + 8) * 2))

# 2. Main Liquid Disc with Cream Gradient
$discRect = New-Object System.Drawing.RectangleF ($center.X - $radius), ($center.Y - $radius), ($radius * 2), ($radius * 2)
$c1 = [System.Drawing.Color]::FromArgb(255, 232, 213, 183)
$c2 = [System.Drawing.Color]::FromArgb(255, 190, 170, 140)
$discBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new($discRect, $c1, $c2, [System.Drawing.Drawing2D.LinearGradientMode]::ForwardDiagonal)
$g.FillEllipse($discBrush, $discRect)

# 3. Vinyl Concentric Rings
$ringPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(40, 255, 255, 255)), 1.5
$g.DrawEllipse($ringPen, ($center.X - $radius * 0.55), ($center.Y - $radius * 0.55), ($radius * 1.1), ($radius * 1.1))
$g.DrawEllipse($ringPen, ($center.X - $radius * 0.72), ($center.Y - $radius * 0.72), ($radius * 1.44), ($radius * 1.44))
$g.DrawEllipse($ringPen, ($center.X - $radius * 0.88), ($center.Y - $radius * 0.88), ($radius * 1.76), ($radius * 1.76))

# 4. Top Specular Glass Rim
$rimPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(180, 255, 255, 255)), 2.5
$g.DrawArc($rimPen, ($center.X - $radius), ($center.Y - $radius), ($radius * 2), ($radius * 2), 180, 180)

# 5. Play Triangle Glyph (Dark Cashmere Ink #131728)
$triBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 19, 23, 40))
$p1 = New-Object System.Drawing.PointF 106.0, 78.0
$p2 = New-Object System.Drawing.PointF 172.0, 128.0
$p3 = New-Object System.Drawing.PointF 106.0, 178.0
$g.FillPolygon($triBrush, @($p1, $p2, $p3))

$pngPath = "$dir\icon.png"
$bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()

# Now create standard 256x256 ICO from the PNG
$icoPath = "$dir\icon.ico"
$pngBytes = [System.IO.File]::ReadAllBytes($pngPath)
$pngLen = $pngBytes.Length

$ms = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter $ms

# ICONDIR (6 bytes)
$bw.Write([UInt16]0) # Reserved
$bw.Write([UInt16]1) # Type (1 = ICO)
$bw.Write([UInt16]1) # Count (1 image)

# ICONDIRENTRY (16 bytes)
$bw.Write([Byte]0)   # Width (0 = 256)
$bw.Write([Byte]0)   # Height (0 = 256)
$bw.Write([Byte]0)   # Color count (0 = >= 8bpp)
$bw.Write([Byte]0)   # Reserved
$bw.Write([UInt16]1) # Color planes
$bw.Write([UInt16]32)# Bits per pixel
$bw.Write([UInt32]$pngLen) # Size of image data
$bw.Write([UInt32]22)      # Offset of image data (6 + 16 = 22)

# Write PNG payload
$bw.Write($pngBytes)
$bw.Flush()

[System.IO.File]::WriteAllBytes($icoPath, $ms.ToArray())
$bw.Close()
$ms.Close()

Write-Host "Generated icon.png and icon.ico successfully in $dir with size $pngLen bytes"
