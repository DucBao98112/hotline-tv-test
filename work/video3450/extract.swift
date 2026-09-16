import AVFoundation
import AppKit
import Foundation

let asset = AVURLAsset(url: URL(fileURLWithPath: "/Users/owner/Desktop/IMG_3450.MOV"))
let generator = AVAssetImageGenerator(asset: asset)
generator.appliesPreferredTrackTransform = true

for (index, seconds) in [0.5, 2.0, 4.0].enumerated() {
    let image = try generator.copyCGImage(
        at: CMTime(seconds: seconds, preferredTimescale: 600),
        actualTime: nil
    )
    let representation = NSBitmapImageRep(cgImage: image)
        .representation(using: .jpeg, properties: [.compressionFactor: 0.9])!
    try representation.write(
        to: URL(fileURLWithPath: "/Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/work/video3450/frame-\(index).jpg")
    )
}
