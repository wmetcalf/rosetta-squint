// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaSquintHash",
    platforms: [
        // swift-png 4.x requires macOS 10.15+. Set our minimum to match.
        .macOS(.v10_15),
    ],
    products: [
        .library(name: "RosettaSquintHash", targets: ["RosettaSquintHash"]),
    ],
    dependencies: [
        .package(url: "https://github.com/tayloraswift/swift-png", "4.0.0"..<"4.4.0"),
    ],
    targets: [
        .target(
            name: "RosettaSquintHash",
            dependencies: [
                .product(name: "PNG", package: "swift-png"),
            ]
        ),
        .testTarget(
            name: "RosettaSquintHashTests",
            dependencies: ["RosettaSquintHash"]
        ),
    ]
)
