// decode-cli — decode an image and emit raw bytes (spec/SPEC.md §2 wire
// format: 12-byte header + row-major pixels) to stdout.
//
// Used by tools/cross-port-diff/diff_all.py for live cross-port equivalence.
//
// Usage: DecodeCLI <fixture.path>
// Exit:  0 on success, 1 on decode error, 2 on harness error.

import Foundation
import RosettaSquintDecode

let args = CommandLine.arguments
guard args.count == 2 else {
    FileHandle.standardError.write("usage: DecodeCLI <fixture>\n".data(using: .utf8)!)
    exit(2)
}

let path = args[1]
guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)) else {
    FileHandle.standardError.write("read \(path): failed\n".data(using: .utf8)!)
    exit(2)
}

do {
    let img = try Decoder.decode(Array(data))
    let channels: UInt8 = img.channels == .rgba ? 4 : 3
    var header = Data()
    var w = UInt32(img.width).littleEndian
    var h = UInt32(img.height).littleEndian
    withUnsafeBytes(of: &w) { header.append(contentsOf: $0) }
    withUnsafeBytes(of: &h) { header.append(contentsOf: $0) }
    header.append(channels)
    header.append(0)
    header.append(0)
    header.append(0)
    FileHandle.standardOutput.write(header)
    FileHandle.standardOutput.write(Data(img.data))
    exit(0)
} catch let e as DecodeError {
    FileHandle.standardError.write("decode error: \(e)\n".data(using: .utf8)!)
    exit(1)
} catch {
    FileHandle.standardError.write("error: \(error)\n".data(using: .utf8)!)
    exit(1)
}
