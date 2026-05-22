import Foundation
import RosettaSquint

let args = CommandLine.arguments
guard args.count == 4 else {
    FileHandle.standardError.write("usage: SquintCLI <algo> <size> <path>\n".data(using: .utf8)!)
    exit(2)
}

let algo = args[1]
let size = Int(args[2]) ?? 8
let path = args[3]

do {
    let result: String
    switch algo {
    case "phash": result = String(describing: try RosettaSquint.phash(at: path, hashSize: size))
    case "phash_simple": result = String(describing: try RosettaSquint.phashSimple(at: path, hashSize: size))
    case "dhash": result = String(describing: try RosettaSquint.dhash(at: path, hashSize: size))
    case "dhash_vertical": result = String(describing: try RosettaSquint.dhashVertical(at: path, hashSize: size))
    case "average_hash": result = String(describing: try RosettaSquint.averageHash(at: path, hashSize: size))
    case "whash_haar": result = String(describing: try RosettaSquint.whashHaar(at: path, hashSize: size))
    case "whash_db4": result = String(describing: try RosettaSquint.whashDb4(at: path, hashSize: size))
    case "whash_db4_robust": result = String(describing: try RosettaSquint.whashDb4Robust(at: path, hashSize: size))
    case "colorhash": result = String(describing: try RosettaSquint.colorhash(at: path, binbits: size))
    case "crop_resistant_hash": result = String(describing: try RosettaSquint.cropResistantHash(at: path))
    default:
        FileHandle.standardError.write("unknown algo: \(algo)\n".data(using: .utf8)!)
        exit(2)
    }
    print(result)
    exit(0)
} catch {
    FileHandle.standardError.write("error: \(error)\n".data(using: .utf8)!)
    exit(1)
}
