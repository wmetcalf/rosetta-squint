// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaSquint",
    products: [
        .library(name: "RosettaSquint", targets: ["RosettaSquint"]),
        .executable(name: "SquintCLI", targets: ["SquintCLI"]),
    ],
    dependencies: [
        .package(path: "../../../hash/swift/RosettaSquintHash"),
        .package(path: "../../../decode/swift/RosettaSquintDecode"),
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
