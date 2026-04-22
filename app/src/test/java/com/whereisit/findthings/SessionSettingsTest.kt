package com.whereisit.findthings

import com.google.common.truth.Truth.assertThat
import com.whereisit.findthings.data.repository.ActiveEndpoint
import com.whereisit.findthings.data.repository.AppTheme
import com.whereisit.findthings.data.repository.SessionSettings
import org.junit.Test

class SessionSettingsTest {
    @Test
    fun activeBaseUrl_addsTrailingSlash() {
        val s = SessionSettings("http://a:4000", "https://b", ActiveEndpoint.INTERNAL, "", AppTheme.SAND, "")
        assertThat(s.activeBaseUrl()).isEqualTo("http://a:4000/")
    }

    @Test
    fun fallbackBaseUrl_returnsOtherEndpoint() {
        val s = SessionSettings("http://a:4000", "https://b", ActiveEndpoint.INTERNAL, "", AppTheme.SAND, "")
        assertThat(s.fallbackBaseUrl()).isEqualTo("https://b:3000/")
    }
}
