// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaImageHash",
    products: [
        .library(name: "RosettaImageHash", targets: ["RosettaImageHash"]),
    ],
    dependencies: [
        .package(url: "https://github.com/tayloraswift/swift-png", "4.0.0"..<"4.4.0"),
    ],
    targets: [
        .target(
            name: "RosettaImageHash",
            dependencies: [
                .product(name: "PNG", package: "swift-png"),
            ]
        ),
        .testTarget(
            name: "RosettaImageHashTests",
            dependencies: ["RosettaImageHash"]
        ),
    ]
)
