// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "RosettaSquintDecode",
    products: [
        .library(name: "RosettaSquintDecode", targets: ["RosettaSquintDecode"]),
        .executable(name: "DecodeCLI", targets: ["DecodeCLI"]),
    ],
    dependencies: [
        .package(url: "https://github.com/tayloraswift/swift-png", "4.0.0"..<"4.4.0"),
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
        .systemLibrary(
            name: "Cheif",
            pkgConfig: "libheif",
            providers: [.apt(["libheif-dev"]), .brew(["libheif"])]
        ),
        .target(
            name: "RosettaSquintDecode",
            dependencies: [
                .product(name: "PNG", package: "swift-png"),
                "Cjpeg",
                "Cwebp",
                "Ctiff",
                "Cheif",
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
