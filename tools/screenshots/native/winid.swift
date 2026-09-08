import CoreGraphics
import Foundation
let list = CGWindowListCopyWindowInfo([.optionAll], kCGNullWindowID) as! [[String: Any]]
for w in list {
  let owner = w[kCGWindowOwnerName as String] as? String ?? ""
  let name = w[kCGWindowName as String] as? String ?? ""
  let layer = w[kCGWindowLayer as String] as? Int ?? 0
  let b = w[kCGWindowBounds as String] as! [String: Any]
  let id = w[kCGWindowNumber as String] as! Int
  let pid = w[kCGWindowOwnerPID as String] as! Int
  if pid == Int(CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "0")! {
    print("\(id)\t\(pid)\t\(layer)\t\(owner)\t\(name)\t\(b["X"]!),\(b["Y"]!),\(b["Width"]!),\(b["Height"]!)")
  }
}
let d = CGDisplayBounds(CGMainDisplayID()); print("SCREEN\t\(d.width)x\(d.height)")
