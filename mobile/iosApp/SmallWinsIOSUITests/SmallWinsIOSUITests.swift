import XCTest

final class SmallWinsIOSUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--uitesting-reset"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Small Wins"].waitForExistence(timeout: 20))
    }

    func testCatalogShowsThePortfolioEntryPoint() {
        XCTAssertTrue(element(containing: "Purrdoku").exists)
        XCTAssertTrue(element(containing: "Burrow Network").exists)
        XCTAssertTrue(element(containing: "Settings").exists)
    }

    func testCanOpenAndLeaveAPuzzle() {
        tapElement(identifier: "game-pipes")
        tapElement(identifier: "level-level-1")
        XCTAssertTrue(app.staticTexts["Burrow Network"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Around the bend"].exists)

        tapElement(identifier: "back-to-levels")
        XCTAssertTrue(element(containing: "Fresh puzzle").waitForExistence(timeout: 5))
    }

    func testUnfinishedAttemptRestoresAfterRelaunch() {
        tapElement(identifier: "game-pipes")
        tapElement(identifier: "level-level-1")
        tapElement(identifier: "pipe-0")
        XCTAssertTrue(element(containing: "Undo").waitForExistence(timeout: 5))

        app.terminate()
        app.launchArguments = []
        app.launch()

        XCTAssertTrue(app.staticTexts["Around the bend"].waitForExistence(timeout: 10))
        XCTAssertTrue(element(containing: "Undo").exists)
    }

    func testGeneratedPuzzleRestoresAfterRelaunch() {
        tapElement(identifier: "game-pipes")
        tapElement(identifier: "generate-level")
        XCTAssertTrue(app.staticTexts["Fresh network"].waitForExistence(timeout: 5))

        tapElement(identifier: "pipe-0")
        XCTAssertTrue(element(containing: "Undo").waitForExistence(timeout: 5))

        app.terminate()
        app.launchArguments = []
        app.launch()

        XCTAssertTrue(app.staticTexts["Fresh network"].waitForExistence(timeout: 10))
        XCTAssertTrue(element(containing: "Undo").exists)
    }

    func testSettingsPersistAndClearLocally() {
        tapElement(identifier: "settings")
        let reduceMotion = element(identifier: "reduce-motion")
        XCTAssertTrue(reduceMotion.waitForExistence(timeout: 5))
        let initialState = accessibilityState(of: reduceMotion)

        tapElement(identifier: "reduce-motion")
        let enabledState = accessibilityState(of: reduceMotion)
        XCTAssertNotEqual(enabledState, initialState)

        app.terminate()
        app.launchArguments = []
        app.launch()

        tapElement(identifier: "settings")
        XCTAssertEqual(accessibilityState(of: element(identifier: "reduce-motion")), enabledState)

        tapElement(identifier: "clear-progress")
        tapElement(identifier: "confirm-clear-progress")
        tapElement(identifier: "settings")
        XCTAssertEqual(accessibilityState(of: element(identifier: "reduce-motion")), initialState)
    }

    private func element(containing label: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", label))
            .firstMatch
    }

    private func element(identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func tapElement(containing label: String) {
        let candidate = element(containing: label)
        tap(candidate, description: "label containing: \(label)")
    }

    private func tapElement(identifier: String) {
        let candidate = element(identifier: identifier)
        tap(candidate, description: "identifier: \(identifier)")
    }

    private func tap(_ candidate: XCUIElement, description: String) {
        if !candidate.exists {
            _ = candidate.waitForExistence(timeout: 3)
        }
        var swipes = 0
        while (!candidate.exists || !candidate.isHittable) && swipes < 8 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(candidate.waitForExistence(timeout: 5), "Missing UI element with \(description)")
        candidate.tap()
    }

    private func accessibilityState(of element: XCUIElement) -> String {
        element.label
    }
}
