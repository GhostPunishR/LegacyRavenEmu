import Foundation
import RavenBridge

/// Swift wrapper around the exact same C++ emulator cores as Android.
/// Load only legally sourced game files selected by the user.
final class RavenNativeCore {
    enum System: Int32 { case gb = 0, gbc = 1, gba = 2 }
    private var handle: OpaquePointer?

    init?(system: System) {
        guard let value = ravenemu_v3_create(system.rawValue) else { return nil }
        self.handle = value
    }

    deinit {
        ravenemu_v3_destroy(handle)
    }

    var width: Int { Int(ravenemu_v3_video_width(handle)) }
    var height: Int { Int(ravenemu_v3_video_height(handle)) }
    var fps: Double { ravenemu_v3_video_fps(handle) }
    var audioRate: Int { Int(ravenemu_v3_audio_rate(handle)) }
    var lastError: String { String(cString: ravenemu_v3_last_error()) }

    func load(rom: Data, battery: Data? = nil) -> Bool {
        guard let handle else { return false }
        return rom.withUnsafeBytes { romBytes in
            if let battery {
                return battery.withUnsafeBytes { batteryBytes in
                    ravenemu_v3_load(handle,
                        romBytes.bindMemory(to: UInt8.self).baseAddress, rom.count,
                        batteryBytes.bindMemory(to: UInt8.self).baseAddress, battery.count) != 0
                }
            }
            return ravenemu_v3_load(handle,
                romBytes.bindMemory(to: UInt8.self).baseAddress, rom.count, nil, 0) != 0
        }
    }

    /// Packed 0xAARRGGBB output. Call from a single serial emulation queue.
    func runFrame() -> [UInt32]? {
        guard let handle else { return nil }
        var pixels = [UInt32](repeating: 0, count: width * height)
        let success = pixels.withUnsafeMutableBufferPointer {
            ravenemu_v3_run_frame(handle, $0.baseAddress, $0.count) != 0
        }
        return success ? pixels : nil
    }

    func readAudio(sampleCapacity: Int = 4096) -> [Int16] {
        guard let handle, sampleCapacity >= 0 else { return [] }
        var samples = [Int16](repeating: 0, count: sampleCapacity)
        let count = samples.withUnsafeMutableBufferPointer {
            ravenemu_v3_read_audio(handle, $0.baseAddress, $0.count)
        }
        return Array(samples.prefix(Int(count)))
    }

    func button(_ id: Int32, pressed: Bool) -> Bool {
        ravenemu_v3_set_button(handle, id, pressed ? 1 : 0) != 0
    }

    func reset() -> Bool { ravenemu_v3_reset(handle) != 0 }
}
