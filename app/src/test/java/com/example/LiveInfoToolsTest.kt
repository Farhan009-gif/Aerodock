package com.example

import com.example.data.mcp.LiveInfoHttpResponse
import com.example.data.mcp.LiveInfoToolsService
import com.example.data.mcp.RssFeedConfig
import com.example.data.mcp.SafePublicDns
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class LiveInfoToolsTest {

    private val savedDuckDuckGoHtmlSample = """
        <!DOCTYPE html>
        <html>
        <body>
          <div id="links" class="results">
            <div class="result results_links results_links_deep web-result">
              <div class="links_main links_deep result__body">
                <h2 class="result__title">
                  <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2Fcoroutines-overview.html&amp;rut=abc123">
                    <b>Kotlin</b> Coroutines Overview - Official Documentation
                  </a>
                </h2>
                <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2Fcoroutines-overview.html&amp;rut=abc123">
                  Asynchronous or non-blocking programming is an important part of the <b>Kotlin</b> ecosystem.
                </a>
              </div>
            </div>
            <div class="result results_links results_links_deep web-result">
              <div class="links_main links_deep result__body">
                <h2 class="result__title">
                  <a rel="nofollow" class="result__a" href="/l/?kh=-1&amp;uddg=https%3A%2F%2Fdeveloper.android.com%2Fjetpack%2Fcompose">
                    Jetpack Compose UI App Development Toolkit
                  </a>
                </h2>
                <div class="result__snippet">
                  Jetpack Compose is Android&#39;s recommended modern toolkit for building native UI.
                </div>
              </div>
            </div>
            <div class="result results_links results_links_deep web-result">
              <div class="links_main links_deep result__body">
                <h2 class="result__title">
                  <a rel="nofollow" class="result__a" href="https://duckduckgo.com/y.js?ad_provider=bing&amp;u3=https%3A%2F%2Fads.example.com">
                    Sponsored Ad Result
                  </a>
                </h2>
                <div class="result__snippet">Ad snippet that should be skipped.</div>
              </div>
            </div>
          </div>
        </body>
        </html>
    """.trimIndent()

    private val savedRssXmlSample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>BBC News - World</title>
            <link>https://www.bbc.co.uk/news/world</link>
            <item>
              <title><![CDATA[Global Climate Summit Reaches New Clean Energy Accord]]></title>
              <description><![CDATA[<p>Delegates agreed to accelerate solar and wind deployment by 2030.</p>]]></description>
              <link>https://www.bbc.com/news/world-climate-101</link>
              <pubDate>Mon, 01 Nov 2026 07:00:00 GMT</pubDate>
            </item>
            <item>
              <title>Breakthrough in Quantum Error Correction Announced</title>
              <description>Researchers demonstrated logical qubits with lower error rates.</description>
              <link>https://www.bbc.com/news/science-quantum-202</link>
              <pubDate>Mon, 01 Nov 2026 06:30:00 GMT</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val savedAtomXmlSample = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Hacker News Frontpage</title>
          <entry>
            <title>SQLite 3.50 Release Notes and Query Planner Improvements</title>
            <link rel="alternate" type="text/html" href="https://www.sqlite.org/releaselog/3_50_0.html"/>
            <published>2026-11-01T08:00:00Z</published>
            <summary>Faster B-tree lookups and improved JSON operators for embedded databases.</summary>
          </entry>
          <entry>
            <title>Open-Source Solar Microgrid Controller in Rust</title>
            <link href="https://example.org/solar-rust-controller"/>
            <updated>2026-11-01T07:45:00Z</updated>
            <summary>Clean energy firmware for off-grid inverters.</summary>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `DuckDuckGo redirect decoder extracts real target URL`() {
        val protocolRelative =
            "//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2Fcoroutines-overview.html&amp;rut=abc123"
        assertEquals(
            "https://kotlinlang.org/docs/coroutines-overview.html",
            LiveInfoToolsService.decodeDuckDuckGoRedirectUrl(protocolRelative)
        )

        val pathRelative =
            "/l/?kh=-1&uddg=https%3A%2F%2Fdeveloper.android.com%2Fjetpack%2Fcompose%3Fhl%3Den"
        assertEquals(
            "https://developer.android.com/jetpack/compose?hl=en",
            LiveInfoToolsService.decodeDuckDuckGoRedirectUrl(pathRelative)
        )

        val directHttps = "https://example.com/direct-article"
        assertEquals(
            "https://example.com/direct-article",
            LiveInfoToolsService.decodeDuckDuckGoRedirectUrl(directHttps)
        )
    }

    @Test
    fun `DuckDuckGo HTML parser parses titles decoded links and snippets and skips ads`() {
        val parsed = LiveInfoToolsService.parseDuckDuckGoHtml(savedDuckDuckGoHtmlSample, maxResults = 5)
        assertEquals(2, parsed.size)

        assertEquals("Kotlin Coroutines Overview - Official Documentation", parsed[0].title)
        assertEquals("https://kotlinlang.org/docs/coroutines-overview.html", parsed[0].link)
        assertTrue(parsed[0].snippet.contains("Asynchronous or non-blocking programming"))
        assertFalse(parsed[0].title.contains("<b>"))

        assertEquals("Jetpack Compose UI App Development Toolkit", parsed[1].title)
        assertEquals("https://developer.android.com/jetpack/compose", parsed[1].link)
        assertEquals(
            "Jetpack Compose is Android's recommended modern toolkit for building native UI.",
            parsed[1].snippet
        )
    }

    @Test
    fun `DuckDuckGo CAPTCHA page returns Search unavailable without fallback loop`() = runBlocking {
        var callCount = 0
        val captchaHtml = """
            <html><body>
              <div class="anomaly-modal">Unfortunately, bots use DuckDuckGo too. Please complete the following challenge.</div>
            </body></html>
        """.trimIndent()

        val service = LiveInfoToolsService(
            dnsResolver = { listOf(InetAddress.getByAddress(byteArrayOf(52, 142.toByte(), 124, 215.toByte()))) },
            customHttpFetcher = { _, _ ->
                callCount++
                LiveInfoHttpResponse(statusCode = 200, body = captchaHtml)
            }
        )

        val result = service.executeWebSearch("""{"query":"kotlin coroutines"}""")
        assertFalse(result.isSuccess)
        assertEquals(LiveInfoToolsService.SEARCH_UNAVAILABLE_MESSAGE, result.modelResponseText)
        // Must not retry in a loop or call fallback when CAPTCHA is shown
        assertEquals(1, callCount)
    }

    @Test
    fun `web_search uses Instant Answer API fallback only when HTML page fails`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val instantAnswerJson = """
            {
              "Heading": "Kotlin (programming language)",
              "AbstractText": "Kotlin is a cross-platform, statically typed, general-purpose high-level programming language with type inference.",
              "AbstractURL": "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
              "RelatedTopics": [
                {
                  "Text": "Jetpack Compose - Modern Android UI toolkit written in Kotlin.",
                  "FirstURL": "https://developer.android.com/compose"
                }
              ]
            }
        """.trimIndent()

        val service = LiveInfoToolsService(
            dnsResolver = { listOf(InetAddress.getByAddress(byteArrayOf(52, 142.toByte(), 124, 215.toByte()))) },
            customHttpFetcher = { url, _ ->
                requestedUrls.add(url)
                if (url.startsWith("https://html.duckduckgo.com")) {
                    LiveInfoHttpResponse(statusCode = 500, body = "Internal Server Error")
                } else {
                    LiveInfoHttpResponse(statusCode = 200, body = instantAnswerJson)
                }
            }
        )

        val res = service.executeWebSearch("""{"query":"kotlin"}""")
        assertTrue(res.isSuccess)
        assertEquals(2, requestedUrls.size)
        assertTrue(requestedUrls[0].startsWith("https://html.duckduckgo.com/html/?q=kotlin"))
        assertTrue(requestedUrls[1].startsWith("https://api.duckduckgo.com/?q=kotlin&format=json&no_html=1"))
        assertEquals(2, res.sources.size)
        assertEquals("https://en.wikipedia.org/wiki/Kotlin_(programming_language)", res.sources[0].link)
    }

    @Test
    fun `RSS and Atom parsers and topic filter work for matching and empty topics`() {
        val rssItems = LiveInfoToolsService.parseRssOrAtomXml(savedRssXmlSample, "BBC World")
        assertEquals(2, rssItems.size)
        assertEquals("Global Climate Summit Reaches New Clean Energy Accord", rssItems[0].title)
        assertEquals("BBC World", rssItems[0].sourceName)
        assertEquals("Mon, 01 Nov 2026 07:00:00 GMT", rssItems[0].publishDate)
        assertEquals("https://www.bbc.com/news/world-climate-101", rssItems[0].link)
        assertEquals("Delegates agreed to accelerate solar and wind deployment by 2030.", rssItems[0].summary)

        val atomItems = LiveInfoToolsService.parseRssOrAtomXml(savedAtomXmlSample, "Hacker News")
        assertEquals(2, atomItems.size)
        assertEquals("SQLite 3.50 Release Notes and Query Planner Improvements", atomItems[0].title)
        assertEquals("Hacker News", atomItems[0].sourceName)
        assertEquals("2026-11-01T08:00:00Z", atomItems[0].publishDate)
        assertEquals("https://www.sqlite.org/releaselog/3_50_0.html", atomItems[0].link)

        val combined = rssItems + atomItems

        // Filter by topic matching title or summary ("solar" is in BBC item 1 summary and HN item 2 title)
        val solarFiltered = LiveInfoToolsService.filterHeadlinesByTopic(combined, "solar", maxItems = 10)
        assertEquals(2, solarFiltered.size)
        assertTrue(solarFiltered.any { it.sourceName == "BBC World" })
        assertTrue(solarFiltered.any { it.sourceName == "Hacker News" })

        // Empty topic returns latest items across all feeds up to maxItems
        val emptyTopicItems = LiveInfoToolsService.filterHeadlinesByTopic(combined, "", maxItems = 3)
        assertEquals(3, emptyTopicItems.size)
    }

    @Test
    fun `Rate limits cache TTLs and 2 KB response cap are enforced`() = runBlocking {
        var fakeNowMs = 1_000_000L
        val recordedSleeps = mutableListOf<Long>()
        var networkSearchCalls = 0
        val feedCallCountByUrl = mutableMapOf<String, Int>()

        val service = LiveInfoToolsService(
            clockMs = { fakeNowMs },
            sleeperMs = { waitMs ->
                recordedSleeps.add(waitMs)
                fakeNowMs += waitMs
            },
            dnsResolver = { listOf(InetAddress.getByAddress(byteArrayOf(151.toByte(), 101, 1, 67))) },
            customHttpFetcher = { url, _ ->
                if (url.contains("duckduckgo.com")) {
                    networkSearchCalls++
                    LiveInfoHttpResponse(statusCode = 200, body = savedDuckDuckGoHtmlSample)
                } else {
                    feedCallCountByUrl[url] = (feedCallCountByUrl[url] ?: 0) + 1
                    LiveInfoHttpResponse(statusCode = 200, body = savedRssXmlSample)
                }
            }
        )

        // 1. First search at t=1_000_000
        val s1 = service.executeWebSearch("""{"query":"first query"}""")
        assertTrue(s1.isSuccess)
        assertEquals(1, networkSearchCalls)
        assertTrue(recordedSleeps.isEmpty())

        // 2. Same query within 10 minutes uses cache (no network call, no sleep)
        fakeNowMs += 1_000L
        val s2Cached = service.executeWebSearch("""{"query":"FIRST   QUERY"}""")
        assertTrue(s2Cached.isSuccess)
        assertEquals(1, networkSearchCalls)
        assertTrue(recordedSleeps.isEmpty())

        // 3. Second distinct search 1 second after first network search -> waits 2,000ms to satisfy 3s minimum interval
        val s3 = service.executeWebSearch("""{"query":"second query"}""")
        assertTrue(s3.isSuccess)
        assertEquals(2, networkSearchCalls)
        assertEquals(listOf(2_000L), recordedSleeps)

        // 4. Fourth search in the same message still runs — there is no per-message search cap
        val s4 = service.executeWebSearch("""{"query":"third query"}""")
        assertTrue(s4.isSuccess)
        assertEquals(3, networkSearchCalls)

        // Reset per-message counter for next user message
        service.resetPerMessageSearchCounter()

        // 5. Feed cache (15 minutes) and 2-second wait between requests to the same feed
        val feed = RssFeedConfig(id = "f1", name = "BBC World", url = "https://feeds.bbci.co.uk/news/world/rss.xml")
        recordedSleeps.clear()
        val n1 = service.executeNewsHeadlines("""{"topic":""}""", listOf(feed))
        assertTrue(n1.isSuccess)
        assertEquals(1, feedCallCountByUrl[feed.url])

        // Within 15 minutes -> served from cache without network call
        fakeNowMs += 500L
        val n2Cached = service.executeNewsHeadlines("""{"topic":"quantum"}""", listOf(feed))
        assertTrue(n2Cached.isSuccess)
        assertEquals(1, feedCallCountByUrl[feed.url])

        // Advance past 15-minute cache TTL, then test 2-second same-feed rate limit when cache is cleared
        service.clearCachesForTesting()
        service.executeNewsHeadlines("""{"topic":""}""", listOf(feed))
        // Force cache miss while keeping lastFeedRequestAtMs by advancing < 2s on a non-cached empty response
        val emptyFeed = RssFeedConfig(id = "f2", name = "Empty", url = "https://hnrss.org/frontpage")
        val serviceWithShortWaitTest = LiveInfoToolsService(
            clockMs = { fakeNowMs },
            sleeperMs = { waitMs ->
                recordedSleeps.add(waitMs)
                fakeNowMs += waitMs
            },
            dnsResolver = { listOf(InetAddress.getByAddress(byteArrayOf(151.toByte(), 101, 1, 67))) },
            customHttpFetcher = { _, _ ->
                // Return empty body first so it is not cached, allowing immediate second network call to same feed
                LiveInfoHttpResponse(statusCode = 503, body = "")
            }
        )
        recordedSleeps.clear()
        serviceWithShortWaitTest.executeNewsHeadlines("""{"topic":""}""", listOf(emptyFeed))
        fakeNowMs += 500L
        serviceWithShortWaitTest.executeNewsHeadlines("""{"topic":""}""", listOf(emptyFeed))
        assertEquals(listOf(1_500L), recordedSleeps) // 2000ms - 500ms = 1500ms wait for same feed

        // 6. Verify 2 KB (2,048 bytes) response cap
        val hugeText = LiveInfoToolsService.wrapAsUntrustedData(
            LiveInfoToolsService.WEB_SEARCH_TOOL_NAME,
            "A".repeat(6_000)
        )
        val capped = LiveInfoToolsService.capResponseTo2Kb(hugeText)
        assertTrue(capped.toByteArray(StandardCharsets.UTF_8).size <= LiveInfoToolsService.MAX_RESPONSE_BYTES)
        assertTrue(capped.endsWith("[/UNTRUSTED_EXTERNAL_DATA]"))
    }

    @Test
    fun `Blocked address check refuses private loopback link-local and CGNAT addresses after DNS lookup`() {
        val blockedIps = listOf(
            byteArrayOf(127, 0, 0, 1),                      // Loopback 127.0.0.1
            byteArrayOf(10, 0, 0, 42),                      // Private 10.0.0.0/8
            byteArrayOf(172.toByte(), 16, 0, 1),            // Private 172.16.0.0/12
            byteArrayOf(172.toByte(), 31, 255.toByte(), 1), // Private 172.31.255.1
            byteArrayOf(192.toByte(), 168.toByte(), 1, 1),  // Private 192.168.0.0/16
            byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte()), // Link-local / metadata
            byteArrayOf(100, 64, 0, 1),                     // CGNAT 100.64.0.0/10
            byteArrayOf(0, 0, 0, 0)                         // Unspecified 0.0.0.0
        )

        for (ipBytes in blockedIps) {
            val addr = InetAddress.getByAddress("example.com", ipBytes)
            assertTrue(
                "Expected ${addr.hostAddress} to be blocked",
                LiveInfoToolsService.isBlockedAddress(addr)
            )
            val dns = SafePublicDns { listOf(addr) }
            val threw = runCatching { dns.lookup("example.com") }.isFailure
            assertTrue("Expected SafePublicDns to throw for ${addr.hostAddress}", threw)
        }

        // Public IPv4 (e.g., 93.184.216.34) must be permitted
        val publicAddr = InetAddress.getByAddress("example.com", byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        assertFalse(LiveInfoToolsService.isBlockedAddress(publicAddr))
        val publicDns = SafePublicDns { listOf(publicAddr) }
        assertEquals(listOf(publicAddr), publicDns.lookup("example.com"))

        // Local hostnames must also be blocked
        assertTrue(LiveInfoToolsService.isBlockedHostname("localhost"))
        assertTrue(LiveInfoToolsService.isBlockedHostname("router.local"))
        assertFalse(LiveInfoToolsService.isValidHttpsFeedUrl("http://feeds.bbci.co.uk/news/world/rss.xml"))
        assertFalse(LiveInfoToolsService.isValidHttpsFeedUrl("https://192.168.1.1/rss.xml"))
        assertTrue(LiveInfoToolsService.isValidHttpsFeedUrl("https://feeds.bbci.co.uk/news/world/rss.xml"))
    }

    @Test
    fun `Disabled tool is never offered to the model`() {
        // Both off by default -> neither tool is offered
        val bothDisabled = LiveInfoToolsService.resolveEnabledLiveInfoToolTriples(
            modelSupportsFunctionCalling = true,
            webSearchEnabled = false,
            newsHeadlinesEnabled = false
        )
        assertTrue(bothDisabled.isEmpty())

        // Only web_search enabled -> news_headlines is never offered
        val onlyWebSearch = LiveInfoToolsService.resolveEnabledLiveInfoToolTriples(
            modelSupportsFunctionCalling = true,
            webSearchEnabled = true,
            newsHeadlinesEnabled = false
        )
        assertEquals(1, onlyWebSearch.size)
        assertEquals(LiveInfoToolsService.WEB_SEARCH_TOOL_NAME, onlyWebSearch.single().third.toolName)
        assertFalse(onlyWebSearch.any { it.third.toolName == LiveInfoToolsService.NEWS_HEADLINES_TOOL_NAME })

        // Only news_headlines enabled -> web_search is never offered
        val onlyNewsHeadlines = LiveInfoToolsService.resolveEnabledLiveInfoToolTriples(
            modelSupportsFunctionCalling = true,
            webSearchEnabled = false,
            newsHeadlinesEnabled = true
        )
        assertEquals(1, onlyNewsHeadlines.size)
        assertEquals(LiveInfoToolsService.NEWS_HEADLINES_TOOL_NAME, onlyNewsHeadlines.single().third.toolName)
        assertFalse(onlyNewsHeadlines.any { it.third.toolName == LiveInfoToolsService.WEB_SEARCH_TOOL_NAME })

        // Model without tool support -> neither tool is offered even if toggles are enabled
        val noToolSupport = LiveInfoToolsService.resolveEnabledLiveInfoToolTriples(
            modelSupportsFunctionCalling = false,
            webSearchEnabled = true,
            newsHeadlinesEnabled = true
        )
        assertTrue(noToolSupport.isEmpty())
    }
}
