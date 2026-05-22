// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaSquint",
    products: [
        .library(name: "RosettaSquint", targets: ["RosettaSquint"]),
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
        .testTarget(
            name: "RosettaSquintTests",
            dependencies: ["RosettaSquint"]
        ),
    ]
)
