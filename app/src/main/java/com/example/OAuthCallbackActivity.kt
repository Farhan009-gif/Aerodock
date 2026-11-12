package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.data.mcp.McpOAuthCallbacks

/**
 * Receives the OAuth redirect (runway://oauth-callback) after a browser
 * sign-in for an MCP server. Hands the URI to the running app and returns to
 * MainActivity; the actual code exchange happens in McpOAuthManager so this
 * activity stays a pure trampoline (it also works cold, after the browser
 * step got the app killed: MainActivity starts fresh and still consumes the
 * callback, and the pending flow lives in the vault).
 */
class OAuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { McpOAuthCallbacks.deliver(it) }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
