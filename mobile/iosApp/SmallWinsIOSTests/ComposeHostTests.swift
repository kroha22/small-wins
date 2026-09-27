import XCTest
@testable import SmallWinsIOS
import SmallWinsShared

/// The iOS client is only a host for the shared Compose UI, so its own test
/// target checks that the framework links and that the entry point builds a
/// view controller. Game rules, presentation and screen behaviour are covered
/// by the shared Kotlin tests, which also run on the iOS simulator target.
final class ComposeHostTests: XCTestCase {
    func testComposeEntryPointBuildsAViewController() {
        let controller = MainViewControllerKt.MainViewController()

        XCTAssertNotNil(controller.view)
    }
}
