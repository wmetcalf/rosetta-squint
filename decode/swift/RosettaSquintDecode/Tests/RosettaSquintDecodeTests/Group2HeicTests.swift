import XCTest
@testable import RosettaSquintDecode

final class Group2HeicTests: XCTestCase {
    func testByteExactAllHeic() throws {
        let fixtures = try TestKit.listValidFixtures("heic")
        XCTAssertEqual(fixtures.count, 10, "expected 10 HEIC fixtures")

        var failures: [String] = []
        for rel in fixtures {
            let input = try TestKit.readFixture(rel)
            do {
                let got = try Decoder.decode(input)
                let want = try TestKit.readGolden(rel)
                if got.width != want.width || got.height != want.height
                    || got.channels.bytesPerPixel != want.channels {
                    failures.append("\(rel): shape \(got.width)x\(got.height)c\(got.channels.bytesPerPixel)"
                        + " != \(want.width)x\(want.height)c\(want.channels)")
                    continue
                }
                if got.data.count != want.pixels.count {
                    failures.append("\(rel): pixel count \(got.data.count) != \(want.pixels.count)")
                    continue
                }
                for i in 0..<got.data.count {
                    if got.data[i] != want.pixels[i] {
                        failures.append("\(rel): byte \(i) got=\(got.data[i]) want=\(want.pixels[i])")
                        break
                    }
                }
            } catch {
                failures.append("\(rel): threw \(error)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) HEIC failures:\n  \(failures.joined(separator: "\n  "))")
        }
    }
}
