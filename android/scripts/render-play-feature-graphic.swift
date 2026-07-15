#!/usr/bin/env swift

import AppKit
import Foundation

let canvasWidth = 1024
let canvasHeight = 500
let repositoryRoot = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let patientURL = repositoryRoot.appendingPathComponent(
    "ios/MedicationApp/Assets.xcassets/RolePatient.imageset/role-patient.png"
)
let familyURL = repositoryRoot.appendingPathComponent(
    "ios/MedicationApp/Assets.xcassets/RoleFamily.imageset/role-family.png"
)
let outputURL = repositoryRoot.appendingPathComponent(
    "docs/android/play-store-assets/feature-graphic-1024x500.jpg"
)

guard let patientImage = NSImage(contentsOf: patientURL),
      let familyImage = NSImage(contentsOf: familyURL) else {
    fatalError("Run this script from the repository root with the iOS role assets present.")
}

guard let bitmap = NSBitmapImageRep(
    bitmapDataPlanes: nil,
    pixelsWide: canvasWidth,
    pixelsHigh: canvasHeight,
    bitsPerSample: 8,
    samplesPerPixel: 4,
    hasAlpha: true,
    isPlanar: false,
    colorSpaceName: .deviceRGB,
    bytesPerRow: 0,
    bitsPerPixel: 0
) else {
    fatalError("Unable to allocate feature-graphic canvas.")
}

func color(_ hex: UInt32, alpha: CGFloat = 1) -> NSColor {
    NSColor(
        calibratedRed: CGFloat((hex >> 16) & 0xff) / 255,
        green: CGFloat((hex >> 8) & 0xff) / 255,
        blue: CGFloat(hex & 0xff) / 255,
        alpha: alpha
    )
}

func rect(x: CGFloat, top: CGFloat, width: CGFloat, height: CGFloat) -> NSRect {
    NSRect(x: x, y: CGFloat(canvasHeight) - top - height, width: width, height: height)
}

func fillRounded(_ frame: NSRect, radius: CGFloat, fill: NSColor, stroke: NSColor? = nil, lineWidth: CGFloat = 1) {
    let path = NSBezierPath(roundedRect: frame, xRadius: radius, yRadius: radius)
    fill.setFill()
    path.fill()
    if let stroke {
        stroke.setStroke()
        path.lineWidth = lineWidth
        path.stroke()
    }
}

func drawText(_ text: String, x: CGFloat, top: CGFloat, size: CGFloat, weight: NSFont.Weight, textColor: NSColor) {
    let font = NSFont.systemFont(ofSize: size, weight: weight)
    let attributes: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: textColor,
    ]
    let attributed = NSAttributedString(string: text, attributes: attributes)
    let textSize = attributed.size()
    attributed.draw(at: NSPoint(x: x, y: CGFloat(canvasHeight) - top - textSize.height))
}

func drawRoleCard(image: NSImage, x: CGFloat, top: CGFloat, size: CGFloat, border: NSColor) {
    let frame = rect(x: x, top: top, width: size, height: size)
    let shadow = NSShadow()
    shadow.shadowColor = color(0x123B3A, alpha: 0.14)
    shadow.shadowBlurRadius = 12
    shadow.shadowOffset = NSSize(width: 0, height: -8)
    NSGraphicsContext.saveGraphicsState()
    shadow.set()
    fillRounded(frame, radius: size * 0.22, fill: .white, stroke: border, lineWidth: 4)
    NSGraphicsContext.restoreGraphicsState()

    let insetFrame = frame.insetBy(dx: 12, dy: 12)
    NSGraphicsContext.saveGraphicsState()
    NSBezierPath(roundedRect: insetFrame, xRadius: size * 0.18, yRadius: size * 0.18).addClip()
    image.draw(in: insetFrame, from: .zero, operation: .sourceOver, fraction: 1)
    NSGraphicsContext.restoreGraphicsState()
}

NSGraphicsContext.saveGraphicsState()
guard let graphicsContext = NSGraphicsContext(bitmapImageRep: bitmap) else {
    fatalError("Unable to create feature-graphic context.")
}
NSGraphicsContext.current = graphicsContext

color(0xF3FAFC).setFill()
NSRect(x: 0, y: 0, width: canvasWidth, height: canvasHeight).fill()

color(0xCDEDEA, alpha: 0.72).setFill()
NSBezierPath(ovalIn: rect(x: -150, top: 275, width: 380, height: 380)).fill()
color(0xFFE5D3, alpha: 0.68).setFill()
NSBezierPath(ovalIn: rect(x: 820, top: -115, width: 290, height: 290)).fill()

drawRoleCard(image: patientImage, x: 54, top: 70, size: 282, border: color(0xB7E2DE))
drawRoleCard(image: familyImage, x: 213, top: 171, size: 242, border: color(0xFFD6B9))

fillRounded(rect(x: 510, top: 78, width: 52, height: 52), radius: 18, fill: color(0xD9F1EF))
color(0x009688).setFill()
rect(x: 525, top: 100, width: 22, height: 18).fill()
rect(x: 527, top: 91, width: 18, height: 6).fill()
color(0xFFFFFF).setFill()
rect(x: 533, top: 103, width: 6, height: 12).fill()
rect(x: 530, top: 106, width: 12, height: 6).fill()

drawText("お薬見守り", x: 580, top: 83, size: 34, weight: .bold, textColor: color(0x35605D))
drawText("毎日の服薬を", x: 510, top: 158, size: 48, weight: .heavy, textColor: color(0x17191C))
drawText("本人と家族で確認", x: 510, top: 224, size: 48, weight: .heavy, textColor: color(0x17191C))

color(0xC7DEDC).setFill()
rect(x: 510, top: 317, width: 424, height: 2).fill()
drawText("予定・記録・お薬の残量をひとつに", x: 510, top: 342, size: 27, weight: .semibold, textColor: color(0x596563))

fillRounded(rect(x: 510, top: 403, width: 132, height: 44), radius: 22, fill: color(0xD9F1EF))
drawText("本人モード", x: 528, top: 411, size: 20, weight: .bold, textColor: color(0x007D73))
fillRounded(rect(x: 656, top: 403, width: 132, height: 44), radius: 22, fill: color(0xFFE7D6))
drawText("家族モード", x: 674, top: 411, size: 20, weight: .bold, textColor: color(0xD85B00))

NSGraphicsContext.restoreGraphicsState()

guard let jpeg = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.95]) else {
    fatalError("Unable to encode feature graphic as JPEG.")
}
try jpeg.write(to: outputURL)
print("Wrote \(outputURL.path)")
