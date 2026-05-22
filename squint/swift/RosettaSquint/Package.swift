// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaSquint",
    products: [
        .library(name: "RosettaSquint", targets: ["RosettaSquint"]),
        .executable(name: "SquintCLI", targets: ["SquintCLI"]),
    ],
    dependencies: [
        .package(path: "../../../hash/swift/RosettaImageHash"),
        .package(path: "../../../decode/swift/RosettaImageDecode"),
    ],
    targets: [
        .target(
            name: "RosettaSquint",
            dependencies: [
                .product(name: "RosettaImageHash", package: "RosettaImageHash"),
                .product(name: "RosettaImageDecode", package: "RosettaImageDecode"),
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
