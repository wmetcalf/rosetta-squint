// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaImageDecode",
    products: [
        .library(name: "RosettaImageDecode", targets: ["RosettaImageDecode"]),
    ],
    dependencies: [
        .package(url: "https://github.com/tayloraswift/swift-png", "4.0.0"..<"4.4.0"),
    ],
    targets: [
        .target(
            name: "RosettaImageDecode",
            dependencies: [
                .product(name: "PNG", package: "swift-png"),
            ]
        ),
        .testTarget(
            name: "RosettaImageDecodeTests",
            dependencies: ["RosettaImageDecode"]
        ),
    ]
)
