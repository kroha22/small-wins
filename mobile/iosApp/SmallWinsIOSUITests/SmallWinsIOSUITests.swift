import XCTest

final class SmallWinsIOSUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--uitesting-reset"]
        app.launch()
    }

    func testCatalogShowsThePortfolioEntryPoint() {
        XCTAssertTrue(app.staticTexts["Small Wins"].waitForExistence(timeout: 10))
        XCTAssertTrue(element(containing: "Purrdoku").exists)
        XCTAssertTrue(element(containing: "Burrow Network").exists)
        XCTAssertTrue(element(containing: "Settings").exists)
    }

    func testCanOpenAndLeaveAPuzzle() {
        tapElement(containing: "Burrow Network")
        tapElement(containing: "First flow")
        XCTAssertTrue(app.staticTexts["Burrow Network"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["First flow"].exists)

        tapElement(containing: "Back")
        XCTAssertTrue(element(containing: "Fresh puzzle").waitForExistence(timeout: 5))
    }

    func testUnfinishedAttemptRestoresAfterRelaunch() {
        tapElement(containing: "Burrow Network")
        tapElement(containing: "Around the bend")
        tapElement(containing: "Pipe 1")
        XCTAssertTrue(element(containing: "Undo").waitForExistence(timeout: 5))

        app.terminate()
        app.launchArguments = []
        app.launch()

        XCTAssertTrue(app.staticTexts["Around the bend"].waitForExistence(timeout: 10))
        XCTAssertTrue(element(containing: "Undo").exists)
    }

    private func element(containing label: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", label))
            .firstMatch
    }

    private func tapElement(containing label: String) {
        let candidate = element(containing: label)
        var swipes = 0
        while (!candidate.exists || !candidate.isHittable) && swipes < 8 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(candidate.waitForExistence(timeout: 5), "Missing UI element containing label: \(label)")
        candidate.tap()
    }
}
