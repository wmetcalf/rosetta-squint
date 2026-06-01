// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaSquintDecode",
    platforms: [
        // swift-png 4.x requires macOS 10.15+. Set our minimum to match.
        .macOS(.v10_15),
    ],
    products: [
        .library(name: "RosettaSquintDecode", targets: ["RosettaSquintDecode"]),
        .executable(name: "DecodeCLI", targets: ["DecodeCLI"]),
    ],
    dependencies: [
        .package(url: "https://github.com/tayloraswift/swift-png", "4.0.0"..<"4.4.0"),
        .package(url: "https://github.com/swiftwasm/WasmKit", exact: "0.1.6"),
        // Pin swift-system below 1.5.0: its IORing.swift fails to compile under
        // newer Swift parsers (e.g. 5.10.1 in CI). WasmKit allows >= 1.3.0.
        .package(url: "https://github.com/apple/swift-system", "1.3.0"..<"1.5.0"),
    ],
    targets: [
        .systemLibrary(
            name: "Cjpeg",
            pkgConfig: "libturbojpeg",
            providers: [.apt(["libturbojpeg0-dev"]), .brew(["jpeg-turbo"])]
        ),
        .systemLibrary(
            name: "Cwebp",
            pkgConfig: "libwebp",
            providers: [.apt(["libwebp-dev"]), .brew(["webp"])]
        ),
        .systemLibrary(
            name: "Ctiff",
            pkgConfig: "libtiff-4",
            providers: [.apt(["libtiff-dev"]), .brew(["libtiff"])]
        ),
        .target(
            name: "RosettaSquintDecode",
            dependencies: [
                .product(name: "PNG", package: "swift-png"),
                "Cjpeg",
                "Cwebp",
                "Ctiff",
                .product(name: "WasmKit", package: "WasmKit"),
                .product(name: "WasmKitWASI", package: "WasmKit"),
            ],
            resources: [
                .copy("Resources/libheif_decode.wasm")
            ]
        ),
        .executableTarget(
            name: "DecodeCLI",
            dependencies: ["RosettaSquintDecode"]
        ),
        .testTarget(
            name: "RosettaSquintDecodeTests",
            dependencies: ["RosettaSquintDecode"]
        ),
    ]
)
