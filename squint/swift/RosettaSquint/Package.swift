// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaSquint",
    platforms: [
        // Matches the minimum from RosettaSquintHash + RosettaSquintDecode
        // (swift-png 4.x requires macOS 10.15+).
        .macOS(.v10_15),
    ],
    products: [
        .library(name: "RosettaSquint", targets: ["RosettaSquint"]),
        .executable(name: "SquintCLI", targets: ["SquintCLI"]),
    ],
    dependencies: [
        .package(path: "../../../hash/swift/RosettaSquintHash"),
        .package(path: "../../../decode/swift/RosettaSquintDecode"),
        // swift-system pinned <1.5.0: IORing.swift (1.5+) breaks newer Swift parsers; WasmKit allows >=1.3.0.
        .package(url: "https://github.com/apple/swift-system", "1.3.0"..<"1.5.0"),
    ],
    targets: [
        .target(
            name: "RosettaSquint",
            dependencies: [
                .product(name: "RosettaSquintHash", package: "RosettaSquintHash"),
                .product(name: "RosettaSquintDecode", package: "RosettaSquintDecode"),
            ]
        ),
        .executableTarget(
            name: "SquintCLI",
            dependencies: ["RosettaSquint"]
        ),
        .testTarget(
            name: "RosettaSquintTests",
            dependencies: ["RosettaSquint"]
        ),
    ]
)
