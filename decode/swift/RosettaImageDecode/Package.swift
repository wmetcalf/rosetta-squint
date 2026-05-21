// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaImageDecode",
    products: [
        .library(name: "RosettaImageDecode", targets: ["RosettaImageDecode"]),
    ],
    dependencies: [],
    targets: [
        .target(name: "RosettaImageDecode"),
        .testTarget(
            name: "RosettaImageDecodeTests",
            dependencies: ["RosettaImageDecode"]
        ),
    ]
)
