package dev.smallwins.shared

data class PortfolioGame(
    val id: String,
    val name: String,
    val eyebrow: String,
    val description: String,
    val tags: String,
    val nativeStatus: NativeStatus,
)

enum class NativeStatus { PLAYABLE, PLANNED }

val portfolioGames = listOf(
    PortfolioGame("purrdoku", "Purrdoku", "A place for every cat", "A cosy house. A few clues. Find where every cat belongs.", "Logic · Deduction", NativeStatus.PLANNED),
    PortfolioGame("pipes", "Burrow Network", "Find the way through", "Turn the tunnel pieces until every burrow connects.", "Patterns · Connection", NativeStatus.PLAYABLE),
    PortfolioGame("untangle", "Untangle", "Make a little space", "Move a point. Shift your perspective. Find the calm in the tangle.", "Spatial · Perspective", NativeStatus.PLANNED),
    PortfolioGame("waypoints", "Waypoints", "Enjoy the little detours", "A rabbit, a few carrots, and a path to find.", "Planning · Discovery", NativeStatus.PLANNED),
    PortfolioGame("shikaku", "Shikaku", "Make room for every clue", "Draw tidy rectangles and give each clue exactly the space it needs.", "Spatial · Packing", NativeStatus.PLANNED),
    PortfolioGame("wildlife-survey", "Habitat Search", "A pond or a burrow", "Look carefully, find what is hidden, and keep a little map of your search.", "Observation · Logic", NativeStatus.PLANNED),
)
